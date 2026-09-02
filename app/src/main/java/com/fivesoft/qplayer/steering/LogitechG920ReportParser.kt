package com.fivesoft.qplayer.steering

internal data class LogitechG920Report(
    val steering: Float,
    val accelerator: Float,
    val brake: Float,
    val gear: SteeringGear,
    val shiftUpPressed: Boolean,
    val shiftDownPressed: Boolean
)

internal object LogitechG920ReportParser {

    fun parse(report: ByteArray, length: Int): LogitechG920Report? {
        if (length < MIN_REPORT_LENGTH || length > report.size || report[REPORT_ID_OFFSET].toInt() != REPORT_ID) {
            return null
        }

        val steeringRaw = report[STEERING_LOW_OFFSET].unsigned() or
            (report[STEERING_HIGH_OFFSET].unsigned() shl 8)
        val buttons = decodeButtons(report)

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
            ),
            gear = decodeGear(buttons),
            shiftUpPressed = buttons.isButtonPressed(SHIFT_UP_BUTTON),
            shiftDownPressed = buttons.isButtonPressed(SHIFT_DOWN_BUTTON)
        )
    }

    private fun decodeButtons(report: ByteArray): Int =
        (report[BUTTONS_FIRST_OFFSET].unsigned() ushr HAT_BITS) or
            (report[BUTTONS_SECOND_OFFSET].unsigned() shl FIRST_BYTE_BUTTON_BITS) or
            ((report[BUTTONS_THIRD_OFFSET].unsigned() and LAST_BUTTON_BYTE_MASK) shl
                (FIRST_BYTE_BUTTON_BITS + Byte.SIZE_BITS))

    private fun decodeGear(buttons: Int): SteeringGear =
        when {
            buttons.isButtonPressed(REVERSE_BUTTON) -> SteeringGear.Reverse
            buttons.isButtonPressed(FIRST_GEAR_BUTTON) -> SteeringGear.First
            buttons.isButtonPressed(SECOND_GEAR_BUTTON) -> SteeringGear.Second
            buttons.isButtonPressed(THIRD_GEAR_BUTTON) -> SteeringGear.Third
            buttons.isButtonPressed(FOURTH_GEAR_BUTTON) -> SteeringGear.Fourth
            buttons.isButtonPressed(FIFTH_GEAR_BUTTON) -> SteeringGear.Fifth
            buttons.isButtonPressed(SIXTH_GEAR_BUTTON) -> SteeringGear.Sixth
            else -> SteeringGear.Neutral
        }

    private fun Int.isButtonPressed(buttonNumber: Int): Boolean =
        this and (1 shl (buttonNumber - 1)) != 0

    private fun Byte.unsigned(): Int = toInt() and U8_MAX

    private const val REPORT_ID_OFFSET = 0
    private const val REPORT_ID = 0x01
    private const val BUTTONS_FIRST_OFFSET = 1
    private const val BUTTONS_SECOND_OFFSET = 2
    private const val BUTTONS_THIRD_OFFSET = 3
    private const val STEERING_LOW_OFFSET = 4
    private const val STEERING_HIGH_OFFSET = 5
    private const val ACCELERATOR_OFFSET = 6
    private const val BRAKE_OFFSET = 7
    private const val MIN_REPORT_LENGTH = 8
    private const val STEERING_DEAD_ZONE = 300
    private const val PEDAL_DEAD_ZONE = 10
    private const val U8_MAX = 0xFF
    private const val U16_MAX = 0xFFFF
    private const val HAT_BITS = 4
    private const val FIRST_BYTE_BUTTON_BITS = 4
    private const val LAST_BUTTON_BYTE_MASK = 0x3F
    private const val SHIFT_UP_BUTTON = 5
    private const val SHIFT_DOWN_BUTTON = 6
    private const val REVERSE_BUTTON = 12
    private const val FIRST_GEAR_BUTTON = 13
    private const val SECOND_GEAR_BUTTON = 14
    private const val THIRD_GEAR_BUTTON = 15
    private const val FOURTH_GEAR_BUTTON = 16
    private const val FIFTH_GEAR_BUTTON = 17
    private const val SIXTH_GEAR_BUTTON = 18
}
