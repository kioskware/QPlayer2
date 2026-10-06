package com.fivesoft.qplayer.impl.source;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;

/** Backwards-compatible multicast source backed by the complete UDP implementation. */
public class MulticastSocketDataSource extends UdpDataSource {
    public MulticastSocketDataSource(@NonNull SocketAddress address) {
        this(asInternetAddress(address));
    }

    private MulticastSocketDataSource(InetSocketAddress address) {
        super(address.getHostString(), address.getPort());
    }

    public MulticastSocketDataSource(@NonNull String host, int port) {
        super(host, port);
    }

    public MulticastSocketDataSource(@NonNull Uri uri) {
        this(Objects.requireNonNull(uri.getHost()), uri.getPort());
    }

    private static InetSocketAddress asInternetAddress(SocketAddress address) {
        if (!(Objects.requireNonNull(address) instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("An Internet socket address is required");
        }
        return (InetSocketAddress) address;
    }
}
