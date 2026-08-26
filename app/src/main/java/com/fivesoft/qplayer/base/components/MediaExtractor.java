package com.fivesoft.qplayer.base.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Authentication;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.track.Tracks;
import com.fivesoft.qplayer.common.TimeoutException;
import com.fivesoft.qplayer.common.Timeoutable;
import com.fivesoft.qplayer.common.TrackSelector;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/**
 * MediaExtractors are used to extract media samples from a {@link DataSource}.
 * For instance it may be extracting H264 nal units from a mp4 file.<br>
 * <p>
 *     Each extractor is associated with a {@link DataSource} and can only be used with that
 *     {@link DataSource}.<br>
 * </p>
 * <p>
 *     Extractors don't need to be thread safe and they are not responsible for connecting/closing DataSource.
 * </p>
 */

public abstract class MediaExtractor
        implements AutoCloseable, Timeoutable {

    protected final DataSource dataSource;
    private int timeout = 15000;
    private boolean allowReuseBuffers = false;

    @NonNull
    public final TrackSelector trackSelector;

    /**
     * Creates a new extractor for the specified data source.<br>
     * @param dataSource The data source to extract samples from. May be not connected while creating the extractor.
     * @param trackSelector The track selector to select tracks to extract. If null, all tracks will be selected.
     * @throws NullPointerException if the data source is null.
     */

    public MediaExtractor(@NonNull DataSource dataSource, @Nullable TrackSelector trackSelector){
        this.dataSource = Objects.requireNonNull(dataSource);
        this.trackSelector = trackSelector == null ? TrackSelector.ALL : trackSelector;
    }

    /**
     * Creates a new extractor for the specified data source which extracts all tracks.<br>
     * @param dataSource The data source to extract samples from.
     * @throws NullPointerException if the data source is null.
     */

    public MediaExtractor(@NonNull DataSource dataSource){
        this(dataSource, null);
    }

    /**
     * Prepares the extractor for reading samples.<br>
     * @throws IOException if any I/O error occurs while preparing.
     * @throws TimeoutException if the timeout is reached while preparing.
     * @throws InterruptedException if the thread is interrupted while preparing.
     */

    public abstract void prepare(int timeout) throws IOException, TimeoutException, InterruptedException;

    /**
     * Checks if the extractor is prepared.<br>
     * @return true if the extractor is prepared, false otherwise.
     * @see #prepare(int)
     */
    public abstract boolean isPrepared();

    /**
     * Reads next sample from the data source.<br>
     * @return The next sample or null if the end of the data source is reached.
     * @throws IOException if any I/O error occurs while reading the sample.
     * @throws TimeoutException if the timeout is reached while reading the sample.
     * @throws IllegalStateException If the extractor is not prepared yet or it is closed.
     * @throws InterruptedException if the thread is interrupted while reading the sample.
     */
    public abstract Sample nextSample() throws IOException, TimeoutException,
            IllegalStateException, InterruptedException;

    /**
     * Returns tracks collection available in the data source.<br>
     * This should return the same reference for whole lifetime of the extractor.<br>
     * @return Tracks collection of the data source.
     * @throws IllegalStateException If the extractor is not prepared yet or it is closed.
     * @throws RuntimeException if other error occurs.
     */

    @NonNull
    public abstract Tracks getTracks() throws IllegalStateException, RuntimeException;

    /**
     * Gets the current position in milliseconds.<br>
     * @return The current position in milliseconds or -1 if the
     * position is unknown.
     */

    public abstract long getPosition();

    /**
     * Gets the current sample index.<br>
     * @return The current sample index or -1 if the index is unknown.
     */

    public abstract long getSampleIndex();

    /**
     * Gets the sample format of the extractor.<br>
     * This may be one of:
     * <ul>
     *     <li>{@link MediaDecoder#FORMAT_RAW}</li>
     *     <li>{@link MediaDecoder#FORMAT_ANNEX_B}</li>
     *     <li>{@link MediaDecoder#FORMAT_AVCC}</li>
     * </ul>
     * @return The sample format of the extractor.
     */

    public abstract int getSampleFormat();

    /**
     * Releases all resources associated with this extractor.<br>
     * This does not close the data source associated with this extractor.<br>
     * @throws IOException if any I/O error occurs while closing the extractor.
     */

    @Override
    public abstract void close() throws IOException;

    /**
     * Sets the authentication for the data source.<br>
     * Used for data sources that require authentication. (e.g. HTTP Basic Authentication)<br>
     * @param authentication The authentication for the data source or null
     *                       if no authentication is needed.
     */

    public abstract void setAuthentication(@Nullable Authentication authentication);

    /**
     * Sets the timeout value for I/O operations.<br>
     * @param timeout The timeout value in milliseconds. Non-positive value means no timeout. (waiting forever)
     */

    @Override
    public void setTimeout(int timeout) {
        if(this.timeout == timeout)
            return;

        this.timeout = timeout;
        try {
            onTimeoutSet(Math.max(timeout, 0));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the timeout value for I/O operations. (set by {@link #setTimeout(int)})<br>
     * @return The timeout value in milliseconds. Non-positive value means no timeout. (waiting forever)
     */

    @Override
    public int getTimeout() {
        return timeout;
    }

    /**
     * Gets data source associated with this extractor.<br>
     * @return The data source associated with this extractor.
     */

    @NonNull
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Sets whether the extractor is allowed to reuse buffers.<br>
     * Reusing buffers may improve performance but it may also cause problems if the buffers are not
     * used properly.<br>Before enabling this, make sure that sample you get from {@link #nextSample()},
     * is consumed or copied before calling {@link #nextSample()} again.<br>
     * @param allowReuseBuffers true to allow the extractor to reuse buffers, false otherwise.
     */

    public void setAllowReuseBuffers(boolean allowReuseBuffers) {
        this.allowReuseBuffers = allowReuseBuffers;
    }

    /**
     * Checks if the extractor is allowed to reuse buffers.<br>
     * @return true if the extractor is allowed to reuse buffers, false otherwise.
     */

    public boolean isReuseBuffersAllowed() {
        return allowReuseBuffers;
    }

    /**
     * Called when timeout is set by {@link #setTimeout(int)}.<br>
     * This is called after the timeout is set and before the timeout is applied to the data source, so
     * it will be: <code>getTimeout() == timeout</code><br>
     * The implementation should apply the timeout to the media extractor.
     * @param timeout The timeout in milliseconds or 0 if no timeout. (waiting forever)
     *                API will care to provide only non-negative values.
     * @throws IOException If an I/O error occurs while setting the timeout.
     */

    protected abstract void onTimeoutSet(int timeout) throws IOException;

    public static class Descriptor {

        public final DataSource dataSource;
        public final TrackSelector trackSelector;
        public final URI uri;

        public Descriptor(DataSource dataSource, TrackSelector trackSelector, URI uri){
            this.dataSource = dataSource;
            this.trackSelector = trackSelector;
            this.uri = uri;
        }

    }

}
