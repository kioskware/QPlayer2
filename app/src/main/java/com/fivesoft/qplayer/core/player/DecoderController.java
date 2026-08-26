package com.fivesoft.qplayer.core.player;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Frame;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.base.components.MediaDecoderOutput;
import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.common.resolver.Resolver;
import com.fivesoft.qplayer.core.MediaDecoderThread;

import java.util.Objects;

public class DecoderController<RendererType, DecoderType extends MediaDecoder<?, RendererType>> {

    private final Object decoderLock = new Object();

    private final MediaDecoder.Descriptor descriptor;
    private final MediaDecoderOutput.Creator<RendererType> outputCreator;

    private final Resolver<MediaDecoder.Descriptor, DecoderType> decoderResolver;

    private volatile DecoderType decoder;
    private volatile MediaDecoderThread decoderThread;

    /**
     * Creates a new DecoderController.
     *
     * @param descriptor      The descriptor. (description of the decoder used to resolve it)
     * @param decoderResolver The decoder resolver. (for resolving the decoder from the descriptor)
     * @param outputCreator   The output creator. (for creating the output for the decoder)
     */

    public DecoderController(@NonNull MediaDecoder.Descriptor descriptor,
                             @NonNull Resolver<MediaDecoder.Descriptor, DecoderType> decoderResolver,
                             @Nullable MediaDecoderOutput.Creator<RendererType> outputCreator) {
        this.descriptor = Objects.requireNonNull(descriptor);
        this.outputCreator = outputCreator;
        this.decoderResolver = Objects.requireNonNull(decoderResolver);
    }

    /**
     * Feed the given sample to the decoder. This is equivalent to calling {@link MediaDecoder#feed(Sample)}.<br>
     * @param sample The sample to feed.
     * @return The frame decoded from the sample or null if frame is not available yet or if the sample is not supported.
     */
    @Nullable
    public Frame[] feed(@NonNull Sample sample) {
        synchronized (decoderLock) {
            ensureDecoder(sample.getTrack());
            if (decoder == null || decoder.isReleased())
                return null;
            try {
                return decoder.feed(sample);
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Decodes the given frame. This is equivalent to calling {@link MediaDecoder#decode(Frame)}.
     * @param frame The frame to decode.
     * @return Whether the frame was decoded successfully.
     */
    public boolean decode(@NonNull Frame frame) {
        synchronized (decoderLock) {
            ensureDecoder(frame.getTrack());
            if (decoderThread == null) {
                return false;
            }
            return decoderThread.decode(frame);
        }
    }

    /**
     * Releases the decoder and the decoder thread.
     */
    public void release() {
        synchronized (decoderLock) {
            if (decoder != null) {
                releaseDecoderSilently(decoder);
            }
            if (decoderThread != null) {
                decoderThread.interrupt();
                decoderThread = null;
            }
            decoder = null;
        }
    }

    private void ensureDecoder(@NonNull Track track) {
        synchronized (decoderLock) {
            if (decoder == null || decoder.isReleased()) {
                decoder = decoderResolver.resolve(descriptor);

                if (decoderThread != null && decoderThread.isAlive()) {
                    decoderThread.interrupt();
                    decoderThread = null;
                }

                if (decoder == null)
                    return;

                if (outputCreator != null) {
                    MediaDecoderOutput<RendererType> output = outputCreator.create(track);
                    decoder.setOutput(output);
                }

                decoderThread = new MediaDecoderThread(decoder) {
                    @Override
                    public void onThreadInterrupted(@NonNull MediaDecoder<?, ?> decoder) {
                        DecoderController.this.decoder = null;
                        decoderThread = null;
                        releaseDecoderSilently(decoder);
                        Log.println(Log.ERROR, "DecoderController", "Decoder thread interrupted");
                    }

                    @Override
                    public void onThreadException(@NonNull MediaDecoder<?, ?> decoder, @NonNull Exception e) {
                        DecoderController.this.decoder = null;
                        decoderThread = null;
                        releaseDecoderSilently(decoder);
                        e.printStackTrace();
                        Log.println(Log.ERROR, "DecoderController", "Decoder thread exception: " + e.getMessage());
                    }
                };
                decoderThread.start();
            }
        }
    }

    private static void releaseDecoderSilently(@NonNull MediaDecoder<?, ?> decoder) {
        try {
            decoder.release();
        } catch (Exception ignored) {
        }
    }

}
