package com.fivesoft.qplayer.steering

internal data class LogitechG920Report(
    val steering: Float,
    val accelerator: Float,
    val brake: Float
)

internal object LogitechG920ReportParser {

    fun parse(report: ByteArray, length: Int): LogitechG920Report? {
        if (length < MIN_REPORT_LENGTH || length > report.size || report[REPORT_ID_OFFSET].toInt() != REPORT_ID) {
            return null
        }

        val steeringRaw = report[STEERING_LOW_OFFSET].unsigned() or
            (report[STEERING_HIGH_OFFSET].unsigned() shl 8)

        return LogitechG920Report(
            steering = normalizeSteeringAxis(
                value = steeringRaw.toFloat(),
                min = 0f,
                max = U16_MAX.toFloat(),
                flat = STEERING_DEAD_ZONE.toFloat()
            ),
            accelerator = normalizePedalAxis(
                value = report[ACCELERATOR_OFFSET].unsigned().toFloat(),
                min = 0f,
                max = U8_MAX.toFloat(),
                flat = PEDAL_DEAD_ZONE.toFloat(),
                inverted = true
            ),
            brake = normalizePedalAxis(
                value = report[BRAKE_OFFSET].unsigned().toFloat(),
                min = 0f,
                max = U8_MAX.toFloat(),
                flat = PEDAL_DEAD_ZONE.toFloat(),
                inverted = true
            )
        )
    }

    private fun Byte.unsigned(): Int = toInt() and U8_MAX

    private const val REPORT_ID_OFFSET = 0
    private const val REPORT_ID = 0x01
    private const val STEERING_LOW_OFFSET = 4
    private const val STEERING_HIGH_OFFSET = 5
    private const val ACCELERATOR_OFFSET = 6
    private const val BRAKE_OFFSET = 7
    private const val MIN_REPORT_LENGTH = 8
    private const val STEERING_DEAD_ZONE = 300
    private const val PEDAL_DEAD_ZONE = 10
    private const val U8_MAX = 0xFF
    private const val U16_MAX = 0xFFFF
}
