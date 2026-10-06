package com.fivesoft.qplayer.impl.source;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.components.DataSource;
import com.fivesoft.qplayer.common.resolver.Creator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/** RTP with the conventional RTCP port pair, or a single port with rtcp-mux=true. */
public class RtpDataSource extends UdpDataSource {
    public static final Creator<URI, DataSource> CREATOR = new Creator<URI, DataSource>() {
        @Override
        public int accept(URI uri) {
            return UdpDataSource.CREATOR.accept(uri) > 0 && "rtp".equalsIgnoreCase(uri.getScheme()) ? 2 : 0;
        }

        @Nullable
        @Override
        public DataSource create(URI uri) {
            return accept(uri) > 0 ? new RtpDataSource(uri) : null;
        }
    };

    @Nullable
    private final UdpDataSource controlSource;

    public RtpDataSource(@NonNull URI uri) {
        super(uri);
        int controlPort = uri.getPort() + 1;
        boolean mux = false;
        if (uri.getRawQuery() != null) {
            for (String pair : uri.getRawQuery().split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length != 2) continue;
                if ("rtcp-mux".equalsIgnoreCase(parts[0])) {
                    if (!"true".equalsIgnoreCase(parts[1]) && !"false".equalsIgnoreCase(parts[1])) {
                        throw new IllegalArgumentException("rtcp-mux must be true or false");
                    }
                    mux = Boolean.parseBoolean(parts[1]);
                } else if ("rtcp-port".equalsIgnoreCase(parts[0])) {
                    controlPort = Integer.parseInt(parts[1]);
                }
            }
        }
        if (!mux && (controlPort <= 0 || controlPort > 65535 || controlPort == uri.getPort())) {
            throw new IllegalArgumentException("Invalid RTCP port; use rtcp-mux=true for a shared port");
        }
        controlSource = mux ? null : new UdpDataSource(uri.getHost(), controlPort);
    }

    public int getRtcpLocalPort() {
        return controlSource == null ? getLocalPort() : controlSource.getLocalPort();
    }

    @Override
    public void setNetworkInterface(@Nullable NetworkInterface networkInterface) {
        super.setNetworkInterface(networkInterface);
        if (controlSource != null) controlSource.setNetworkInterface(networkInterface);
    }

    @Override
    public void connect(int timeout) throws IOException {
        super.connect(timeout);
        try {
            if (controlSource != null) controlSource.connect(timeout);
        } catch (IOException | RuntimeException error) {
            close();
            throw error;
        }
    }

    @Override
    public boolean isConnected() {
        return super.isConnected() && (controlSource == null || controlSource.isConnected());
    }

    @Override
    public int readDatagram(@NonNull byte[] buffer, int offset, int length, int timeout)
            throws IOException {
        if (controlSource == null) return super.readDatagram(buffer, offset, length, timeout);
        long started = System.nanoTime();
        while (true) {
            try {
                return controlSource.readDatagram(buffer, offset, length, 1);
            } catch (SocketTimeoutException noControl) {
                // Check both sockets without background reader threads.
            }
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (timeout > 0 && elapsed >= timeout) throw new SocketTimeoutException("RTP receive timed out");
            try {
                int wait = timeout <= 0 ? 20 : (int) Math.max(1, Math.min(20, timeout - elapsed));
                return super.readDatagram(buffer, offset, length, wait);
            } catch (SocketTimeoutException noMedia) {
                // Continue until the caller's overall deadline.
            }
        }
    }

    @Override
    public boolean sendControlDatagram(@NonNull byte[] data) throws IOException {
        if (controlSource == null) return super.sendControlDatagram(data);
        InetSocketAddress destination = controlSource.getLastSenderAddress();
        if (destination == null) {
            InetSocketAddress mediaSender = getLastSenderAddress();
            if (mediaSender == null || mediaSender.getPort() == 65535) return false;
            destination = new InetSocketAddress(mediaSender.getAddress(), mediaSender.getPort() + 1);
        }
        return controlSource.sendDatagram(data, destination);
    }

    @Override
    public void close() {
        super.close();
        if (controlSource != null) controlSource.close();
    }
}
