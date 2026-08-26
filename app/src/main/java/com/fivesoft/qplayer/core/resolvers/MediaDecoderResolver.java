package com.fivesoft.qplayer.core.resolvers;

import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.base.components.MediaDecoderOutput;
import com.fivesoft.qplayer.base.track.AudioTrack;
import com.fivesoft.qplayer.base.track.SubtitleTrack;
import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.base.track.VideoTrack;
import com.fivesoft.qplayer.common.SubtitleReceiver;
import com.fivesoft.qplayer.core.decoders.AudioDecoder;
import com.fivesoft.qplayer.core.decoders.SubtitleDecoder;
import com.fivesoft.qplayer.core.decoders.VideoDecoder;

public class MediaDecoderResolver {

    @SuppressWarnings("unchecked")
    @Nullable
    public static MediaDecoder <?, ?> resolveDecoder(@NonNull MediaDecoder.Descriptor descriptor,
                                                            @Nullable MediaDecoderOutput<?> output)
            throws IllegalArgumentException {

        Track track = descriptor.track;
        if (track == null)
            return null;

        MediaDecoder<?, ?> res;

        try {
            if (track instanceof VideoTrack){
                VideoDecoder vd = VideoDecoderResolver.resolveDecoder(descriptor);
                if(vd != null){
                    vd.setOutput((MediaDecoderOutput<Surface>) output);
                }
                res = vd;
            } else if(track instanceof AudioTrack){
                AudioDecoder ad = AudioDecoderResolver.resolveDecoder(descriptor);
                if(ad != null){
                    ad.setOutput((MediaDecoderOutput<android.media.AudioTrack>) output);
                }
                res = ad;
            } else if(track instanceof SubtitleTrack){
                SubtitleDecoder sd = SubtitleDecoderResolver.resolveDecoder(descriptor);
                if(sd != null){
                    sd.setOutput((MediaDecoderOutput<SubtitleReceiver>) output);
                }
                res = sd;
            } else {
                return null;
            }
        } catch (ClassCastException | IllegalArgumentException e) {
            return null;
        }

        return res;
    }

}
