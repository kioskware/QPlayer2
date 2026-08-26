package com.fivesoft.qplayer.common;

public class TimeoutException extends RuntimeException {

    public TimeoutException(int timeout) {
        super("No response after " + (timeout <= 0 ? "NO TIMEOUT" : " ms."));
    }
    
    public TimeoutException() {
        super("No response after timeout.");
    }
    
}
