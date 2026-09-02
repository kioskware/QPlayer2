package com.fivesoft.qplayer.steering

import kotlin.math.abs

data class SteeringInputState(
    val connected: Boolean = false,
    val inputAvailable: Boolean = false,
    val deviceName: String? = null,
    val statusMessage: String? = null,
    val steering: Float = 0f,
    val accelerator: Float = 0f,
    val brake: Float = 0f,
    val gear: SteeringGear = SteeringGear.Unknown,
    val controlsVisible: Boolean = true
)

enum class SteeringGear(val displayValue: String) {
    Unknown("\u2014"),
    Reverse("R"),
    Neutral("N"),
    First("1"),
    Second("2"),
    Third("3"),
    Fourth("4"),
    Fifth("5"),
    Sixth("6")
}

internal fun SteeringGear.shiftUp(): SteeringGear = when (this) {
    SteeringGear.Unknown, SteeringGear.Neutral -> SteeringGear.First
    SteeringGear.Reverse -> SteeringGear.Neutral
    SteeringGear.First -> SteeringGear.Second
    SteeringGear.Second -> SteeringGear.Third
    SteeringGear.Third -> SteeringGear.Fourth
    SteeringGear.Fourth -> SteeringGear.Fifth
    SteeringGear.Fifth, SteeringGear.Sixth -> SteeringGear.Sixth
}

internal fun SteeringGear.shiftDown(): SteeringGear = when (this) {
    SteeringGear.Unknown, SteeringGear.Neutral -> SteeringGear.Reverse
    SteeringGear.Reverse -> SteeringGear.Reverse
    SteeringGear.First -> SteeringGear.Neutral
    SteeringGear.Second -> SteeringGear.First
    SteeringGear.Third -> SteeringGear.Second
    SteeringGear.Fourth -> SteeringGear.Third
    SteeringGear.Fifth -> SteeringGear.Fourth
    SteeringGear.Sixth -> SteeringGear.Fifth
}

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
