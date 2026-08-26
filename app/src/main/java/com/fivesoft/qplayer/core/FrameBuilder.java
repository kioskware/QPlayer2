package com.fivesoft.qplayer.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Frame;
import com.fivesoft.qplayer.base.Sample;

import java.util.Objects;

/**
 * Frame builder is a base class for building frames from multiple data samples.<br>
 * Samples should be collected in a buffer, till a frame is ready to be built.<br>
 * <p>
 *     Samples are provided via {@link #pull(Sample)}. If a frame is ready, it will be returned, otherwise null.<br>
 *     Current size of the buffer (total length of all samples) can be obtained via {@link #getBufferSize()}.<br>
 * </p>
 * <p>
 *     Buffer can be cleared via {@link #clear()} method.
 * </p>
 *
 */

public abstract class FrameBuilder {

    public static final Frame[] SINGLE_FRAME_ARRAY = new Frame[1];

    /**
     * Default maximum frame size in bytes. The value is 2MB.<br>
     * Enough for most compressed video formats.
     */

    public static final int DEFAULT_MAX_FRAME_SIZE = 1024 * 1024 * 2; // 2MB

    protected final int maxFrameSize;

    /**
     * Creates Frame builder with specified max frame size.<br>
     * @param maxFrameSize If a frame is bigger than this value, {@link BufferOverflowException}
     * will be thrown by {@link #pull(Sample sampe)} method.<br>
     */

    public FrameBuilder(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
    }

    /**
     * Creates Frame builder with default max frame size ({@link #DEFAULT_MAX_FRAME_SIZE}).<br>
     * @see #FrameBuilder(int)
     */

    public FrameBuilder() {
        this(DEFAULT_MAX_FRAME_SIZE);
    }

    /**
     * Returns maximum frame size in bytes. The value is set in constructor and can't be changed later.
     * @return maximum frame size in bytes (see {@link #FrameBuilder(int)} constructor)
     */

    public final int getMaxFrameSize(){
        return maxFrameSize;
    }

    /**
     * Pulls a sample from the buffer.<br>
     * @param sample a sample to pull
     * @return a frame if it is ready, otherwise null
     * @throws BufferOverflowException if a frame is bigger than {@link #getMaxFrameSize()} value
     */

    @Nullable
    public abstract Frame[] pull(@NonNull Sample sample) throws BufferOverflowException;

    /**
     * Returns current size of the buffer (total length of all samples).
     * @return current size of the buffer
     */

    public abstract int getBufferSize();

    /**
     * Clears the buffer. All samples will be discarded.
     */

    public abstract void clear();

    /**
     * Thrown when buffer is full and a new sample is being pulled via {@link #pull(Sample)}.
     */
    public static class BufferOverflowException extends RuntimeException {

        /**
         * Constructs a new {@code BufferOverflowException} that includes the current stack trace.
         */

        public BufferOverflowException() {
            super("Buffer overflow");
        }
    }

    /**
     * Raw frame builder. It doesn't buffer samples, but returns
     * a frame for each sample with sync flag set to {@link Frame#UNKNOWN_FRAME}.
     */

    public static final FrameBuilder RAW_FRAME_BUILDER = new FrameBuilder(Integer.MAX_VALUE) {

        private final Frame[] frame = new Frame[1];

        @NonNull
        @Override
        public Frame[] pull(@NonNull Sample sample) throws BufferOverflowException {
            Objects.requireNonNull(sample);

            if (sample instanceof Frame) {
                frame[0] = (Frame) sample;
            } else {
                frame[0] = new Frame(sample.getArray(), sample.getOffset(), sample.getLength(),
                        sample.getTimestamp(), sample.getTrack(), Frame.UNKNOWN_FRAME);
            }
            return frame;
        }

        @Override
        public int getBufferSize() {
            return 0;
        }

        @Override
        public void clear() {
            // do nothing
        }
    };

}
