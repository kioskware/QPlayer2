package com.fivesoft.qplayer.base;

import com.fivesoft.qplayer.base.components.MediaDecoder;

/**
 * Thrown when the {@link MediaDecoder} does not support passed {@link Sample} or {@link Frame}.<br>
 * This may be caused by:
 * <ul>
 *     <li>Unsupported sample/frame format.</li>
 *     <li>Unsupported sample/frame type.</li>
 *     <li>Corrupted sample/frame</li>
 * </ul>
 */

public class UnsupportedSampleException extends Exception {

    public UnsupportedSampleException() {
        super();
    }

    public UnsupportedSampleException(String message) {
        super(message);
    }

    public UnsupportedSampleException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsupportedSampleException(Throwable cause) {
        super(cause);
    }
}
