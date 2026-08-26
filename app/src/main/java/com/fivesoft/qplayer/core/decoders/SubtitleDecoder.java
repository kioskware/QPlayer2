package com.fivesoft.qplayer.core.decoders;

import androidx.annotation.NonNull;

import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.base.track.SubtitleTrack;
import com.fivesoft.qplayer.common.SubtitleReceiver;

public abstract class SubtitleDecoder extends MediaDecoder<SubtitleTrack, SubtitleReceiver> {

    /**
     * Creates a new media decoder for the specified subtitle track.
     * @param track The track that the decoder decodes. Cannot be null.
     */

    public SubtitleDecoder(@NonNull SubtitleTrack track, int sampleFormat, int maxEncodedFrameSize) {
        super(track, sampleFormat, maxEncodedFrameSize);
    }

}
