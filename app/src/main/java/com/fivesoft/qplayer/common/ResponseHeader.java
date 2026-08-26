package com.fivesoft.qplayer.common;

public class ResponseHeader {

    public final int code;
    public final String message;
    public final Headers headers;

    public ResponseHeader(int code, String message, Headers headers) {
        this.code = code;
        this.message = message;
        this.headers = headers;
    }

}
