package com.fivesoft.qplayer.steering

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class LogitechG920Controller(context: Context) : InputManager.InputDeviceListener {

    private val appContext = context.applicationContext
    private val inputManager = appContext.getSystemService(InputManager::class.java)
    private val mutableState = MutableStateFlow(SteeringInputState())
    private val paddleGearSelector = PaddleGearSelector()
    private val usbTransport = LogitechG920UsbTransport(
        context = appContext,
        listener = object : LogitechG920UsbTransport.Listener {
            override fun onUsbDeviceDetected(deviceName: String, statusMessage: String) {
                updateUsbDevice(deviceName, statusMessage)
            }

            override fun onUsbInputReady(deviceName: String) {
                updateUsbInputReady(deviceName)
            }

            override fun onUsbInput(report: LogitechG920Report) {
                updateUsbInput(report)
            }

            override fun onUsbDeviceUnavailable() {
                clearUsbDevice()
            }
        }
    )

    val state: StateFlow<SteeringInputState> = mutableState.asStateFlow()

    @Volatile
    private var started = false

    @Volatile
    private var activeDeviceId: Int? = null

    @Volatile
    private var axisMapping: AxisMapping? = null

    private var usbDeviceName: String? = null
    private var usbStatusMessage: String? = null
    private var usbInputAvailable = false

    @Synchronized
    fun start() {
        if (started) {
            return
        }
        started = true
        inputManager.registerInputDeviceListener(this, null)
        refreshDevice()
        usbTransport.start()
    }

    @Synchronized
    fun stop() {
        if (!started) {
            return
        }
        started = false
        usbTransport.stop()
        inputManager.unregisterInputDeviceListener(this)
        activeDeviceId = null
        axisMapping = null
        usbDeviceName = null
        usbStatusMessage = null
        usbInputAvailable = false
        paddleGearSelector.reset()
        mutableState.value = SteeringInputState()
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (!started || usbInputAvailable || event.actionMasked != MotionEvent.ACTION_MOVE ||
            !event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        ) {
            return false
        }

        val device = event.device ?: inputManager.getInputDevice(event.deviceId) ?: return false
        if (!device.isLogitechG920()) {
            return false
        }
        if (activeDeviceId != device.id) {
            connectDevice(device)
        }

        val mapping = axisMapping ?: return false
        val previousState = mutableState.value
        val steering = mapping.steering?.readSteering(event) ?: previousState.steering
        val accelerator = mapping.accelerator?.readPedal(event) ?: previousState.accelerator
        val brake = mapping.brake?.readPedal(event) ?: previousState.brake

        mutableState.value = previousState.copy(
            connected = true,
            inputAvailable = mapping.hasAllAxes,
            deviceName = device.name,
            statusMessage = null,
            steering = steering,
            accelerator = accelerator,
            brake = brake
        )
        return mapping.hasAnyAxis
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        refreshDevice()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        refreshDevice()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        if (deviceId == activeDeviceId) {
            refreshDevice()
        }
    }

    @Synchronized
    private fun refreshDevice() {
        if (!started) {
            return
        }

        val device = inputManager.inputDeviceIds
            .asSequence()
            .mapNotNull { deviceId -> inputManager.getInputDevice(deviceId) }
            .filter { device -> device.isLogitechG920() }
            .maxByOrNull { device -> device.g920DeviceScore() }

        if (device == null) {
            activeDeviceId = null
            axisMapping = null
            publishUsbState()
        } else {
            connectDevice(device)
        }
    }

    @Synchronized
    private fun connectDevice(device: InputDevice) {
        val sameDevice = activeDeviceId == device.id
        activeDeviceId = device.id
        axisMapping = AxisMapping.from(device)
        if (usbInputAvailable) {
            publishUsbState()
            return
        }
        val previousState = mutableState.value
        mutableState.value = SteeringInputState(
            connected = true,
            inputAvailable = axisMapping?.hasAllAxes == true,
            deviceName = device.name,
            steering = if (sameDevice) previousState.steering else 0f,
            accelerator = if (sameDevice) previousState.accelerator else 0f,
            brake = if (sameDevice) previousState.brake else 0f
        )
    }

    @Synchronized
    private fun updateUsbDevice(deviceName: String, statusMessage: String) {
        usbDeviceName = deviceName
        usbStatusMessage = statusMessage
        if (activeDeviceId == null) {
            publishUsbState()
        }
    }

    @Synchronized
    private fun updateUsbInputReady(deviceName: String) {
        usbDeviceName = deviceName
        usbStatusMessage = null
        usbInputAvailable = true
        paddleGearSelector.reset()
        mutableState.value = SteeringInputState(
            connected = true,
            inputAvailable = true,
            deviceName = deviceName,
            gear = SteeringGear.Neutral
        )
    }

    @Synchronized
    private fun updateUsbInput(report: LogitechG920Report) {
        if (!started) return
        usbInputAvailable = true
        val selectedGear = paddleGearSelector.update(
            shifterGear = report.gear,
            shiftUpPressed = report.shiftUpPressed,
            shiftDownPressed = report.shiftDownPressed
        )
        mutableState.value = SteeringInputState(
            connected = true,
            inputAvailable = true,
            deviceName = usbDeviceName ?: "Logitech G920",
            steering = report.steering,
            accelerator = report.accelerator,
            brake = report.brake,
            gear = selectedGear
        )
    }

    @Synchronized
    private fun clearUsbDevice() {
        usbDeviceName = null
        usbStatusMessage = null
        usbInputAvailable = false
        paddleGearSelector.reset()
        refreshDevice()
    }

    private fun publishUsbState() {
        val name = usbDeviceName
        mutableState.value = if (name == null) {
            SteeringInputState()
        } else {
            val previousState = mutableState.value
            SteeringInputState(
                connected = true,
                inputAvailable = usbInputAvailable,
                deviceName = name,
                statusMessage = usbStatusMessage,
                steering = if (usbInputAvailable) previousState.steering else 0f,
                accelerator = if (usbInputAvailable) previousState.accelerator else 0f,
                brake = if (usbInputAvailable) previousState.brake else 0f,
                gear = if (usbInputAvailable) previousState.gear else SteeringGear.Unknown
            )
        }
    }

    private data class AxisMapping(
        val steering: AxisBinding?,
        val accelerator: AxisBinding?,
        val brake: AxisBinding?
    ) {
        val hasAnyAxis: Boolean
            get() = steering != null || accelerator != null || brake != null

        val hasAllAxes: Boolean
            get() = steering != null && accelerator != null && brake != null

        companion object {
            fun from(device: InputDevice): AxisMapping {
                val steering = device.findAxis(
                    AxisCandidate(MotionEvent.AXIS_WHEEL),
                    AxisCandidate(MotionEvent.AXIS_X)
                )
                val accelerator = device.findAxis(
                    AxisCandidate(MotionEvent.AXIS_Y, inverted = true),
                    AxisCandidate(MotionEvent.AXIS_GAS),
                    AxisCandidate(MotionEvent.AXIS_RTRIGGER)
                )
                val brake = device.findAxis(
                    AxisCandidate(MotionEvent.AXIS_Z, inverted = true),
                    AxisCandidate(MotionEvent.AXIS_BRAKE),
                    AxisCandidate(MotionEvent.AXIS_LTRIGGER),
                    excludedAxis = accelerator?.axis
                )
                return AxisMapping(steering, accelerator, brake)
            }
        }
    }

    private data class AxisCandidate(
        val axis: Int,
        val inverted: Boolean = false
    )

    private data class AxisBinding(
        val axis: Int,
        val range: InputDevice.MotionRange,
        val inverted: Boolean
    ) {
        fun readSteering(event: MotionEvent): Float = normalizeSteeringAxis(
            value = event.getAxisValue(axis),
            min = range.min,
            max = range.max,
            flat = range.flat
        )

        fun readPedal(event: MotionEvent): Float = normalizePedalAxis(
            value = event.getAxisValue(axis),
            min = range.min,
            max = range.max,
            flat = range.flat,
            inverted = inverted
        )
    }

    private companion object {
        const val LOGITECH_VENDOR_ID = 0x046D
        const val G920_INITIAL_MODE_PRODUCT_ID = 0xC261
        const val G920_ACTIVE_MODE_PRODUCT_ID = 0xC262

        fun InputDevice.isLogitechG920(): Boolean {
            val matchesUsbId = vendorId == LOGITECH_VENDOR_ID &&
                (productId == G920_INITIAL_MODE_PRODUCT_ID || productId == G920_ACTIVE_MODE_PRODUCT_ID)
            val matchesName = name.lowercase(Locale.US).contains("g920")
            return matchesUsbId || matchesName
        }

        fun InputDevice.g920DeviceScore(): Int {
            var score = 0
            if (productId == G920_ACTIVE_MODE_PRODUCT_ID) score += 4
            if (supportsSource(InputDevice.SOURCE_JOYSTICK)) score += 2
            if (getMotionRange(MotionEvent.AXIS_WHEEL) != null ||
                getMotionRange(MotionEvent.AXIS_X) != null
            ) {
                score += 1
            }
            return score
        }

        fun InputDevice.findAxis(
            vararg candidates: AxisCandidate,
            excludedAxis: Int? = null
        ): AxisBinding? {
            candidates.forEach { candidate ->
                if (candidate.axis == excludedAxis) {
                    return@forEach
                }
                val range = getMotionRange(candidate.axis, InputDevice.SOURCE_JOYSTICK)
                    ?: getMotionRange(candidate.axis)
                if (range != null) {
                    return AxisBinding(candidate.axis, range, candidate.inverted)
                }
            }
            return null
        }
    }
}
