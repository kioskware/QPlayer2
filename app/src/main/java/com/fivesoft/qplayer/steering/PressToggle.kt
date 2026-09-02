package com.fivesoft.qplayer.steering

internal class PressToggle(initialValue: Boolean) {

    var value: Boolean = initialValue
        private set

    private var wasPressed = false

    fun update(pressed: Boolean): Boolean {
        if (pressed && !wasPressed) {
            value = !value
        }
        wasPressed = pressed
        return value
    }

    fun release() {
        wasPressed = false
    }

    fun reset(value: Boolean) {
        this.value = value
        wasPressed = false
    }
}
