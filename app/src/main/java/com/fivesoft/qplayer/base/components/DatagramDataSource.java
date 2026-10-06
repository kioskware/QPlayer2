package com.fivesoft.qplayer.base.components;

import androidx.annotation.NonNull;

import java.io.IOException;

/** A source that preserves network datagram boundaries, unlike a byte stream. */
public abstract class DatagramDataSource extends DataSource {

    public static final int MAX_DATAGRAM_SIZE = 65535;

    public final int readDatagram(@NonNull byte[] buffer, int offset, int length)
            throws IOException {
        return readDatagram(buffer, offset, length, getTimeout());
    }

    /**
     * Reads exactly one datagram. A buffer that is too small causes an IOException,
     * never silent truncation. Non-positive timeout means wait indefinitely.
     */
    public abstract int readDatagram(@NonNull byte[] buffer, int offset, int length,
                                     int timeout) throws IOException;

    /** Sends protocol feedback to the current sender, if the transport supports it. */
    public boolean sendControlDatagram(@NonNull byte[] data) throws IOException {
        return false;
    }
}
