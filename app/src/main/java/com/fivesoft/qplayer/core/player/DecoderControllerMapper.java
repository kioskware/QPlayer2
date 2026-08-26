package com.fivesoft.qplayer.core.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Frame;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.base.components.MediaDecoderOutput;
import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.common.resolver.Resolver;

import java.util.HashMap;
import java.util.Objects;

public class DecoderControllerMapper<RendererType, DecoderType extends MediaDecoder<?, RendererType>> {

    private final HashMap<String, DecoderController<RendererType, DecoderType>>  decoders = new HashMap<>();

    private final Resolver<MediaDecoder.Descriptor, DecoderType> resolver;
    private final MediaDecoderOutput.Creator<RendererType> outputCreator;

    private final int sampleFormat;
    private final int maxFrameSize;

    public DecoderControllerMapper(@NonNull Resolver<MediaDecoder.Descriptor, DecoderType> resolver,
                                   @Nullable MediaDecoderOutput.Creator<RendererType> outputCreator,
                                   int sampleFormat, int maxFrameSize) {
        this.resolver = Objects.requireNonNull(resolver);
        this.outputCreator = outputCreator;
        this.sampleFormat = sampleFormat;
        this.maxFrameSize = maxFrameSize;
    }

    public Frame[] feed(@NonNull Sample sample) {
        return getControllerForSample(sample).feed(sample);
    }

    public boolean decode(@NonNull Frame frame) {
        return getControllerForSample(frame).decode(frame);
    }

    public void release(@NonNull Track track) {
        DecoderController<?, ?> controller;
        synchronized (decoders) {
           controller = decoders.remove(track.getId());
        }
        if (controller != null)
            controller.release();
    }

    public void releaseAll() {
        synchronized (decoders) {
            for (DecoderController<?, ?> controller : decoders.values())
                controller.release();
            decoders.clear();
        }
    }

    @NonNull
    private DecoderController<RendererType, DecoderType> getControllerForSample(@NonNull Sample sample) {
        DecoderController<RendererType, DecoderType> controller;
        synchronized (decoders) {
            controller = decoders.get(sample.getTrack().getId());
            if (controller == null) {
                MediaDecoder.Descriptor descriptor = new MediaDecoder.Descriptor(sample.getTrack(), sampleFormat, maxFrameSize);
                controller = new DecoderController<>(descriptor, resolver, outputCreator);
                decoders.put(sample.getTrack().getId(), controller);
            }
        }
        return controller;
    }

}
