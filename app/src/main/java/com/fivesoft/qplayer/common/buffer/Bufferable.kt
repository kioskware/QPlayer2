package com.fivesoft.qplayer.common.buffer;

/**
 * Base interface for objects that can be buffered in {@link Buffer}.<br>
 */
public interface Bufferable {

    /**
     * Get the timestamp of the object.<br>
     * Later objects should have greater timestamps.<br>
     * @return The timestamp of the object in milliseconds.<br>
     */

    long getTimestamp();

}
