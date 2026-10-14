package com.fivesoft.qplayer.impl.source;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.components.DataSource;
import com.fivesoft.qplayer.common.Util;
import com.fivesoft.qplayer.common.X;
import com.fivesoft.qplayer.common.resolver.Creator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;

public class SocketDataSource extends DataSource {

    public static final Creator<URI, DataSource> CREATOR = new Creator<URI, DataSource>() {

        @Override
        public int accept(URI t) {
            if(t == null)
                return 0;

            //noinspection all
            String scheme = t.getScheme();
            if (scheme == null)
                return 0;
            scheme = scheme.toLowerCase(Locale.US);

            int port = t.getPort();

            if (port == -1){
                port = Util.getDefaultPort(scheme);
            }

            if (port == -1)
                return 0;

            String host = t.getHost();
            if (host == null)
                return 0;

            return switch (scheme) {
                case "http", "rtsp", "rtmp", "tcp" -> 1;
                default -> 0;
            };
        }

        @Nullable
        @Override
        public SocketDataSource create(URI t) {
            if(accept(t) > 0) {
                int port = t.getPort();
                if (port == -1){
                    port = Util.getDefaultPort(t.getScheme());
                }
                return new SocketDataSource(t.getHost(), port);
            }
            return null;
        }

    };

    private final Object sl = new Object();
    private Socket socket;

    private final SocketAddress address;

    private InputStream inputStream;
    private OutputStream outputStream;

    public SocketDataSource(@NonNull SocketAddress address) {
        this.address = Objects.requireNonNull(address, "Socket address is null");
    }

    public SocketDataSource(@NonNull String host, int port) {
        this(new InetSocketAddress(host, port));
    }

    public SocketDataSource(@NonNull Uri uri) {
        this(Objects.requireNonNull(uri.getHost()), uri.getPort());
    }

    public SocketDataSource(@NonNull String uri) {
        this(Uri.parse(uri));
    }

    @Override
    public long getLength() {
        return UNKNOWN_LENGTH;
    }

    @Override
    public void connect(int timeout) throws IOException {
        synchronized (sl) {
            if (isConnected()) {
                throw new IllegalStateException("Already connected");
            } else {
                //Create a new socket
                socket = new Socket();
                //Connect to the server
                X.l("Connecting to " + address);
                socket.connect(address, timeout);
                socket.setSoTimeout(getTimeout());
                socket.setSoLinger(false, 1);
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                X.l("Connected ");
            }
        }
    }

    @Override
    public boolean isConnected() {
        Socket socket = this.socket;
        return socket != null && socket.isConnected() && !socket.isClosed() &&
                !socket.isInputShutdown() && !socket.isOutputShutdown()
                && socket.isBound();
    }

    @NonNull
    @Override
    public InputStream getInputStream() {
        if (inputStream != null) {
            return inputStream;
        } else {
            throw new IllegalStateException("Not connected");
        }
    }

    @Override
    public boolean isOutSupported() {
        return true; //Socket supports output stream
    }

    @Nullable
    @Override
    public OutputStream getOutputStream() throws IllegalStateException {
        if (socket != null) {
            return outputStream;
        } else {
            throw new IllegalStateException("Not connected");
        }
    }

    @Override
    public void close() throws IOException {
        Socket socket = this.socket;
        if (socket != null) {
            socket.close();
        }
    }

    @Override
    protected void onTimeoutSet(int timeout) throws IOException {
        Socket socket = this.socket;
        if (socket != null) {
            socket.setSoTimeout(timeout);
        }
    }
}
