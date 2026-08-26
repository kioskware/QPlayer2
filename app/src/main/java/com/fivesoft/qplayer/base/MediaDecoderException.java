package com.fivesoft.qplayer.base;

import com.fivesoft.qplayer.base.components.MediaDecoder;

/**
 * Thrown when exception occurs during media decoding.
 * @see MediaDecoder
 */

public class MediaDecoderException extends Exception {

    public MediaDecoderException(String message) {
        super(message);
    }

    public MediaDecoderException(String message, Throwable cause) {
        super(message, cause);
    }

    public MediaDecoderException(Throwable cause) {
        super(cause);
    }

}
