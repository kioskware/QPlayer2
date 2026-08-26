package com.fivesoft.qplayer.core.player;

import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Frame;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.base.components.MediaDecoderOutput;
import com.fivesoft.qplayer.base.track.AudioTrack;
import com.fivesoft.qplayer.base.track.SubtitleTrack;
import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.base.track.VideoTrack;
import com.fivesoft.qplayer.common.SubtitleReceiver;
import com.fivesoft.qplayer.common.resolver.Resolver;
import com.fivesoft.qplayer.core.decoders.AudioDecoder;
import com.fivesoft.qplayer.core.decoders.SubtitleDecoder;
import com.fivesoft.qplayer.core.decoders.VideoDecoder;
import com.fivesoft.qplayer.core.resolvers.AudioDecoderResolver;
import com.fivesoft.qplayer.core.resolvers.SubtitleDecoderResolver;
import com.fivesoft.qplayer.core.resolvers.VideoDecoderResolver;

public class DecodersManager {

    private final DecoderControllerMapper<Surface, VideoDecoder> videoDecoders;
    private final DecoderControllerMapper<android.media.AudioTrack, AudioDecoder> audioDecoders;
    private final DecoderControllerMapper<SubtitleReceiver, SubtitleDecoder> subtitleDecoders;

    public DecodersManager(@NonNull Resolver<MediaDecoder.Descriptor, VideoDecoder> videoDecoderResolver,
                           @NonNull Resolver<MediaDecoder.Descriptor, AudioDecoder> audioDecoderResolver,
                           @NonNull Resolver<MediaDecoder.Descriptor, SubtitleDecoder> subtitleDecoderResolver,
                           @Nullable MediaDecoderOutput.Creator<Surface> videoOutputCreator,
                           @Nullable MediaDecoderOutput.Creator<android.media.AudioTrack> audioOutputCreator,
                           @Nullable MediaDecoderOutput.Creator<SubtitleReceiver> subtitleOutputCreator,
                           int sampleFormat, int maxVideoFrameSize, int maxAudioFrameSize, int maxSubtitleFrameSize) {
        videoDecoders = new DecoderControllerMapper<>(videoDecoderResolver, videoOutputCreator, sampleFormat, maxVideoFrameSize);
        audioDecoders = new DecoderControllerMapper<>(audioDecoderResolver, audioOutputCreator, sampleFormat, maxAudioFrameSize);
        subtitleDecoders = new DecoderControllerMapper<>(subtitleDecoderResolver, subtitleOutputCreator, sampleFormat, maxSubtitleFrameSize);
    }

    public static DecodersManager createDefault(@Nullable MediaDecoderOutput.Creator<Surface> videoOutputCreator,
                                                @Nullable MediaDecoderOutput.Creator<android.media.AudioTrack> audioOutputCreator,
                                                @Nullable MediaDecoderOutput.Creator<SubtitleReceiver> subtitleOutputCreator,
                                                int sampleFormat){

        return new DecodersManager(
                VideoDecoderResolver.getInstance(),
                AudioDecoderResolver.getInstance(),
                SubtitleDecoderResolver.getInstance(),
                videoOutputCreator,
                audioOutputCreator,
                subtitleOutputCreator,
                sampleFormat,
                MediaDecoder.DEFAULT_MAX_VIDEO_FRAME_SIZE,
                MediaDecoder.DEFAULT_MAX_AUDIO_FRAME_SIZE,
                MediaDecoder.DEFAULT_MAX_SUBTITLE_FRAME_SIZE
        );
    }

    @Nullable
    public Frame[] feed(@NonNull Sample sample){
        Track track = sample.getTrack();
        if(track instanceof VideoTrack)
            return videoDecoders.feed(sample);

        if(track instanceof AudioTrack)
            return audioDecoders.feed(sample);

        if(track instanceof SubtitleTrack)
            return subtitleDecoders.feed(sample);

        return null;
    }

    public boolean decode(@NonNull Frame frame){
        Track track = frame.getTrack();
        if(track instanceof VideoTrack)
            return videoDecoders.decode(frame);

        if(track instanceof AudioTrack)
            return audioDecoders.decode(frame);

        if(track instanceof SubtitleTrack)
            return subtitleDecoders.decode(frame);

        return false;
    }

    public void release(@NonNull Track track){
        if(track instanceof VideoTrack)
            videoDecoders.release(track);

        if(track instanceof AudioTrack)
            audioDecoders.release(track);

        if(track instanceof SubtitleTrack)
            subtitleDecoders.release(track);
    }

    public void releaseAll() {
        videoDecoders.releaseAll();
        audioDecoders.releaseAll();
        subtitleDecoders.releaseAll();
    }

}
