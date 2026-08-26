package com.fivesoft.qplayer.impl.source;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.components.DataSource;
import com.fivesoft.qplayer.common.Constants;
import com.fivesoft.qplayer.common.X;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.util.Objects;

public class MulticastSocketDataSource extends DataSource {

    private final Object sl = new Object();
    private MulticastSocket socket;
    private final SocketAddress address;

    @Override
    public long getLength() {
        return Constants.UNKNOWN_VALUE;
    }

    public MulticastSocketDataSource(@NonNull SocketAddress address) {
        this.address = Objects.requireNonNull(address, "Socket address is null");
    }

    public MulticastSocketDataSource(@NonNull String host, int port) {
        this(new InetSocketAddress(host, port));
    }

    public MulticastSocketDataSource(@NonNull Uri uri) {
        this(Objects.requireNonNull(uri.getHost()), uri.getPort());
    }

    @Override
    public void connect(int timeout) throws IOException {
        synchronized (sl) {
            if (isConnected()) {
                throw new IllegalStateException("Already connected");
            } else {
                //Create a new socket
                socket = new MulticastSocket(address);
                //Connect to the server
                socket.setSoTimeout(getTimeout());
                X.l("Connected ");
            }
        }
    }

    @Override
    public boolean isConnected() {
        MulticastSocket socket = this.socket;
        return socket != null && socket.isConnected() && !socket.isClosed()
                && socket.isBound();
    }

    @NonNull
    @Override
    public InputStream getInputStream() throws IOException {
        return null;
    }

    @Override
    public boolean isOutSupported() {
        return true;
    }

    @Nullable
    @Override
    public OutputStream getOutputStream() throws IllegalStateException, IOException {
        return null;
    }

    @Override
    public void close() throws IOException {
        MulticastSocket socket = this.socket;
        if (socket != null) {
            socket.close();
        }
    }

    @Override
    protected void onTimeoutSet(int timeout) throws IOException {
        MulticastSocket socket = this.socket;
        if (socket != null) {
            socket.setSoTimeout(timeout);
        }
    }

}
