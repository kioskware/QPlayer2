package com.fivesoft.qplayer.impl.extractor.rtsp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Authentication;
import com.fivesoft.qplayer.common.Constants;
import com.fivesoft.qplayer.common.Credentials;
import com.fivesoft.qplayer.common.NetUtils;
import com.fivesoft.qplayer.common.ResponseHeader;
import com.fivesoft.qplayer.common.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class RtspSession {

    private static final int MAX_RESPONSE_CONTENT_LENGTH = 4 * 1024 * 1024;

    private final InputStream in;
    private final OutputStream out;
    private final String userAgent;
    private final String uri;

    private final AtomicLong cSeq = new AtomicLong(0);

    private final Authentication auth;
    private String authToken;
    private RealmNonce digestChallenge;
    private long digestNonceCount;
    private String session;

    private int capabilities = 0;

    public RtspSession(@NonNull InputStream in, @NonNull OutputStream out,
                       @Nullable String userAgent, @NonNull String uri, @Nullable Authentication auth) {
        this.in = Objects.requireNonNull(in);
        this.out = Objects.requireNonNull(out);
        this.userAgent = userAgent;
        this.uri = Objects.requireNonNull(uri);
        this.auth = auth;
        if (auth != null && auth.getToken() != null) {
            this.authToken = new String(auth.getToken(), StandardCharsets.ISO_8859_1);
        }
    }

    public int getCapabilities() {
        return capabilities;
    }

    public boolean hasCapability(int capability) {
        return (capabilities & capability) != 0;
    }

    public void setSession(String session) {
        this.session = RtspUtil.getSessionId(session);
    }

    public String getSession() {
        return session;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
        this.digestChallenge = null;
        this.digestNonceCount = 0;
    }

    public String getAuthToken() {
        return authToken;
    }

    public RtspResponse sendKeepAlive() throws IOException {
        String command = hasCapability(RtspUtil.RTSP_CAPABILITY_GET_PARAMETER)
                ? "GET_PARAMETER"
                : "OPTIONS";
        return sendCommandBasic(
                command,
                session,
                createAuthToken(command, uri),
                uri,
                false
        );
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
        int cl = RtspUtil.getHeaderContentLength(header.getHeaders());
        if (cl > MAX_RESPONSE_CONTENT_LENGTH) {
            throw new IOException("RTSP response is too large: " + cl + " bytes");
        }
        byte[] content = new byte[Math.max(0, cl)];

        if(cl > 0) {
            int read = NetUtils.readData(in, content, 0, cl);
            if(read != cl) {
                throw new IOException("Unexpected end of stream");
            }
        }

        String responseSession = RtspUtil.getSessionId(header.getHeaders().get("Session"));
        if (responseSession != null) {
            session = responseSession;
        }

        updateCapabilities(header);

        return new RtspResponse(header, content);
    }

    private RtspResponse sendCommandBasic(@NonNull String command, @Nullable String session,
                                          @Nullable String authToken, @NonNull String uri, boolean waitForResponse,
                                          @NonNull Object... headers) throws IOException {

        Util.checkInterrupted();

        out.write((command + " " + uri + " RTSP/1.0" + Constants.CRLF)
                .getBytes(StandardCharsets.ISO_8859_1));

        if(authToken != null) {
            out.write(("Authorization: " + authToken + Constants.CRLF)
                    .getBytes(StandardCharsets.ISO_8859_1));
        }

        if(userAgent != null) {
            out.write(("User-Agent: " + userAgent + Constants.CRLF)
                    .getBytes(StandardCharsets.ISO_8859_1));
        }

        out.write(("CSeq: " + cSeq.incrementAndGet() + Constants.CRLF)
                .getBytes(StandardCharsets.ISO_8859_1));

        String sessionId = RtspUtil.getSessionId(session);
        if (sessionId != null) {
            out.write(("Session: " + sessionId + Constants.CRLF)
                    .getBytes(StandardCharsets.ISO_8859_1));
        }

        if (headers.length % 2 != 0) {
            throw new IllegalArgumentException("RTSP headers must be key/value pairs");
        }
        for(int i = 0; i < headers.length; i += 2) {
            Util.checkInterrupted();

            String key = String.valueOf(headers[i]);
            String value = String.valueOf(headers[i + 1]);

            out.write((key + ": " + value + Constants.CRLF)
                    .getBytes(StandardCharsets.ISO_8859_1));
        }

        out.write(Constants.CRLF.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();

        if (waitForResponse) {
            return readResponse(in);
        } else {
            return null;
        }
    }

    public RtspResponse sendCommand(@NonNull String command, @Nullable String session, @NonNull String uri,
                                    @NonNull Object... headers) throws IOException {

        RtspResponse res = sendCommandBasic(
                command,
                session,
                createAuthToken(command, uri),
                uri,
                true,
                headers
        );

        if(Objects.requireNonNull(res).header.getCode() != 401) {
            return res;
        }

        Authentication auth = this.auth;
        if(auth == null) {
            return res;
        }

        Credentials credentials = auth.getCredentials();
        RealmNonce rn = RtspUtil.getHeaderWwwAuthenticateDigestRealmAndNonce(res.header.getHeaders());

        if(rn == null) {
            String basicRealm = RtspUtil.getHeaderWwwAuthenticateBasicRealm(res.header.getHeaders());
            if (basicRealm == null) {
                throw new IOException("Unknown authentication type");
            }
            digestChallenge = null;
            digestNonceCount = 0;
            authToken = RtspUtil.getBasicAuthHeader(credentials);
        } else {
            digestChallenge = rn;
            digestNonceCount = 0;
            authToken = createAuthToken(command, uri);
        }

        return sendCommandBasic(command, session, authToken, uri, true, headers);
    }

    @Nullable
    private String createAuthToken(@NonNull String command, @NonNull String requestUri) throws IOException {
        RealmNonce challenge = digestChallenge;
        if (challenge == null) {
            return authToken;
        }
        Credentials credentials = auth == null ? null : auth.getCredentials();
        String token = RtspUtil.getDigestAuthHeader(
                credentials,
                command,
                requestUri,
                challenge,
                ++digestNonceCount
        );
        if (token == null) {
            throw new IOException("Unsupported RTSP Digest authentication challenge");
        }
        authToken = token;
        return token;
    }

    private void updateCapabilities(@NonNull ResponseHeader header) {
        Integer responseCapabilities = RtspUtil.getSupportedCapabilities(header.getHeaders());
        if (responseCapabilities != null) {
            capabilities |= responseCapabilities;
        }
    }

    public RtspResponse sendCommand(@NonNull String command, @NonNull String uri,
                                    @NonNull Object... headers) throws IOException {
        return sendCommand(command, session, uri, headers);
    }

    public void close() {
        if (session == null) {
            return;
        }
        try {
            teardown(uri, session);
        } catch (Exception e) {
            //Ignore
        }
    }

}
