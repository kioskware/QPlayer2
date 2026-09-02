package com.fivesoft.qplayer.impl.extractor.rtsp;

import androidx.annotation.Nullable;

public class RealmNonce {

    public final String realm;
    public final String nonce;
    @Nullable
    public final String algorithm;
    @Nullable
    public final String qop;
    @Nullable
    public final String opaque;

    public RealmNonce(String realm, String nonce) {
        this(realm, nonce, null, null, null);
    }

    public RealmNonce(String realm, String nonce, @Nullable String algorithm,
                      @Nullable String qop, @Nullable String opaque) {
        this.realm = realm;
        this.nonce = nonce;
        this.algorithm = algorithm;
        this.qop = qop;
        this.opaque = opaque;
    }

    @Override
    public String toString() {
        return "RealmNonce{" +
                "realm='" + realm + '\'' +
                ", nonce='" + nonce + '\'' +
                ", algorithm='" + algorithm + '\'' +
                ", qop='" + qop + '\'' +
                ", opaque='" + opaque + '\'' +
                '}';
    }
}
