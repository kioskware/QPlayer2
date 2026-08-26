package com.fivesoft.qplayer.base;

import androidx.annotation.NonNull;

import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.common.ByteArray;
import com.fivesoft.qplayer.common.buffer.Bufferable;

import java.util.Objects;

public class Sample extends ByteArray implements Bufferable {

    private long timestamp;
    @NonNull
    private Track track;

    public Sample(@NonNull byte[] data, int off, int len, long timestamp, @NonNull Track track) {
        super(data, off, len);
        this.timestamp = timestamp;
        this.track = Objects.requireNonNull(track);
    }

    public Sample(@NonNull byte[] data, long timestamp, @NonNull Track track) {
        super(data);
        this.timestamp = timestamp;
        this.track = Objects.requireNonNull(track);
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp){
        this.timestamp = timestamp;
    }

    @NonNull
    public Track getTrack(){
        return track;
    }

    public void setTrack(@NonNull Track track){
        this.track = Objects.requireNonNull(track);
    }

}
