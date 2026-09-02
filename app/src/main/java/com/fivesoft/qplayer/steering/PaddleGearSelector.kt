package com.fivesoft.qplayer.steering

internal class PaddleGearSelector {

    private var selectedGear = SteeringGear.Neutral
    private var previousShifterGear = SteeringGear.Neutral
    private var shiftUpWasPressed = false
    private var shiftDownWasPressed = false

    fun update(
        shifterGear: SteeringGear,
        shiftUpPressed: Boolean,
        shiftDownPressed: Boolean
    ): SteeringGear {
        val shiftUpEdge = shiftUpPressed && !shiftUpWasPressed
        val shiftDownEdge = shiftDownPressed && !shiftDownWasPressed
        val shifterReturnedToNeutral = previousShifterGear.isDirectGear() &&
            shifterGear == SteeringGear.Neutral

        selectedGear = when {
            shifterGear.isDirectGear() -> shifterGear
            shifterReturnedToNeutral -> SteeringGear.Neutral
            shiftUpEdge && !shiftDownPressed -> selectedGear.shiftUp()
            shiftDownEdge && !shiftUpPressed -> selectedGear.shiftDown()
            else -> selectedGear
        }

        previousShifterGear = shifterGear
        shiftUpWasPressed = shiftUpPressed
        shiftDownWasPressed = shiftDownPressed
        return selectedGear
    }

    fun reset() {
        selectedGear = SteeringGear.Neutral
        previousShifterGear = SteeringGear.Neutral
        shiftUpWasPressed = false
        shiftDownWasPressed = false
    }

    private fun SteeringGear.isDirectGear(): Boolean =
        this != SteeringGear.Unknown && this != SteeringGear.Neutral
}
