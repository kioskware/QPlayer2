package com.fivesoft.qplayer.base;

import androidx.annotation.NonNull;

import com.fivesoft.qplayer.base.track.Track;

public class Frame extends Sample {

    public static final byte UNKNOWN_FRAME = 0x00;
    public static final byte NON_SYNC_FRAME = 0x01;
    public static final byte SYNC_FRAME = 0x02;

    public static final byte CONFIG_FRAME = 0x03;

    public volatile byte frameType;

    public Frame(@NonNull byte[] data, int off, int len, long timestamp, @NonNull Track track, byte frameType) {
        super(data, off, len, timestamp, track);
        this.frameType = frameType;
    }

    public Frame(@NonNull byte[] data, long timestamp, @NonNull Track track, byte frameType) {
        super(data, timestamp, track);
        this.frameType = frameType;
    }

}
