package com.fivesoft.qplayer.steering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteeringInputStateTest {

    @Test
    fun steeringAxisIsNormalizedAroundItsCenter() {
        assertEquals(-1f, normalizeSteeringAxis(0f, 0f, 65_535f))
        assertEquals(0f, normalizeSteeringAxis(32_767.5f, 0f, 65_535f))
        assertEquals(1f, normalizeSteeringAxis(65_535f, 0f, 65_535f))
    }

    @Test
    fun steeringDeadZoneRemovesCenterNoise() {
        assertEquals(0f, normalizeSteeringAxis(0.03f, -1f, 1f, flat = 0.05f))
    }

    @Test
    fun pedalAxisSupportsStandardAndInvertedHardwareRanges() {
        assertEquals(0.25f, normalizePedalAxis(0.25f, 0f, 1f))
        assertEquals(0.75f, normalizePedalAxis(0.25f, 0f, 1f, inverted = true))
        assertEquals(0f, normalizePedalAxis(255f, 0f, 255f, inverted = true))
        assertEquals(1f, normalizePedalAxis(0f, 0f, 255f, inverted = true))
    }

    @Test
    fun nativeG920ReportProvidesSteeringAndPedals() {
        val report = byteArrayOf(
            0x01, 0x0F, 0x00, 0x00, 0x00, 0x80.toByte(),
            0x00, 0x80.toByte(), 0xFF.toByte(), 0x07
        )

        val parsed = requireNotNull(LogitechG920ReportParser.parse(report, report.size))

        assertEquals(0f, parsed.steering, 0.001f)
        assertEquals(1f, parsed.accelerator)
        assertEquals(0.48f, parsed.brake, 0.01f)
        assertEquals(SteeringGear.Neutral, parsed.gear)
    }

    @Test
    fun nativeG920ReportDecodesEveryShifterPosition() {
        val positions = listOf(
            Triple(2, 0x80, SteeringGear.Reverse),
            Triple(3, 0x01, SteeringGear.First),
            Triple(3, 0x02, SteeringGear.Second),
            Triple(3, 0x04, SteeringGear.Third),
            Triple(3, 0x08, SteeringGear.Fourth),
            Triple(3, 0x10, SteeringGear.Fifth),
            Triple(3, 0x20, SteeringGear.Sixth)
        )

        positions.forEach { (byteOffset, buttonMask, expectedGear) ->
            val report = neutralG920Report().apply {
                this[byteOffset] = buttonMask.toByte()
            }

            assertEquals(
                expectedGear,
                requireNotNull(LogitechG920ReportParser.parse(report, report.size)).gear
            )
        }
    }

    @Test
    fun nativeG920ReportDecodesBothShiftPaddles() {
        val shiftUpReport = neutralG920Report().apply { this[2] = 0x01 }
        val shiftDownReport = neutralG920Report().apply { this[2] = 0x02 }

        val shiftUp = requireNotNull(
            LogitechG920ReportParser.parse(shiftUpReport, shiftUpReport.size)
        )
        val shiftDown = requireNotNull(
            LogitechG920ReportParser.parse(shiftDownReport, shiftDownReport.size)
        )

        assertTrue(shiftUp.shiftUpPressed)
        assertFalse(shiftUp.shiftDownPressed)
        assertFalse(shiftDown.shiftUpPressed)
        assertTrue(shiftDown.shiftDownPressed)
    }

    @Test
    fun nativeG920ReportDecodesXButton() {
        val report = neutralG920Report().apply {
            this[1] = (this[1].toInt() or 0x40).toByte()
        }

        val parsed = requireNotNull(LogitechG920ReportParser.parse(report, report.size))

        assertTrue(parsed.controlsTogglePressed)
    }

    @Test
    fun heldXButtonTogglesControlsOnlyOnce() {
        val toggle = PressToggle(initialValue = true)

        assertFalse(toggle.update(pressed = true))
        assertFalse(toggle.update(pressed = true))
        assertFalse(toggle.update(pressed = false))
        assertTrue(toggle.update(pressed = true))
    }

    @Test
    fun heldPaddleChangesOnlyOneGear() {
        val selector = PaddleGearSelector()

        assertEquals(SteeringGear.First, selector.update(SteeringGear.Neutral, true, false))
        assertEquals(SteeringGear.First, selector.update(SteeringGear.Neutral, true, false))
        assertEquals(SteeringGear.First, selector.update(SteeringGear.Neutral, false, false))
        assertEquals(SteeringGear.Second, selector.update(SteeringGear.Neutral, true, false))
    }

    @Test
    fun paddlesShiftAcrossReverseNeutralAndForwardGears() {
        val selector = PaddleGearSelector()

        assertEquals(SteeringGear.Reverse, selector.update(SteeringGear.Neutral, false, true))
        selector.update(SteeringGear.Neutral, false, false)
        assertEquals(SteeringGear.Neutral, selector.update(SteeringGear.Neutral, true, false))
        selector.update(SteeringGear.Neutral, false, false)
        assertEquals(SteeringGear.First, selector.update(SteeringGear.Neutral, true, false))
        selector.update(SteeringGear.Neutral, false, false)
        assertEquals(SteeringGear.Neutral, selector.update(SteeringGear.Neutral, false, true))
    }

    @Test
    fun hShifterOverridesPaddleSelectedGear() {
        val selector = PaddleGearSelector()
        selector.update(SteeringGear.Neutral, true, false)
        selector.update(SteeringGear.Neutral, false, false)

        assertEquals(SteeringGear.Fifth, selector.update(SteeringGear.Fifth, true, false))
        assertEquals(SteeringGear.Neutral, selector.update(SteeringGear.Neutral, false, false))
    }

    @Test
    fun unrelatedHidReportIsIgnored() {
        val report = byteArrayOf(0x11, 0, 0, 0, 0, 0, 0, 0)

        assertEquals(null, LogitechG920ReportParser.parse(report, report.size))
    }

    private fun neutralG920Report(): ByteArray = byteArrayOf(
        0x01, 0x0F, 0x00, 0x00, 0x00, 0x80.toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x07
    )
}
