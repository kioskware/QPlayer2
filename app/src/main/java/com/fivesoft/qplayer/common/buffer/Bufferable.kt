package com.fivesoft.qplayer.common.buffer

/**
 * Base interface for objects that can be buffered in [Buffer].<br></br>
 */
interface Bufferable {
    /**
     * Get the timestamp of the object.<br></br>
     * Later objects should have greater timestamps.<br></br>
     * @return The timestamp of the object in milliseconds.<br></br>
     */
    val timestamp: Long
}
