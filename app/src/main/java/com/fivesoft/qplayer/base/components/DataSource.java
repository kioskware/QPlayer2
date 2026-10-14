package com.fivesoft.qplayer.base.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.common.Constants;
import com.fivesoft.qplayer.common.Timeoutable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Represents a source of data which can be read and controlled; it can be either a file or any data stream.<br>
 * <p>
 *     To read or control the source you have to establish connection before. To do that, use {@link #connect()} method.<br>
 *     After successful connection, the source is ready to be used.<br>
 * </p>
 * <p>
 *     To read data from the source, use {@link #getInputStream()} method.<br>This will return non-null
 *     {@link InputStream} object which can be used to read data from the source.<br>
 * </p>
 * <p>
 *     Some types of data sources can be controlled. To control the source, use {@link #getOutputStream()} method.<br>
 *     Example of a controlled source can be a TCP socket. In this case, the {@link OutputStream} can be used to send
 *     commands to the server.<br>
 * </p>
 * <p>
 *     If a media source does not support output stream, {@link #getOutputStream()} will return null.<br>
 *     The example of such source is a file data source.
 * </p>
 * <p>
 *     After finishing using the source, you should close it by calling {@link #close()} method or by using try-with-resources statement.<br>
 * </p>
 */
public abstract class DataSource implements AutoCloseable, Timeoutable {

    /**
     * Default connection timeout in milliseconds. Used by {@link #connect()}.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT = 15000;

    /**
     * Default I/O timeout in milliseconds. Used by {@link #getInputStream()} and {@link #getOutputStream()}.
     */
    public static final int DEFAULT_IO_TIMEOUT = 10000;

    /**
     * Indicates that the length of the data source is unknown.
     */
    public static final long UNKNOWN_LENGTH = Constants.UNKNOWN_VALUE;

    /**
     * Returns total length of the data source in bytes.
     * @return The length of the data source in bytes or {@link #UNKNOWN_LENGTH}
     * if the length is unknown (most common for live streams).
     */
    public abstract long getLength();

    /**
     * Establishes connection to the data source with given timeout.<br>
     * After successful connection, the data source is ready to be used.<br>
     * {@link #getInputStream()} and {@link #getOutputStream()} won't throw {@link IllegalStateException} till closing.
     * @param timeout The timeout in milliseconds. Non-positive value means no timeout. (waiting forever)
     * @throws IOException If an I/O error occurs while connecting to the data source.
     * @throws IllegalStateException If the data source is already connected.
     */
    public abstract void connect(int timeout) throws IOException;

    /**
     * Establishes connection to the data source with default timeout.<br>
     * After successful connection, the data source is ready to be used.<br>
     * {@link #getInputStream()} and {@link #getOutputStream()} won't throw {@link IllegalStateException} till closing.
     * @throws IOException If an I/O error occurs while connecting to the data source.
     * @throws IllegalStateException If the data source is already connected.
     * @see #connect(int)
     */
    public final void connect() throws IOException {
        connect(DEFAULT_CONNECT_TIMEOUT);
    }

    /**
     * Checks if the data source is connected and ready to use.<br>
     * @return True if the data source is connected.
     */
    public abstract boolean isConnected();

    /**
     * Returns input stream for reading data from the data source.<br>
     * This method returns same stream instance for each call, till the data source is closed.<br>
     * @return The input stream.
     * @throws IllegalStateException If the data source is not connected.
     * @throws IOException If an I/O error occurs while creating or getting the input stream.
     */
    @NonNull
    public abstract InputStream getInputStream() throws IOException;

    /**
     * Returns true if the data source supports output stream.<br>
     * @return True if the data source supports {@link #getOutputStream()}. (the method will return non-null)
     */
    public abstract boolean isOutSupported();

    /**
     * Returns output stream for controlling the data source.<br>
     * This method returns same stream instance for each call, till the data source is closed.<br>
     * This may not be supported by all data sources for ex. file data source.
     * @return The output stream or null if not supported.
     * @throws IllegalStateException If the data source supports output stream, but it is not connected.
     * @throws IOException If an I/O error occurs while creating or getting the output stream.
     */
    @Nullable
    public abstract OutputStream getOutputStream() throws IllegalStateException, IOException;

    /**
     * Closes the data source and releases any system resources associated with it.<br>
     * All pending read/write operations will be aborted and all streams will be closed automatically.<br>
     * After closing, {@link #getInputStream()} and {@link #getOutputStream()} will throw {@link IllegalStateException}.<br>
     * Media source can be reopened by calling {@link #connect()} again.
     * @throws IOException If an I/O error occurs while closing the data source.
     */
    @Override
    public abstract void close() throws IOException;

    /**
     * Closes the data source and opens it again.<br>
     * All pending read/write operations will be aborted and all streams will be closed automatically, but opened again.<br>
     * @throws IOException If an I/O error occurs while closing or opening the data source.
     */
    public void reconnect() throws IOException {
        close();
        connect();
    }

    //Timeoutable implementation
    private volatile int timeout = DEFAULT_IO_TIMEOUT;

    /**
     * Returns the timeout in milliseconds which is currently set for the data source streams.<br>
     * The timeout may be not applied yet, but it will be applied before the next read/write operation.
     * @return The timeout in milliseconds or non-positive value if no timeout. (waiting forever)
     */
    @Override
    public final int getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout in milliseconds for the data source streams.<br>
     * @param timeout The timeout value in milliseconds. Non-positive value means no timeout. (waiting forever)
     * @throws RuntimeException If an I/O error occurs while setting the timeout.
     */
    @Override
    public final void setTimeout(int timeout) throws RuntimeException {
        if(timeout == this.timeout)
            return;
        this.timeout = timeout;
        try {
            onTimeoutSet(Math.max(timeout, 0));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Called when timeout is set by {@link #setTimeout(int)}.<br>
     * This is called after the timeout is set and before the timeout is applied to the data source, so
     * it will be: <code>getTimeout() == timeout</code><br>
     * The implementation should apply the timeout to the data source.
     * @param timeout The timeout in milliseconds or 0 if no timeout. (waiting forever)
     *                API will care to provide only non-negative values.
     * @throws IOException If an I/O error occurs while setting the timeout.
     */
    protected abstract void onTimeoutSet(int timeout) throws IOException;

}
