package com.fivesoft.qplayer.common;

/**
 * Interface for objects that support timeout.<br>
 * Class implementing timeout should allow user to set timeout
 * value at any time and throw {@link TimeoutException}
 * if the operation takes longer than the timeout value. Timeout value is in milliseconds.<br>
 * If the value is <= 0, then timeout is disabled. (will wait forever)<br>
 */

@SuppressWarnings("SpellCheckingInspection")
public interface Timeoutable {

    /**
     * Sets the timeout value in milliseconds. Can be changed dynamically.
     * @param timeout The timeout value in milliseconds. Non-positive value means no timeout. (waiting forever)
     */

    void setTimeout(int timeout);

    /**
     * Gets the timeout value in milliseconds that is currently set using {@link #setTimeout(int)}.
     * @return The timeout value in milliseconds. Non-positive value means no timeout. (waiting forever)
     */

    int getTimeout();

    /**
     * Throws {@link TimeoutException} with no timeout specified.
     */

    static void throwTimeoutException(){
        throw new TimeoutException();
    }

    /**
     * Throws {@link TimeoutException} with specified timeout.
     * @param timeout The timeout in milliseconds.
     */

    static void throwTimeoutException(int timeout) {
        throw new TimeoutException(timeout);
    }

}
