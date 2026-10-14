package com.fivesoft.qplayer.impl.extractor.rtsp;

import static com.fivesoft.qplayer.common.NetUtils.readLine;
import static com.fivesoft.qplayer.common.Util.checkInterrupted;
import static com.fivesoft.qplayer.common.Util.getHexStringFromBytes;
import static com.fivesoft.qplayer.common.Util.readUntilBytesFound;

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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
        return getDigestAuthHeader(
                username,
                password,
                method,
                digestUri,
                rn,
                1,
                UUID.randomUUID().toString().replace("-", "")
        );
    }

    @Nullable
    public static String getDigestAuthHeader(@Nullable String username, @Nullable String password,
                                             @NonNull String method, @NonNull String digestUri,
                                             @NonNull RealmNonce rn, long nonceCount,
                                             @NonNull String cnonce) {
        try {
            if (username == null) username = "";
            if (password == null) password = "";

            String algorithm = rn.algorithm == null ? "MD5" : rn.algorithm.trim();
            if (!"MD5".equalsIgnoreCase(algorithm) && !"MD5-sess".equalsIgnoreCase(algorithm)) {
                return null;
            }

            String qop = selectDigestQop(rn.qop);
            if (rn.qop != null && qop == null) {
                return null;
            }

            String ha1 = md5(username + ":" + rn.realm + ":" + password);
            if ("MD5-sess".equalsIgnoreCase(algorithm)) {
                ha1 = md5(ha1 + ":" + rn.nonce + ":" + cnonce);
            }
            String ha2 = md5(method + ":" + digestUri);
            String nonceCountHex = String.format(Locale.US, "%08x", nonceCount);
            String response = qop == null
                    ? md5(ha1 + ":" + rn.nonce + ":" + ha2)
                    : md5(ha1 + ":" + rn.nonce + ":" + nonceCountHex + ":" +
                    cnonce + ":" + qop + ":" + ha2);

            StringBuilder result = new StringBuilder("Digest username=\"")
                    .append(escapeQuoted(username))
                    .append("\", realm=\"").append(escapeQuoted(rn.realm))
                    .append("\", nonce=\"").append(escapeQuoted(rn.nonce))
                    .append("\", uri=\"").append(escapeQuoted(digestUri))
                    .append("\", response=\"").append(response).append('"');

            if (rn.algorithm != null) {
                result.append(", algorithm=").append(algorithm);
            }
            if (rn.opaque != null) {
                result.append(", opaque=\"").append(escapeQuoted(rn.opaque)).append('"');
            }
            if (qop != null) {
                result.append(", qop=").append(qop)
                        .append(", nc=").append(nonceCountHex)
                        .append(", cnonce=\"").append(escapeQuoted(cnonce)).append('"');
            }
            return result.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static String getDigestAuthHeader(@Nullable Credentials credentials, @NonNull String method,
                                              @NonNull String digestUri, @NonNull RealmNonce rn) {

        if (credentials == null) {
            return getDigestAuthHeader(null, null, method, digestUri, rn);
        }

        return getDigestAuthHeader(
                credentials.getUsername(),
                credentials.getPassword(),
                method,
                digestUri,
                rn
        );
    }

    @Nullable
    public static String getDigestAuthHeader(@Nullable Credentials credentials, @NonNull String method,
                                             @NonNull String digestUri, @NonNull RealmNonce rn,
                                             long nonceCount) {
        String username = credentials == null ? null : credentials.getUsername();
        String password = credentials == null ? null : credentials.getPassword();
        return getDigestAuthHeader(
                username,
                password,
                method,
                digestUri,
                rn,
                nonceCount,
                UUID.randomUUID().toString().replace("-", "")
        );
    }

    @NonNull
    private static String md5(@NonNull String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        return getHexStringFromBytes(digest.digest(value.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Nullable
    private static String selectDigestQop(@Nullable String qopList) {
        if (qopList == null) {
            return null;
        }
        for (String qop : qopList.split(",")) {
            if ("auth".equalsIgnoreCase(qop.trim())) {
                return "auth";
            }
        }
        return null;
    }

    @NonNull
    private static String escapeQuoted(@NonNull String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
        return getBasicAuthHeader(
                credentials.getUsername(),
                credentials.getPassword()
        );
    }

    @Nullable
    public static String getHeaderWwwAuthenticateBasicRealm(@NonNull Headers headers) throws InterruptedIOException {
        for (Header header : headers) {
            checkInterrupted();
            if (header == null || header.getName() == null || header.getValue() == null ||
                    !"www-authenticate".equalsIgnoreCase(header.getName())) {
                continue;
            }
            String value = header.getValue().trim();
            if (value.regionMatches(true, 0, "Basic", 0, 5)) {
                String realm = parseAuthenticationParameters(value).get("realm");
                return realm == null ? "" : realm;
            }
        }
        return null;
    }

    @Nullable
    public static RealmNonce getHeaderWwwAuthenticateDigestRealmAndNonce(@NonNull Headers headers) throws InterruptedIOException {
        for (Header header : headers) {
            checkInterrupted();
            if (header == null || header.getName() == null || header.getValue() == null ||
                    !"www-authenticate".equalsIgnoreCase(header.getName())) {
                continue;
            }
            String value = header.getValue().trim();
            if (value.regionMatches(true, 0, "Digest", 0, 6)) {
                Map<String, String> params = parseAuthenticationParameters(value);
                String nonce = params.get("nonce");
                if (nonce == null || nonce.isEmpty()) {
                    continue;
                }
                return new RealmNonce(
                        params.getOrDefault("realm", ""),
                        nonce,
                        params.get("algorithm"),
                        params.get("qop"),
                        params.get("opaque")
                );
            }
        }
        return null;
    }

    @NonNull
    private static Map<String, String> parseAuthenticationParameters(@NonNull String value) {
        Map<String, String> result = new HashMap<>();
        int position = value.indexOf(' ');
        position = position < 0 ? value.length() : position + 1;

        while (position < value.length()) {
            while (position < value.length() &&
                    (value.charAt(position) == ',' || Character.isWhitespace(value.charAt(position)))) {
                position++;
            }
            int equals = value.indexOf('=', position);
            if (equals < 0) {
                break;
            }
            String name = value.substring(position, equals).trim().toLowerCase(Locale.US);
            position = equals + 1;

            String parameterValue;
            if (position < value.length() && value.charAt(position) == '"') {
                position++;
                StringBuilder quoted = new StringBuilder();
                boolean escaped = false;
                while (position < value.length()) {
                    char current = value.charAt(position++);
                    if (escaped) {
                        quoted.append(current);
                        escaped = false;
                    } else if (current == '\\') {
                        escaped = true;
                    } else if (current == '"') {
                        break;
                    } else {
                        quoted.append(current);
                    }
                }
                parameterValue = quoted.toString();
            } else {
                int comma = value.indexOf(',', position);
                int end = comma < 0 ? value.length() : comma;
                parameterValue = value.substring(position, end).trim();
                position = end;
            }
            if (!name.isEmpty()) {
                result.put(name, parameterValue);
            }
        }
        return result;
    }

    public static ResponseHeader readResponseHeader(@NonNull InputStream inputStream) throws IOException {
        ResponseHeader status = readResponseStatus(inputStream);
        Headers headers = readResponseHeaders(inputStream);
        return new ResponseHeader(status.getCode(), status.getMessage(), headers);
    }

    public static int getHeaderContentLength(@NonNull Headers headers) {
        String length = headers.get("content-length");
        if (length != null && !length.trim().isEmpty()) {
            try {
                return Integer.parseInt(length.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    public static Integer getSupportedCapabilities(@NonNull Headers headers) {
        for (Header header : headers) {
            if (header == null || header.getName() == null || header.getValue() == null) {
                continue;
            }
            String h = header.getName().toLowerCase(Locale.US);
            if ("public".equals(h) || "allow".equals(h)) {
                int mask = 0;
                String[] tokens = header.getValue().toLowerCase(Locale.US).split(",");
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
        return readResponseStatus(inputStream).getCode();
    }

    @NonNull
    private static ResponseHeader readResponseStatus(@NonNull InputStream inputStream) throws IOException {
        byte[] marker = "RTSP/".getBytes(StandardCharsets.US_ASCII);
        if (!readUntilBytesFound(inputStream, marker)) {
            throw new IOException("Unexpected end of RTSP stream");
        }

        String line = readLine(inputStream);
        if (line == null) {
            throw new IOException("Missing RTSP status line");
        }
        String[] parts = line.trim().split("\\s+", 3);
        if (parts.length < 2) {
            throw new IOException("Invalid RTSP status line: RTSP/" + line);
        }
        try {
            int code = Integer.parseInt(parts[1]);
            String message = parts.length == 3 ? parts[2] : null;
            return new ResponseHeader(code, message, new Headers());
        } catch (NumberFormatException e) {
            throw new IOException("Invalid RTSP status line: RTSP/" + line, e);
        }
    }

    @NonNull
    public static Headers readResponseHeaders(@NonNull InputStream inputStream) throws IOException {
        Headers headers = new Headers();
        String line;
        while ((line = readLine(inputStream)) != null && !line.isEmpty()) {
            checkInterrupted();
            int separator = line.indexOf(':');
            if (separator > 0) {
                headers.add(
                        line.substring(0, separator).trim(),
                        line.substring(separator + 1).trim()
                );
            }
        }
        return headers;
    }

    @NonNull
    public static Headers getDescribeParams(@NonNull String text) throws InterruptedIOException {
        Headers res = new Headers();
        String[] params = text.split("\\r?\\n");
        int i;
        for (String param : params) {
            i = param.indexOf('=');
            if (i > 0) {
                res.add(param.substring(0, i).trim(), param.substring(i + 1).trim());
            }
            checkInterrupted();
        }
        return res;
    }

    @Nullable
    public static String getSessionId(@Nullable String sessionHeader) {
        if (sessionHeader == null) {
            return null;
        }
        int separator = sessionHeader.indexOf(';');
        String sessionId = (separator < 0 ? sessionHeader : sessionHeader.substring(0, separator)).trim();
        return sessionId.isEmpty() ? null : sessionId;
    }

    public static int getSessionTimeoutSeconds(@Nullable String sessionHeader, int defaultValue) {
        if (sessionHeader == null) {
            return defaultValue;
        }
        String[] parameters = sessionHeader.split(";");
        for (int i = 1; i < parameters.length; i++) {
            String[] pair = parameters[i].trim().split("=", 2);
            if (pair.length == 2 && "timeout".equalsIgnoreCase(pair[0].trim())) {
                try {
                    int timeout = Integer.parseInt(pair[1].trim());
                    return timeout > 0 ? timeout : defaultValue;
                } catch (NumberFormatException ignored) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    public static boolean hasCapability(int capability, int capabilitiesMask) {
        return (capabilitiesMask & capability) != 0;
    }
}
