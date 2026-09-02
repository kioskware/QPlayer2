package com.fivesoft.qplayer.steering

import kotlin.math.abs

data class SteeringInputState(
    val connected: Boolean = false,
    val deviceName: String? = null,
    val steering: Float = 0f,
    val accelerator: Float = 0f,
    val brake: Float = 0f
)

internal fun normalizeSteeringAxis(
    value: Float,
    min: Float,
    max: Float,
    flat: Float = 0f
): Float {
    if (!value.isFinite() || !min.isFinite() || !max.isFinite() || max <= min) {
        return 0f
    }

    val center = (min + max) / 2f
    val distanceFromCenter = value.coerceIn(min, max) - center
    if (abs(distanceFromCenter) <= flat.coerceAtLeast(0f)) {
        return 0f
    }

    val sideRange = if (distanceFromCenter < 0f) center - min else max - center
    return if (sideRange > 0f) {
        (distanceFromCenter / sideRange).coerceIn(-1f, 1f)
    } else {
        0f
    }
}

internal fun normalizePedalAxis(
    value: Float,
    min: Float,
    max: Float,
    flat: Float = 0f,
    inverted: Boolean = false
): Float {
    if (!value.isFinite() || !min.isFinite() || !max.isFinite() || max <= min) {
        return 0f
    }

    var normalized = ((value.coerceIn(min, max) - min) / (max - min)).coerceIn(0f, 1f)
    if (inverted) {
        normalized = 1f - normalized
    }

    val normalizedFlat = (flat.coerceAtLeast(0f) / (max - min)).coerceIn(0f, 0.99f)
    return if (normalized <= normalizedFlat) {
        0f
    } else {
        ((normalized - normalizedFlat) / (1f - normalizedFlat)).coerceIn(0f, 1f)
    }
}
