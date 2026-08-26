package com.fivesoft.qplayer.impl.decoder.video.h264;

import java.nio.ByteBuffer;

public class NalUnitBuffer {

    private final ByteBuffer buffer;
    private byte nalType;

    public NalUnitBuffer(int maxNalUnitSize) {
        this.buffer = ByteBuffer.allocate(maxNalUnitSize);
    }


}
