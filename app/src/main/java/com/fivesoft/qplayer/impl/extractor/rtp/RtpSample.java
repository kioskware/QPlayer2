package com.fivesoft.qplayer.impl.extractor.rtp;

import androidx.annotation.NonNull;

import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.track.Track;

/** RTP payload with transport information needed to detect incomplete codec fragments. */
public final class RtpSample extends Sample {

    public final int sequenceNumber;
    public final long rtpTimestamp;
    public final long ssrc;
    public final boolean marker;
    public final boolean discontinuity;

    public RtpSample(@NonNull byte[] data, int offset, int length, long timestamp,
                     @NonNull Track track, @NonNull RtpParser.RtpHeader header,
                     boolean discontinuity) {
        super(data, offset, length, timestamp, track);
        sequenceNumber = header.sequenceNumber;
        rtpTimestamp = header.timestamp;
        ssrc = header.ssrc;
        marker = header.marker != 0;
        this.discontinuity = discontinuity;
    }
}
