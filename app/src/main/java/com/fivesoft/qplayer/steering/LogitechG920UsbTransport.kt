package com.fivesoft.qplayer.steering

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

internal class LogitechG920UsbTransport(
    context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onUsbDeviceDetected(deviceName: String, statusMessage: String)
        fun onUsbInputReady(deviceName: String)
        fun onUsbInput(report: LogitechG920Report)
        fun onUsbDeviceUnavailable()
    }

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(UsbManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val permissionAction = "${appContext.packageName}.steering.USB_PERMISSION"
    private val permissionIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        Intent(permissionAction).setPackage(appContext.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    @Volatile
    private var started = false

    @Volatile
    private var readGeneration = 0

    private var permissionRequestedDeviceName: String? = null
    @Volatile
    private var modeSwitchDeviceName: String? = null
    private var configuredDeviceName: String? = null
    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != permissionAction) return

            val device = intent.usbDeviceExtra() ?: return
            permissionRequestedDeviceName = null
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) &&
                usbManager.hasPermission(device)
            ) {
                prepareDevice(device)
            } else {
                Log.w(TAG, "USB permission denied for ${device.usbIdentity()}")
                listener.onUsbDeviceDetected(
                    device.displayName(),
                    "Logitech G920 \u2014 brak zgody na dost\u0119p USB"
                )
            }
        }
    }

    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> mainHandler.postDelayed(::scanDevices, ATTACH_DELAY_MS)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    intent.usbDeviceExtra()?.let { detachedDevice ->
                        if (detachedDevice.deviceName == configuredDeviceName) {
                            closeConnection()
                        }
                    }
                    mainHandler.postDelayed(::scanDevices, DETACH_DELAY_MS)
                }
            }
        }
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true

        ContextCompat.registerReceiver(
            appContext,
            permissionReceiver,
            IntentFilter(permissionAction),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            appContext,
            deviceReceiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            },
            ContextCompat.RECEIVER_EXPORTED
        )
        scanDevices()
    }

    @Synchronized
    fun stop() {
        if (!started) return
        started = false
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { appContext.unregisterReceiver(permissionReceiver) }
        runCatching { appContext.unregisterReceiver(deviceReceiver) }
        permissionRequestedDeviceName = null
        modeSwitchDeviceName = null
        closeConnection()
    }

    @Synchronized
    private fun scanDevices() {
        if (!started) return

        val device = usbManager.deviceList.values
            .asSequence()
            .filter { candidate -> candidate.isLogitechG920() }
            .maxByOrNull { candidate ->
                if (candidate.productId == G920_ACTIVE_MODE_PRODUCT_ID) 1 else 0
            }

        if (device == null) {
            permissionRequestedDeviceName = null
            modeSwitchDeviceName = null
            closeConnection()
            listener.onUsbDeviceUnavailable()
            return
        }

        Log.i(TAG, "Detected ${device.usbIdentity()}")
        val status = if (device.productId == G920_INITIAL_MODE_PRODUCT_ID) {
            "Logitech G920 \u2014 prze\u0142\u0105czanie do trybu HID"
        } else {
            "Logitech G920 \u2014 inicjalizacja wej\u015bcia USB"
        }
        listener.onUsbDeviceDetected(device.displayName(), status)

        if (configuredDeviceName == device.deviceName) return
        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
        } else {
            prepareDevice(device)
        }
    }

    @Synchronized
    private fun requestPermission(device: UsbDevice) {
        if (permissionRequestedDeviceName == device.deviceName) return
        permissionRequestedDeviceName = device.deviceName
        Log.i(TAG, "Requesting USB permission for ${device.usbIdentity()}")
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun prepareDevice(device: UsbDevice) {
        if (!started || !device.isLogitechG920()) return
        if (device.productId == G920_INITIAL_MODE_PRODUCT_ID) {
            switchToHidMode(device)
        } else {
            mainHandler.postDelayed(
                { if (started) openRawInput(device) },
                INPUT_DEVICE_GRACE_PERIOD_MS
            )
        }
    }

    private fun switchToHidMode(device: UsbDevice) {
        synchronized(this) {
            if (modeSwitchDeviceName == device.deviceName) return
            modeSwitchDeviceName = device.deviceName
        }
        listener.onUsbDeviceDetected(
            device.displayName(),
            "Logitech G920 \u2014 prze\u0142\u0105czanie do trybu HID"
        )
        Thread({
            if (!started) {
                modeSwitchDeviceName = null
                return@Thread
            }
            val binding = device.findEndpoint(
                direction = UsbConstants.USB_DIR_OUT,
                preferredType = UsbConstants.USB_ENDPOINT_XFER_INT
            )
            if (binding == null) {
                modeSwitchDeviceName = null
                reportUsbError(device, "brak wyj\u015bciowego endpointu USB")
                return@Thread
            }

            val modeConnection = usbManager.openDevice(device)
            if (modeConnection == null || !modeConnection.claimInterface(binding.usbInterface, true)) {
                modeConnection?.close()
                modeSwitchDeviceName = null
                reportUsbError(device, "nie mo\u017cna otworzy\u0107 interfejsu USB")
                return@Thread
            }

            val transferred = try {
                modeConnection.bulkTransfer(
                    binding.endpoint,
                    G920_HID_MODE_COMMAND,
                    G920_HID_MODE_COMMAND.size,
                    TRANSFER_TIMEOUT_MS
                )
            } finally {
                modeConnection.releaseInterface(binding.usbInterface)
                modeConnection.close()
            }

            if (transferred == G920_HID_MODE_COMMAND.size) {
                Log.i(TAG, "G920 HID mode command sent; waiting for USB re-enumeration")
                mainHandler.postDelayed(::scanDevices, MODE_SWITCH_DELAY_MS)
            } else {
                modeSwitchDeviceName = null
                reportUsbError(device, "nie uda\u0142o si\u0119 w\u0142\u0105czy\u0107 trybu HID ($transferred)")
            }
        }, "G920-mode-switch").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    private fun openRawInput(device: UsbDevice) {
        if (!started || configuredDeviceName == device.deviceName) return

        val binding = device.findEndpoint(
            direction = UsbConstants.USB_DIR_IN,
            preferredType = UsbConstants.USB_ENDPOINT_XFER_INT
        )
        if (binding == null) {
            reportUsbError(device, "brak wej\u015bciowego endpointu USB")
            return
        }

        val newConnection = usbManager.openDevice(device)
        if (newConnection == null || !newConnection.claimInterface(binding.usbInterface, true)) {
            newConnection?.close()
            reportUsbError(device, "nie mo\u017cna odczyta\u0107 interfejsu USB")
            return
        }

        closeConnection()
        connection = newConnection
        claimedInterface = binding.usbInterface
        configuredDeviceName = device.deviceName
        val generation = ++readGeneration
        listener.onUsbInputReady(device.displayName())
        Log.i(
            TAG,
            "Reading raw G920 reports from interface ${binding.usbInterface.id}, " +
                "endpoint 0x${binding.endpoint.address.toString(16)}"
        )

        Thread({
            val buffer = ByteArray(binding.endpoint.maxPacketSize.coerceAtLeast(MIN_USB_BUFFER_SIZE))
            while (started && generation == readGeneration && !Thread.currentThread().isInterrupted) {
                val length = newConnection.bulkTransfer(
                    binding.endpoint,
                    buffer,
                    buffer.size,
                    READ_TIMEOUT_MS
                )
                if (length > 0) {
                    LogitechG920ReportParser.parse(buffer, length)?.let(listener::onUsbInput)
                }
            }
        }, "G920-input").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    private fun closeConnection() {
        readGeneration++
        val oldConnection = connection
        val oldInterface = claimedInterface
        connection = null
        claimedInterface = null
        configuredDeviceName = null
        if (oldConnection != null) {
            if (oldInterface != null) {
                runCatching { oldConnection.releaseInterface(oldInterface) }
            }
            oldConnection.close()
        }
    }

    private fun reportUsbError(device: UsbDevice, detail: String) {
        Log.e(TAG, "${device.usbIdentity()}: $detail")
        mainHandler.post {
            if (started) {
                listener.onUsbDeviceDetected(
                    device.displayName(),
                    "Logitech G920 \u2014 $detail"
                )
            }
        }
    }

    private fun UsbDevice.findEndpoint(direction: Int, preferredType: Int): EndpointBinding? {
        var fallback: EndpointBinding? = null
        for (interfaceIndex in 0 until interfaceCount) {
            val usbInterface = getInterface(interfaceIndex)
            for (endpointIndex in 0 until usbInterface.endpointCount) {
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                if (endpoint.direction != direction) continue
                val binding = EndpointBinding(usbInterface, endpoint)
                if (endpoint.type == preferredType) return binding
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) fallback = fallback ?: binding
            }
        }
        return fallback
    }

    private fun UsbDevice.displayName(): String = runCatching { productName }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: "Logitech G920"

    private fun UsbDevice.usbIdentity(): String =
        "USB %04x:%04x (%s)".format(vendorId, productId, deviceName)

    private fun UsbDevice.isLogitechG920(): Boolean = vendorId == LOGITECH_VENDOR_ID &&
        (productId == G920_INITIAL_MODE_PRODUCT_ID || productId == G920_ACTIVE_MODE_PRODUCT_ID)

    @Suppress("DEPRECATION")
    private fun Intent.usbDeviceExtra(): UsbDevice? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    private data class EndpointBinding(
        val usbInterface: UsbInterface,
        val endpoint: UsbEndpoint
    )

    private companion object {
        const val TAG = "G920Usb"
        const val LOGITECH_VENDOR_ID = 0x046D
        const val G920_INITIAL_MODE_PRODUCT_ID = 0xC261
        const val G920_ACTIVE_MODE_PRODUCT_ID = 0xC262
        const val MIN_USB_BUFFER_SIZE = 64
        const val TRANSFER_TIMEOUT_MS = 1_000
        const val READ_TIMEOUT_MS = 250
        const val ATTACH_DELAY_MS = 300L
        const val DETACH_DELAY_MS = 300L
        const val MODE_SWITCH_DELAY_MS = 1_200L
        const val INPUT_DEVICE_GRACE_PERIOD_MS = 800L

        val G920_HID_MODE_COMMAND = byteArrayOf(0x0F, 0x00, 0x01, 0x01, 0x42)
    }
}
