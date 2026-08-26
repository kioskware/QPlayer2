package com.fivesoft.qplayer.common;

public interface BufferReceiver {

    boolean onBufferReceived(long timestamp, byte[] buffer, int offset, int length);

}
