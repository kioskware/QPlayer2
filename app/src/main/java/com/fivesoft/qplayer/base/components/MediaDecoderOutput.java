package com.fivesoft.qplayer.base.components;

import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.common.BufferReceiver;

public class MediaDecoderOutput<RendererType> {

    public static final MediaDecoderOutput<?> EMPTY = new MediaDecoderOutput<>(null, null);

    @Nullable
    private volatile RendererType renderer;
    @Nullable
    private volatile BufferReceiver bufferReceiver;

    public MediaDecoderOutput(@Nullable RendererType renderer, @Nullable BufferReceiver bufferReceiver) {
        this.renderer = renderer;
        this.bufferReceiver = bufferReceiver;
    }

    public MediaDecoderOutput(@Nullable RendererType renderer) {
        this(renderer, null);
    }

    public void setRenderer(@Nullable RendererType renderer) {
        this.renderer = renderer;
    }

    public void setBufferReceiver(@Nullable BufferReceiver bufferReceiver) {
        this.bufferReceiver = bufferReceiver;
    }

    @Nullable
    public RendererType getRenderer() {
        return renderer;
    }

    @Nullable
    public BufferReceiver getBufferReceiver() {
        return bufferReceiver;
    }

    public static abstract class Creator<RendererType>
            implements com.fivesoft.qplayer.common.resolver.Creator<Track, MediaDecoderOutput<RendererType>> {

        @Override
        public int accept(Track t) {
            return t == null ? 0 : 1;
        }
    }

}
