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

    private val inputManager = context.applicationContext.getSystemService(InputManager::class.java)
    private val mutableState = MutableStateFlow(SteeringInputState())

    val state: StateFlow<SteeringInputState> = mutableState.asStateFlow()

    @Volatile
    private var started = false

    @Volatile
    private var activeDeviceId: Int? = null

    @Volatile
    private var axisMapping: AxisMapping? = null

    @Synchronized
    fun start() {
        if (started) {
            return
        }
        started = true
        inputManager.registerInputDeviceListener(this, null)
        refreshDevice()
    }

    @Synchronized
    fun stop() {
        if (!started) {
            return
        }
        started = false
        inputManager.unregisterInputDeviceListener(this)
        activeDeviceId = null
        axisMapping = null
        mutableState.value = SteeringInputState()
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (!started || event.actionMasked != MotionEvent.ACTION_MOVE ||
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
            deviceName = device.name,
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
            mutableState.value = SteeringInputState()
        } else {
            connectDevice(device)
        }
    }

    @Synchronized
    private fun connectDevice(device: InputDevice) {
        val sameDevice = activeDeviceId == device.id
        activeDeviceId = device.id
        axisMapping = AxisMapping.from(device)
        val previousState = mutableState.value
        mutableState.value = SteeringInputState(
            connected = true,
            deviceName = device.name,
            steering = if (sameDevice) previousState.steering else 0f,
            accelerator = if (sameDevice) previousState.accelerator else 0f,
            brake = if (sameDevice) previousState.brake else 0f
        )
    }

    private data class AxisMapping(
        val steering: AxisBinding?,
        val accelerator: AxisBinding?,
        val brake: AxisBinding?
    ) {
        val hasAnyAxis: Boolean
            get() = steering != null || accelerator != null || brake != null

        companion object {
            fun from(device: InputDevice): AxisMapping {
                val steering = device.findAxis(
                    AxisCandidate(MotionEvent.AXIS_WHEEL),
                    AxisCandidate(MotionEvent.AXIS_X)
                )
                val accelerator = device.findAxis(
                    AxisCandidate(MotionEvent.AXIS_GAS),
                    AxisCandidate(MotionEvent.AXIS_RTRIGGER),
                    AxisCandidate(MotionEvent.AXIS_Z, inverted = true)
                )
                val brake = device.findAxis(
                    AxisCandidate(MotionEvent.AXIS_BRAKE),
                    AxisCandidate(MotionEvent.AXIS_LTRIGGER),
                    AxisCandidate(MotionEvent.AXIS_RZ, inverted = true),
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
