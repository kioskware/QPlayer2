package com.fivesoft.qplayer.impl.extractor.rtp;

import com.fivesoft.qplayer.base.track.Track;

/** Transport-level MP2T payload; elementary tracks are discovered from PAT/PMT. */
public final class RtpTransportStreamTrack extends Track {
    public RtpTransportStreamTrack(int payloadType) {
        super("rtp-mp2t-" + payloadType, payloadType);
        setFormat("mp2t");
        setClockRate(90000);
    }
}
