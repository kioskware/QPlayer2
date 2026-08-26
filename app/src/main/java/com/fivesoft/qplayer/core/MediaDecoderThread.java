package com.fivesoft.qplayer.core;

import androidx.annotation.NonNull;

import com.fivesoft.qplayer.base.Frame;
import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.common.measure.CountMeasure;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public abstract class MediaDecoderThread extends Thread {

    private final BlockingQueue<Frame> frameQueue = new ArrayBlockingQueue<>(10);
    private final MediaDecoder<?, ?> decoder;

    private final CountMeasure droppedFramesMeasure = new CountMeasure(100, 1000);

    public MediaDecoderThread(@NonNull MediaDecoder<?, ?> decoder) {
        this.decoder = Objects.requireNonNull(decoder);
    }

    @Override
    public void run() {

        Frame cFrame;

        while (!isInterrupted()) {
            try {
                cFrame = frameQueue.take();
                decoder.decode(cFrame);
            } catch (InterruptedException e) {
                onThreadInterrupted(decoder);
                break;
            } catch (Exception e) {
                onThreadException(decoder, e);
                return;
            }
        }

    }

    public boolean decode(@NonNull Frame frame) {
        return frameQueue.offer(frame);
    }

    public abstract void onThreadInterrupted(@NonNull MediaDecoder<?, ?> decoder);

    public abstract void onThreadException(@NonNull MediaDecoder<?, ?> decoder, @NonNull Exception e);

}
