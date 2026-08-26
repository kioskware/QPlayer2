package com.fivesoft.qplayer.core.decoders;

import androidx.annotation.NonNull;

import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.base.track.AudioTrack;

public abstract class AudioDecoder extends MediaDecoder<AudioTrack, android.media.AudioTrack> {

    public AudioDecoder(@NonNull AudioTrack track, int sampleFormat, int maxEncodedFrameSize)
            throws UnsupportedSampleFormatException {
        super(track, sampleFormat, maxEncodedFrameSize);
    }

}
