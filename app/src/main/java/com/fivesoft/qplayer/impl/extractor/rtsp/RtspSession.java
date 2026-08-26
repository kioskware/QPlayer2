package com.fivesoft.qplayer.impl.extractor.rtsp;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Authentication;
import com.fivesoft.qplayer.common.Constants;
import com.fivesoft.qplayer.common.Credentials;
import com.fivesoft.qplayer.common.ResponseHeader;
import com.fivesoft.qplayer.common.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class RtspSession {

    private final InputStream in;
    private final OutputStream out;
    private final String userAgent;
    private final String uri;

    private final AtomicLong cSeq = new AtomicLong(0);

    private final Authentication auth;
    private String authToken;
    private String session;

    private int capabilities = 0;

    public RtspSession(@NonNull InputStream in, @NonNull OutputStream out,
                       @Nullable String userAgent, @NonNull String uri, @Nullable Authentication auth) {
        this.in = Objects.requireNonNull(in);
        this.out = Objects.requireNonNull(out);
        this.userAgent = userAgent;
        this.uri = Objects.requireNonNull(uri);
        this.auth = auth;
    }

    public int getCapabilities() {
        return capabilities;
    }

    public boolean hasCapability(int capability) {
        return (capabilities & capability) != 0;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public String getSession() {
        return session;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getAuthToken() {
        return authToken;
    }

    public RtspResponse sendKeepAlive() throws IOException {
        if (hasCapability(RtspUtil.RTSP_CAPABILITY_GET_PARAMETER)) {
            return sendCommandBasic("GET_PARAMETER", session, authToken, uri, false);
        } else {
            return sendCommandBasic("OPTIONS", session, authToken, uri, false);
        }
    }

    public RtspResponse options() throws IOException {
        return options(uri);
    }

    public RtspResponse options(@NonNull String uri) throws IOException {
        return options(uri, session);
    }

    public RtspResponse options(@NonNull String uri, @Nullable String session) throws IOException {
        return sendCommand("OPTIONS", session, uri);
    }

    public RtspResponse setup(int clientChannel, int serverChannel) throws IOException {
        return setup(uri, clientChannel, serverChannel);
    }

    public RtspResponse setup(@NonNull String uri, int clientChannel, int serverChannel) throws IOException {
        return setup(uri, clientChannel, serverChannel, session);
    }

    public RtspResponse setup(@NonNull String uri, int clientChannel, int serverChannel, @Nullable String session) throws IOException {
        return sendCommand("SETUP", session, uri,
                "Transport", "RTP/AVP/TCP;unicast;interleaved=" + clientChannel + "-" + serverChannel);
    }

    public RtspResponse setup(int clientChannel, int serverChannel, @Nullable String session) throws IOException {
        return setup(uri, clientChannel, serverChannel, session);
    }

    public RtspResponse describe() throws IOException {
        return describe(uri);
    }

    public RtspResponse describe(@NonNull String uri) throws IOException {
        return describe(uri, session);
    }

    public RtspResponse describe(@NonNull String uri, @NonNull String session) throws IOException {
        return sendCommand("DESCRIBE", session, uri,
                "Accept", "application/sdp");
    }

    public RtspResponse play(@NonNull String session) throws IOException {
        return play(uri, session);
    }

    public RtspResponse play(@NonNull String session, long startTimeMs) throws IOException {
        return play(uri, session, startTimeMs);
    }

    public RtspResponse play(@NonNull String uri, @NonNull String session) throws IOException {
        return play(uri, session, 0);
    }

    public RtspResponse play(@NonNull String uri, @NonNull String session, long startTimeMs) throws IOException {
        double startTimeSec = startTimeMs / 1000.0;
        return sendCommand("PLAY", Objects.requireNonNull(session), uri,
                "Range", "npt=" + startTimeSec + "-");
    }

    public RtspResponse pause(@NonNull String uri, @NonNull String session) throws IOException {
        return sendCommand("PAUSE", Objects.requireNonNull(session), uri);
    }

    public RtspResponse teardown(@NonNull String uri, @NonNull String session) throws IOException {
        return sendCommand("TEARDOWN", session, uri);
    }

    public RtspResponse getParameter() throws IOException {
        return getParameter(uri);
    }

    public RtspResponse getParameter(@NonNull String uri) throws IOException {
        return getParameter(uri, session);
    }

    public RtspResponse getParameter(@NonNull String uri, @Nullable String session) throws IOException {
        return sendCommand("GET_PARAMETER", session, uri);
    }

    public long getCSeq() {
        return cSeq.incrementAndGet();
    }

    private RtspResponse readResponse(@NonNull InputStream in) throws IOException {
        Util.checkInterrupted();

        ResponseHeader header = RtspUtil.readResponseHeader(in);
        int cl = RtspUtil.getHeaderContentLength(header.headers);
        byte[] content = new byte[Math.max(0, cl)];

        if(cl > 0) {
            int read = in.read(content);
            if(read != cl) {
                throw new IOException("Unexpected end of stream");
            }
        }

        session = header.headers.get("Session");

        return new RtspResponse(header, content);
    }

    private RtspResponse sendCommandBasic(@NonNull String command, @Nullable String session,
                                          @Nullable String authToken, @NonNull String uri, boolean waitForResponse,
                                          @NonNull Object... headers) throws IOException {

        Util.checkInterrupted();

        StringBuilder sb = new StringBuilder();

        out.write((command + " " + uri + " RTSP/1.0" + Constants.CRLF).getBytes());

        if(authToken != null) {
            out.write(("Authorization: " + authToken + Constants.CRLF).getBytes());
        }

        if(userAgent != null) {
            out.write(("User-Agent: " + userAgent + Constants.CRLF).getBytes());
        }

        out.write(("CSeq: " + cSeq.incrementAndGet() + Constants.CRLF).getBytes());

        if (session != null) {
            out.write(("Session: " + session + Constants.CRLF).getBytes());
        }

        for(int i = 0; i < headers.length; i += 2) {
            Util.checkInterrupted();

            String key = String.valueOf(headers[i]);
            String value = String.valueOf(headers[i + 1]);

            out.write((key + ": " + value + Constants.CRLF).getBytes());
        }

        out.write(Constants.CRLF.getBytes());
        out.flush();

        if (waitForResponse) {
            return readResponse(in);
        } else {
            return null;
        }
    }

    public RtspResponse sendCommand(@NonNull String command, @Nullable String session, @NonNull String uri,
                                    @NonNull Object... headers) throws IOException {

        //Try with current auth token
        RtspResponse res = sendCommandBasic(command, session, authToken, uri, true, headers);

        //If auth is ok, return
        if(Objects.requireNonNull(res).header.code != 401) {
            return res;
        }

        //Try with auth
        Authentication auth = this.auth;

        //If auth is null, return
        if(auth == null) {
            return res;
        }

        Credentials credentials = auth.credentials;
        RealmNonce rn = RtspUtil.getHeaderWwwAuthenticateDigestRealmAndNonce(res.header.headers);

        if(rn == null) {
            String basicRealm = RtspUtil.getHeaderWwwAuthenticateBasicRealm(res.header.headers);
            if (TextUtils.isEmpty(basicRealm)) {
                throw new IOException("Unknown authentication type");
            }
            // Basic auth
            authToken = RtspUtil.getBasicAuthHeader(credentials);
        } else {
            // Digest auth
            authToken = RtspUtil.getDigestAuthHeader(credentials, command, uri, rn);
        }

        Integer capabilities = RtspUtil.getSupportedCapabilities(res.getHeaders());

        if(capabilities != null) {
            this.capabilities = capabilities;
        }

        //Try with new auth token
        return sendCommandBasic(command, session, authToken, uri, true, headers);
    }

    public RtspResponse sendCommand(@NonNull String command, @NonNull String uri,
                                    @NonNull Object... headers) throws IOException {
        return sendCommand(command, session, uri, headers);
    }

    public void close() {
        try {
            teardown(uri, session);
        } catch (Exception e) {
            //Ignore
        }
    }
}
