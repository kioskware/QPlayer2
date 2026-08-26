package com.fivesoft.qplayer.impl.extractor.rtsp;

import static com.fivesoft.qplayer.common.NetUtils.readLine;
import static com.fivesoft.qplayer.common.Util.checkInterrupted;
import static com.fivesoft.qplayer.common.Util.getHexStringFromBytes;
import static com.fivesoft.qplayer.common.Util.readUntilBytesFound;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.common.Credentials;
import com.fivesoft.qplayer.common.Header;
import com.fivesoft.qplayer.common.Headers;
import com.fivesoft.qplayer.common.ResponseHeader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class RtspUtil {

    public final static int RTSP_CAPABILITY_NONE = 0;
    public final static int RTSP_CAPABILITY_OPTIONS = 1 << 1;
    public final static int RTSP_CAPABILITY_DESCRIBE = 1 << 2;
    public final static int RTSP_CAPABILITY_ANNOUNCE = 1 << 3;
    public final static int RTSP_CAPABILITY_SETUP = 1 << 4;
    public final static int RTSP_CAPABILITY_PLAY = 1 << 5;
    public final static int RTSP_CAPABILITY_RECORD = 1 << 6;
    public final static int RTSP_CAPABILITY_PAUSE = 1 << 7;
    public final static int RTSP_CAPABILITY_TEARDOWN = 1 << 8;
    public final static int RTSP_CAPABILITY_SET_PARAMETER = 1 << 9;
    public final static int RTSP_CAPABILITY_GET_PARAMETER = 1 << 10;
    public final static int RTSP_CAPABILITY_REDIRECT = 1 << 11;

    private static final boolean DEBUG = false;
    private static final String CRLF = "\r\n";
    private static final String TAG = RtspUtil.class.getSimpleName();

    public static void sendSimpleCommand(@NonNull String command, @NonNull OutputStream outputStream,
                                          @NonNull String request, long cSeq, @Nullable String userAgent,
                                          @Nullable String session, @Nullable String authToken) throws IOException {
        outputStream.write((command + " " + request + " RTSP/1.0" + CRLF).getBytes());
        if (authToken != null) {
            outputStream.write(("Authorization: " + authToken + CRLF).getBytes());
        }
        outputStream.write(("CSeq: " + cSeq + CRLF).getBytes());
        if (userAgent != null) {
            outputStream.write(("User-Agent: " + userAgent + CRLF).getBytes());
        }
        if (session != null) {
            outputStream.write(("Session: " + session + CRLF).getBytes());
        }
        outputStream.write(CRLF.getBytes());
        outputStream.flush();
    }

    public static void sendOptionsCommand(@NonNull OutputStream outputStream, @NonNull String request, long cSeq,
                                           @Nullable String userAgent, @Nullable String authToken) throws IOException {
        if (DEBUG) {
            Log.v(TAG, "sendOptionsCommand(request=\"" + request + "\", cSeq=" + cSeq + ")");
        }
        sendSimpleCommand("OPTIONS", outputStream, request, cSeq, userAgent, null, authToken);
    }

    public static void sendGetParameterCommand(@NonNull OutputStream outputStream, @NonNull String request, long cSeq,
                                                @Nullable String userAgent, @Nullable String session, @Nullable String authToken) throws IOException {
        if (DEBUG) {
            Log.v(TAG, "sendGetParameterCommand(request=\"" + request + "\", cSeq=" + cSeq + ")");
        }
        sendSimpleCommand("GET_PARAMETER", outputStream, request, cSeq, userAgent, session, authToken);
    }

    public static void sendDescribeCommand(@NonNull OutputStream outputStream, @NonNull String request, long cSeq,
                                            @Nullable String userAgent, @Nullable String authToken) throws IOException {
        if (DEBUG) {
            Log.v(TAG, "sendDescribeCommand(request=\"" + request + "\", cSeq=" + cSeq + ")");
        }
        outputStream.write(("DESCRIBE " + request + " RTSP/1.0" + CRLF).getBytes());
        outputStream.write(("Accept: application/sdp" + CRLF).getBytes());
        if (authToken != null) {
            outputStream.write(("Authorization: " + authToken + CRLF).getBytes());
        }
        outputStream.write(("CSeq: " + cSeq + CRLF).getBytes());
        if (userAgent != null) {
            outputStream.write(("User-Agent: " + userAgent + CRLF).getBytes());
        }
        outputStream.write(CRLF.getBytes());
        outputStream.flush();
    }

    public static void sendTeardownCommand(@NonNull OutputStream outputStream, @NonNull String request, long cSeq,
                                            @Nullable String userAgent, @Nullable String authToken, @Nullable String session) throws IOException {
        if (DEBUG) {
            Log.v(TAG, "sendTeardownCommand(request=\"" + request + "\", cSeq=" + cSeq + ")");
        }
        outputStream.write(("TEARDOWN " + request + " RTSP/1.0" + CRLF).getBytes());
        if (authToken != null) {
            outputStream.write(("Authorization: " + authToken + CRLF).getBytes());
        }
        outputStream.write(("CSeq: " + cSeq + CRLF).getBytes());
        if (userAgent != null) {
            outputStream.write(("User-Agent: " + userAgent + CRLF).getBytes());
        }
        if (session != null) outputStream.write(("Session: " + session + CRLF).getBytes());
        outputStream.write(CRLF.getBytes());
        outputStream.flush();
    }

    public static void sendSetupCommand(@NonNull OutputStream outputStream, @NonNull String request, long cSeq,
                                         @Nullable String userAgent, @Nullable String authToken, @Nullable String session,
                                         @NonNull String interleaved) throws IOException {
        if (DEBUG) {
            Log.v(TAG, "sendSetupCommand(request=\"" + request + "\", cSeq=" + cSeq + ")");
        }
        outputStream.write(("SETUP " + request + " RTSP/1.0" + CRLF).getBytes());
        outputStream.write(("Transport: RTP/AVP/TCP;unicast;interleaved=" + interleaved + CRLF).getBytes());
        if (authToken != null) {
            outputStream.write(("Authorization: " + authToken + CRLF).getBytes());
        }
        outputStream.write(("CSeq: " + cSeq + CRLF).getBytes());
        if (userAgent != null) {
            outputStream.write(("User-Agent: " + userAgent + CRLF).getBytes());
        }
        if (session != null) {
            outputStream.write(("Session: " + session + CRLF).getBytes());
        }
        outputStream.write(CRLF.getBytes());
        outputStream.flush();
    }

    public static void sendPlayCommand(@NonNull OutputStream outputStream, @NonNull String request, long cSeq,
                                        @Nullable String userAgent, @Nullable String authToken, @NonNull String session) throws IOException {
        if (DEBUG) {
            Log.v(TAG, "sendPlayCommand(request=\"" + request + "\", cSeq=" + cSeq + ")");
        }

        outputStream.write(("PLAY " + request + " RTSP/1.0" + CRLF).getBytes());
        outputStream.write(("Range: npt=0.000-" + CRLF).getBytes());

        if (authToken != null) {
            outputStream.write(("Authorization: " + authToken + CRLF).getBytes());
        }

        outputStream.write(("CSeq: " + cSeq + CRLF).getBytes());
        if (userAgent != null) {
            outputStream.write(("User-Agent: " + userAgent + CRLF).getBytes());
        }
        outputStream.write(("Session: " + session + CRLF).getBytes());
        outputStream.write(CRLF.getBytes());
        outputStream.flush();
    }

    // Digest authentication

    @Nullable
    public static String getDigestAuthHeader(@Nullable String username, @Nullable String password, @NonNull String method,
                                              @NonNull String digestUri, @NonNull RealmNonce rn) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] ha1;

            if (username == null) username = "";
            if (password == null) password = "";

            // calc A1 digest
            md.update(username.getBytes(StandardCharsets.ISO_8859_1));
            md.update((byte) ':');
            md.update(rn.realm.getBytes(StandardCharsets.ISO_8859_1));
            md.update((byte) ':');
            md.update(password.getBytes(StandardCharsets.ISO_8859_1));
            ha1 = md.digest();

            // calc A2 digest
            md.reset();
            md.update(method.getBytes(StandardCharsets.ISO_8859_1));
            md.update((byte) ':');
            md.update(digestUri.getBytes(StandardCharsets.ISO_8859_1));
            byte[] ha2 = md.digest();

            // calc response
            md.update(getHexStringFromBytes(ha1).getBytes(StandardCharsets.ISO_8859_1));
            md.update((byte) ':');
            md.update(rn.nonce.getBytes(StandardCharsets.ISO_8859_1));
            md.update((byte) ':');
            // TODO add support for more secure version of digest auth
            //md.update(nc.getBytes(StandardCharsets.ISO_8859_1));
            //md.update((byte) ':');
            //md.update(cnonce.getBytes(StandardCharsets.ISO_8859_1));
            //md.update((byte) ':');
            //md.update(qop.getBytes(StandardCharsets.ISO_8859_1));
            //md.update((byte) ':');
            md.update(getHexStringFromBytes(ha2).getBytes(StandardCharsets.ISO_8859_1));
            String response = getHexStringFromBytes(md.digest());

            return "Digest username=\"" + username + "\", realm=\"" + rn.realm + "\", nonce=\"" + rn.nonce + "\", uri=\"" + digestUri + "\", response=\"" + response + "\"";
        } catch (Exception e) {
            //ignore
        }
        return null;
    }

    @Nullable
    public static String getDigestAuthHeader(@Nullable Credentials credentials, @NonNull String method,
                                              @NonNull String digestUri, @NonNull RealmNonce rn) {

        if (credentials == null) {
            return getDigestAuthHeader(null, null, method, digestUri, rn);
        }

        return getDigestAuthHeader(credentials.username, credentials.password, method, digestUri, rn);
    }

    // Basic authentication
    @NonNull
    public static String getBasicAuthHeader(@Nullable String username, @Nullable String password) {
        String auth = (username == null ? "" : username) + ":" + (password == null ? "" : password);
        return "Basic " + new String(Base64.encode(auth.getBytes(StandardCharsets.ISO_8859_1), Base64.NO_WRAP));
    }

    @NonNull
    public static String getBasicAuthHeader(@Nullable Credentials credentials) {
        if (credentials == null) {
            return getBasicAuthHeader(null, null);
        }
        return getBasicAuthHeader(credentials.username, credentials.password);
    }

    @Nullable
    public static String getHeaderWwwAuthenticateBasicRealm(@NonNull Headers headers) throws InterruptedIOException {
        for (Header head : headers) {
            checkInterrupted();

            String h = head.name.toLowerCase();
            String v = head.value.toLowerCase();
            if ("www-authenticate".equals(h) && v.startsWith("basic")) {
                v = v.substring(6).trim();
                String[] tokens = TextUtils.split(v, "\"");
                if (tokens.length > 2) {
                    return tokens[1];
                }
            }
        }
        return null;
    }

    @Nullable
    public static RealmNonce getHeaderWwwAuthenticateDigestRealmAndNonce(@NonNull Headers headers) throws InterruptedIOException {
        for (Header header : headers) {
            checkInterrupted();
            String h = header.name.toLowerCase();
            if ("www-authenticate".equals(h) && header.value.toLowerCase().startsWith("digest")) {
                String v = header.value.substring(7).trim();
                int begin, end;

                begin = v.indexOf("realm=");
                begin = v.indexOf('"', begin) + 1;
                end = v.indexOf('"', begin);
                String digestRealm = v.substring(begin, end);

                begin = v.indexOf("nonce=");
                begin = v.indexOf('"', begin) + 1;
                end = v.indexOf('"', begin);
                String digestNonce = v.substring(begin, end);

                return new RealmNonce(digestRealm, digestNonce);
            }
        }
        return null;
    }

    public static ResponseHeader readResponseHeader(@NonNull InputStream inputStream) throws IOException {
        int status = readResponseStatusCode(inputStream);
        Headers headers = readResponseHeaders(inputStream);
        return new ResponseHeader(status, null, headers);
    }

    public static int getHeaderContentLength(@NonNull Headers headers) {
        String length = headers.get("content-length");
        if (!TextUtils.isEmpty(length)) {
            try {
                return Integer.parseInt(length);
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    public static Integer getSupportedCapabilities(@NonNull Headers headers) {
        for (Header header : headers) {
            String h = header.name.toLowerCase();
            if ("public".equals(h)) {
                int mask = 0;
                String[] tokens = TextUtils.split(header.value.toLowerCase(), ",");
                for (String token : tokens) {
                    switch (token.trim()) {
                        case "options":
                            mask |= RTSP_CAPABILITY_OPTIONS;
                            break;
                        case "describe":
                            mask |= RTSP_CAPABILITY_DESCRIBE;
                            break;
                        case "announce":
                            mask |= RTSP_CAPABILITY_ANNOUNCE;
                            break;
                        case "setup":
                            mask |= RTSP_CAPABILITY_SETUP;
                            break;
                        case "play":
                            mask |= RTSP_CAPABILITY_PLAY;
                            break;
                        case "record":
                            mask |= RTSP_CAPABILITY_RECORD;
                            break;
                        case "pause":
                            mask |= RTSP_CAPABILITY_PAUSE;
                            break;
                        case "teardown":
                            mask |= RTSP_CAPABILITY_TEARDOWN;
                            break;
                        case "set_parameter":
                            mask |= RTSP_CAPABILITY_SET_PARAMETER;
                            break;
                        case "get_parameter":
                            mask |= RTSP_CAPABILITY_GET_PARAMETER;
                            break;
                        case "redirect":
                            mask |= RTSP_CAPABILITY_REDIRECT;
                            break;
                    }
                }
                return mask;
            }
        }
        return null;
    }

    public static int readResponseStatusCode(@NonNull InputStream inputStream) throws IOException {
        String line;
        byte[] rtspHeader = "RTSP/1.0 ".getBytes();
        // Search fpr "RTSP/1.0 "
        while (readUntilBytesFound(inputStream, rtspHeader) &&
                (line = readLine(inputStream)) != null) {
            checkInterrupted();

            int indexCode = line.indexOf(' ');
            String code = line.substring(0, indexCode);
            try {
                return Integer.parseInt(code);
            } catch (NumberFormatException e) {
                // Does not fulfill standard "RTSP/1.1 200 OK" token
                // Continue search for
            }
        }
        return -1;
    }

    @NonNull
    public static Headers readResponseHeaders(@NonNull InputStream inputStream) throws IOException {
        Headers headers = new Headers();
        String line;
        while (!TextUtils.isEmpty(line = readLine(inputStream))) {
            checkInterrupted();
            if (CRLF.equals(line)) {
                return headers;
            } else {
                String[] pairs = TextUtils.split(line, ":");
                if (pairs.length == 2) {
                    headers.add(pairs[0].trim(), pairs[1].trim());
                }
            }
        }
        return headers;
    }

    @NonNull
    public static Headers getDescribeParams(@NonNull String text) throws InterruptedIOException {
        Headers res = new Headers();
        String[] params = TextUtils.split(text, "\r\n");
        int i;
        for (String param : params) {
            i = param.indexOf('=');
            if (i > 0) {
                res.add(param.substring(0, i).trim(), param.substring(i + 1));
            }
            checkInterrupted();
        }
        return res;
    }

    public static boolean hasCapability(int capability, int capabilitiesMask) {
        return (capabilitiesMask & capability) != 0;
    }
}
