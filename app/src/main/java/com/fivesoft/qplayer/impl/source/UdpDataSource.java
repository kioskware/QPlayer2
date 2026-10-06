package com.fivesoft.qplayer.impl.source;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.components.DataSource;
import com.fivesoft.qplayer.base.components.DatagramDataSource;
import com.fivesoft.qplayer.common.resolver.Creator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Receives unicast or multicast UDP on the local port given in the URI. */
public class UdpDataSource extends DatagramDataSource {

    public static final Creator<URI, DataSource> CREATOR = new Creator<URI, DataSource>() {
        @Override
        public int accept(URI uri) {
            return uri != null && ("rtp".equalsIgnoreCase(uri.getScheme())
                    || "udp".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null
                    && uri.getPort() > 0 && uri.getPort() <= 65535 ? 1 : 0;
        }

        @Nullable
        @Override
        public DataSource create(URI uri) {
            return accept(uri) > 0 ? new UdpDataSource(uri.getHost(), uri.getPort()) : null;
        }
    };

    private static final int INTERRUPT_POLL_MS = 250;
    private final String host;
    private final int port;
    private final Object socketLock = new Object();
    private final Object readLock = new Object();
    private final byte[] packetBuffer = new byte[MAX_DATAGRAM_SIZE];
    private volatile MulticastSocket socket;
    private volatile InputStream inputStream;
    private NetworkInterface networkInterface;
    private volatile InetSocketAddress lastSender;
    private volatile InetAddress multicastAddress;

    public UdpDataSource(@NonNull String host, int port) {
        this.host = Objects.requireNonNull(host);
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid UDP port: " + port);
        }
        this.port = port;
    }

    public UdpDataSource(@NonNull URI uri) {
        this(Objects.requireNonNull(uri.getHost()), uri.getPort());
    }

    /** Selects the interface used to join multicast groups. Set before connecting. */
    public void setNetworkInterface(@Nullable NetworkInterface networkInterface) {
        synchronized (socketLock) {
            if (isConnected()) {
                throw new IllegalStateException("Already connected");
            }
            this.networkInterface = networkInterface;
        }
    }

    public int getLocalPort() {
        MulticastSocket current = socket;
        return current == null ? -1 : current.getLocalPort();
    }

    @Override
    public long getLength() {
        return UNKNOWN_LENGTH;
    }

    @Override
    public void connect(int timeout) throws IOException {
        synchronized (socketLock) {
            if (isConnected()) {
                throw new IllegalStateException("Already connected");
            }
            InetAddress address = InetAddress.getByName(host);
            MulticastSocket opened = new MulticastSocket(null);
            try {
                opened.setReuseAddress(address.isMulticastAddress());
                opened.setReceiveBufferSize(1024 * 1024);
                opened.bind(new InetSocketAddress(port));
                opened.setSoTimeout(INTERRUPT_POLL_MS);
                if (address.isMulticastAddress()) {
                    opened.joinGroup(new InetSocketAddress(address, port), networkInterface);
                }
                socket = opened;
                lastSender = null;
                multicastAddress = address.isMulticastAddress() ? address : null;
                inputStream = new DatagramInputStream(opened);
            } catch (IOException | RuntimeException error) {
                opened.close();
                throw error;
            }
        }
    }

    @Override
    public boolean isConnected() {
        MulticastSocket current = socket;
        return current != null && current.isBound() && !current.isClosed();
    }

    @NonNull
    @Override
    public InputStream getInputStream() {
        InputStream current = inputStream;
        if (current == null || !isConnected()) {
            throw new IllegalStateException("Not connected");
        }
        return current;
    }

    @Override
    public int readDatagram(@NonNull byte[] buffer, int offset, int length, int timeout)
            throws IOException {
        MulticastSocket current = socket;
        if (current == null || current.isClosed()) {
            throw new IllegalStateException("Not connected");
        }
        return receive(current, buffer, offset, length, timeout);
    }

    private int receive(MulticastSocket current, byte[] buffer, int offset, int length,
                        int timeout) throws IOException {
        Objects.requireNonNull(buffer);
        if (offset < 0 || length < 0 || offset > buffer.length - length) {
            throw new IndexOutOfBoundsException();
        }
        synchronized (readLock) {
            long start = System.nanoTime();
            DatagramPacket packet = new DatagramPacket(packetBuffer, packetBuffer.length);
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("UDP read interrupted");
                }
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                if (timeout > 0 && elapsed >= timeout) {
                    throw new SocketTimeoutException("UDP receive timed out");
                }
                current.setSoTimeout(timeout > 0
                        ? (int) Math.max(1, Math.min(INTERRUPT_POLL_MS, timeout - elapsed))
                        : INTERRUPT_POLL_MS);
                try {
                    current.receive(packet);
                    lastSender = (InetSocketAddress) packet.getSocketAddress();
                    if (packet.getLength() > length) {
                        throw new IOException("UDP datagram exceeds the supplied buffer");
                    }
                    System.arraycopy(packetBuffer, 0, buffer, offset, packet.getLength());
                    return packet.getLength();
                } catch (SocketTimeoutException timeoutError) {
                    // Poll interruption and the overall deadline without consuming a packet.
                }
            }
        }
    }

    @Override
    public boolean isOutSupported() {
        return false;
    }

    @Nullable
    public InetSocketAddress getLastSenderAddress() {
        return lastSender;
    }

    @Nullable
    public InetSocketAddress getMulticastDestination() {
        InetAddress group = multicastAddress;
        return group == null ? null : new InetSocketAddress(group, getLocalPort());
    }

    /** Sends a datagram through the receiving socket, preserving the local port. */
    public boolean sendDatagram(@NonNull byte[] data, @Nullable InetSocketAddress destination)
            throws IOException {
        MulticastSocket current = socket;
        if (current == null || current.isClosed()) throw new IllegalStateException("Not connected");
        if (destination == null) return false;
        current.send(new DatagramPacket(data, data.length, destination));
        return true;
    }

    @Override
    public boolean sendControlDatagram(@NonNull byte[] data) throws IOException {
        InetSocketAddress group = getMulticastDestination();
        return sendDatagram(data, group == null ? lastSender : group);
    }

    @Nullable
    @Override
    public OutputStream getOutputStream() {
        return null;
    }

    @Override
    public void close() {
        synchronized (socketLock) {
            MulticastSocket current = socket;
            socket = null;
            inputStream = null;
            multicastAddress = null;
            if (current != null) {
                current.close();
            }
        }
    }

    @Override
    protected void onTimeoutSet(int timeout) {
        // Applied by receive(); polling also makes an unlimited read interruptible.
    }

    /** Byte-stream view; a bulk read never merges data from different datagrams. */
    private final class DatagramInputStream extends InputStream {
        private final MulticastSocket streamSocket;
        private final byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
        private int position;
        private int limit;

        private DatagramInputStream(MulticastSocket streamSocket) {
            this.streamSocket = streamSocket;
        }

        @Override
        public synchronized int read() throws IOException {
            byte[] single = new byte[1];
            read(single, 0, 1);
            return single[0] & 0xff;
        }

        @Override
        public synchronized int read(byte[] target, int offset, int length) throws IOException {
            Objects.requireNonNull(target);
            if (offset < 0 || length < 0 || offset > target.length - length) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) {
                return 0;
            }
            while (position == limit) {
                limit = receive(streamSocket, buffer, 0, buffer.length, getTimeout());
                position = 0;
            }
            int count = Math.min(length, limit - position);
            System.arraycopy(buffer, position, target, offset, count);
            position += count;
            return count;
        }

        @Override
        public synchronized int available() {
            return limit - position;
        }

        @Override
        public void close() {
            streamSocket.close();
        }
    }
}
