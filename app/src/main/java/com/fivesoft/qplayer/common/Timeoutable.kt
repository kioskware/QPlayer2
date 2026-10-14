package com.fivesoft.qplayer.common

/**
 * Interface for objects that support timeout.<br></br>
 * Class implementing timeout should allow user to set timeout
 * value at any time and throw [TimeoutException]
 * if the operation takes longer than the timeout value. Timeout value is in milliseconds.<br></br>
 * If the value is <= 0, then timeout is disabled. (will wait forever)<br></br>
 */
@Suppress("SpellCheckingInspection")
interface Timeoutable {

    /**
     * Can be changed dynamically.
     * The timeout value in milliseconds. Non-positive value means no timeout. (waiting forever)
     */
    var timeout: Int

    companion object {
        /**
         * Throws [TimeoutException] with no timeout specified.
         */
        fun throwTimeoutException() {
            throw TimeoutException()
        }

        /**
         * Throws [TimeoutException] with specified timeout.
         * @param timeout The timeout in milliseconds.
         */
        fun throwTimeoutException(timeout: Int) {
            throw TimeoutException(timeout)
        }
    }
}
