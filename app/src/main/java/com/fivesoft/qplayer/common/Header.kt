package com.fivesoft.qplayer.common

data class Header(
    val name: String,
    val value: String
) {
    override fun toString(): String {
        return "$name: $value"
    }
}
