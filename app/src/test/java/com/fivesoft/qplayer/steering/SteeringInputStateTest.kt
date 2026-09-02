package com.fivesoft.qplayer.steering

import org.junit.Assert.assertEquals
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
}
