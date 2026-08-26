package com.fivesoft.qplayer.impl.extractor.rtsp;

public class RealmNonce {

    public final String realm;
    public final String nonce;

    public RealmNonce(String realm, String nonce) {
        this.realm = realm;
        this.nonce = nonce;
    }

    @Override
    public String toString() {
        return "RealmNonce{" +
                "realm='" + realm + '\'' +
                ", nonce='" + nonce + '\'' +
                '}';
    }
}
