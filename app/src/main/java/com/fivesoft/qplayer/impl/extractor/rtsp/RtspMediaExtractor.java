package com.fivesoft.qplayer.impl.extractor.rtsp;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Authentication;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.components.DataSource;
import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.base.components.MediaExtractor;
import com.fivesoft.qplayer.base.track.AudioTrack;
import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.base.track.Tracks;
import com.fivesoft.qplayer.common.Constants;
import com.fivesoft.qplayer.common.Header;
import com.fivesoft.qplayer.common.Headers;
import com.fivesoft.qplayer.common.NetUtils;
import com.fivesoft.qplayer.common.TimeoutException;
import com.fivesoft.qplayer.common.TrackSelector;
import com.fivesoft.qplayer.common.Util;
import com.fivesoft.qplayer.common.X;
import com.fivesoft.qplayer.common.resolver.Creator;
import com.fivesoft.qplayer.impl.decoder.video.h264.SPSParser;
import com.fivesoft.qplayer.impl.extractor.rtp.RtpParser;
import com.fivesoft.qplayer.impl.extractor.rtp.RtpSample;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/*
    Based on https://github.com/alexeyvasilyev/rtsp-client-android
 */

public class RtspMediaExtractor extends MediaExtractor {

    public static final Creator<Descriptor, MediaExtractor> CREATOR =
            new Creator<Descriptor, MediaExtractor>() {
        @Override
        public int accept(Descriptor t) {
            if(t == null)
                return 0;

            if(t.uri == null || t.dataSource == null || !t.dataSource.isOutSupported())
                return 0;

            if(!"rtsp".equalsIgnoreCase(t.uri.getScheme()))
                return 0;

            return 1;
        }

        @Nullable
        @Override
        public MediaExtractor create(Descriptor t) {
            if(accept(t) > 0){
                return new RtspMediaExtractor(t.dataSource, t.trackSelector, t.uri.toString());
            }
            return null;
        }
    };

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    public static final int MIN_SESSION_TIMEOUT = 15;
    public static final int MAX_SESSION_TIMEOUT = 60;

    public static final String DEFAULT_USER_AGENT = Constants.Q_PLAYER_USER_AGENT;

    private volatile boolean opened = true;
    private final String uri;
    @Nullable
    private final Authentication uriAuthentication;

    private String userAgent = DEFAULT_USER_AGENT;

    private byte[] readBuffer = new byte[0];

    private final Object bufferLock = new Object();

    private final Tracks tracks = new Tracks();

    private volatile long sessionTimeout = 0;
    private volatile long lastKeepAliveSent = Integer.MIN_VALUE;

    private volatile long sampleIndex = Constants.UNKNOWN_VALUE;

    private volatile long lastTimestamp = Constants.UNKNOWN_VALUE;

    private volatile long totalDuration = Constants.UNKNOWN_VALUE;

    private volatile long sampleCount = Constants.UNKNOWN_VALUE;

    private volatile boolean prepared = false;
    private volatile Authentication auth;
    private volatile RtspSession rtspSession;

    private int getSampleTimeout = 0;

    public RtspMediaExtractor(@NonNull DataSource dataSource, @Nullable TrackSelector trackSelector, String uri) {
        super(dataSource, trackSelector);
        URI sourceUri = URI.create(Objects.requireNonNull(uri));
        this.uri = withoutUserInfo(sourceUri).toASCIIString();
        this.uriAuthentication = getAuthenticationFromUri(sourceUri);
        setTimeout(5000); //Default timeout for re-preparing if needed when getting next sample
    }

    public RtspMediaExtractor(@NonNull DataSource dataSource, String uri) {
        this(dataSource, null, uri);
    }

    public void setUserAgent(@Nullable String userAgent) {
        this.userAgent = userAgent;
    }

    public void setBufferSize(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be positive");
        }
        synchronized (bufferLock) {
            readBuffer = new byte[bufferSize];
        }
    }

    @Override
    public void prepare(int timeout) throws IOException, TimeoutException, SecurityException {
        if (prepared) {
            throw new IllegalStateException("Already prepared");
        }

        InputStream in = dataSource.getInputStream();
        OutputStream out = dataSource.getOutputStream();

        if (out == null) {
            throw new IOException("Output is not supported by this data source");
        }

        Authentication effectiveAuthentication = auth != null ? auth : uriAuthentication;
        rtspSession = new RtspSession(in, out, userAgent, uri, effectiveAuthentication);

        // OPTIONS is optional in practice. Some cameras reject it but still support DESCRIBE.
        RtspResponse res = rtspSession.options();

        //Get tracks
        res = rtspSession.describe();

        if (!res.isOk()) {
            //Throw if failed to authenticate
            throw new IOException("RTSP describe failed: " + res.getCode() + " " + res.getMessage());
        }

        String describeBaseUri = getDescribeBaseUri(uri, res);
        Headers describeParams;
        try {
            describeParams = RtspUtil.getDescribeParams(res.getContentAsText());
            tracks.clear();
            updateTracks(describeParams);
            Log.println(Log.ASSERT, "RTSPMediaSource", "Tracks: " + tracks);
        } catch (Exception e) {
            throw new IOException("Failed to parse tracks", e);
        }

        String playUri = getAggregateControlUri(describeBaseUri, describeParams);
        String session = null, sessionHeader = null, uriForSetup;

        synchronized (tracks) {

            //Sort tracks by tags
            Track[] tracks = this.tracks.toArray();
            Arrays.sort(tracks, Comparator.comparingInt(Track::getTag));

            for (Track track : tracks) {
                if (track == null)
                    continue; //Just in case

                if (!trackSelector.selectTrack(track)) {
                    continue;
                }

                uriForSetup = getUriForSetup(describeBaseUri, track);

                if (uriForSetup == null) {
                    continue;
                }

                int clientChannel = (track.getTag() * 2);
                int serverChannel = (track.getTag() * 2 + 1);

                res = rtspSession.setup(uriForSetup, clientChannel, serverChannel, session);

                if (!res.isOk()) {
                    //Throw if failed to setup
                    throw new IOException("RTSP setup failed: " + res.getCode() + " " + res.getMessage());
                }

                String responseSessionHeader = res.getHeader("Session");
                if (!TextUtils.isEmpty(responseSessionHeader)) {
                    sessionHeader = responseSessionHeader;
                }
                session = rtspSession.getSession();
            }
        }

        if (TextUtils.isEmpty(session)) {
            throw new IOException("Failed to get RTSP session");
        }

        res = rtspSession.play(playUri, session);

        if (!res.isOk()) {
            //Throw if failed to play
            throw new IOException("RTSP play failed: " + res.getCode() + " " + res.getMessage());
        }

        updateSessionTimeout(sessionHeader);
        lastKeepAliveSent = System.currentTimeMillis();

        opened = true;
        prepared = true;
    }

    @Override
    public boolean isPrepared() {
        return prepared;
    }

    @Override
    public synchronized Sample nextSample() throws IOException, TimeoutException, IllegalStateException, InterruptedException {

        if (!prepared) {
            throw new IllegalStateException("Not prepared, call prepare() first");
        }

        sendKeepAliveIfNeeded();
        sampleIndex++;

        return readNextSample();
    }

    private int failedReadHeaderCount = 0;

    private Sample readNextSample() throws IOException, TimeoutException {
        //Read samples
        synchronized (bufferLock) {
            InputStream inputStream = dataSource.getInputStream();
            RtpParser.RtpHeader header = RtpParser.readHeader(inputStream);

            if (header == null) {
                failedReadHeaderCount++;
                if(failedReadHeaderCount > 1){
                    close();
                    prepare(getSampleTimeout);
                    failedReadHeaderCount = 0;
                }
                return null;
            }
            failedReadHeaderCount = 0;

            if (header.payloadSize > readBuffer.length || !isReuseBuffersAllowed()) {
                readBuffer = new byte[header.payloadSize];
            }

            if (NetUtils.readData(inputStream, readBuffer, 0, header.payloadSize) != header.payloadSize) {
                throw new IOException("Unexpected end of RTP packet");
            }

            int sampleLength = header.payloadSize;
            if (header.padding != 0 && sampleLength > 0) {
                int paddingLength = readBuffer[sampleLength - 1] & 0xFF;
                if (paddingLength > 0 && paddingLength <= sampleLength) {
                    sampleLength -= paddingLength;
                }
            }

            Track track = tracks.getByPayloadType(header.payloadType);

            if (track == null) {
                X.l("Failed to get track for payload type: " + header.payloadType);
                return null;
            }

            long timestamp = header.timestamp;
            if (track.getClockRate() > 0) {
                timestamp = (long) (((double) header.timestamp / (double) track.getClockRate()) * 1000.0);
            }

            lastTimestamp = timestamp;
            return new RtpSample(readBuffer, 0, sampleLength, timestamp, track, header, false);
        }
    }

    private void sendKeepAliveIfNeeded() {
        if(!opened)
            return;

        if (Math.abs(System.currentTimeMillis() - lastKeepAliveSent) <
                (Util.limit(sessionTimeout, MIN_SESSION_TIMEOUT, MAX_SESSION_TIMEOUT) * 1000 / 2))
            return;

        try {
            Log.println(Log.ASSERT, "RTSPMediaSource", "Sending keep alive timeout: " + sessionTimeout + "s");
            rtspSession.sendKeepAlive();
        } catch (Exception e) {
            //Failed to send keep alive command
        } finally {
            lastKeepAliveSent = System.currentTimeMillis();
        }

    }

    @NonNull
    @Override
    public Tracks getTracks() throws IllegalStateException {
        if (!prepared) {
            throw new IllegalStateException("Not prepared, call prepare() first");
        }
        return tracks;
    }

    @Override
    public long getPosition() {
        return lastTimestamp;
    }

    @Override
    public long getSampleIndex() {
        return sampleIndex;
    }

    @Override
    public int getSampleFormat() {
        return MediaDecoder.FORMAT_RTP;
    }

    @Override
    public void close() {
        RtspSession session = rtspSession;
        if (session != null) {
            session.close();
        }
        opened = false;
        prepared = false;
        readBuffer = new byte[0];
        lastKeepAliveSent = Integer.MIN_VALUE;
    }

    @Override
    public void setAuthentication(@Nullable Authentication authentication) {
        this.auth = authentication;
    }

    @Override
    protected void onTimeoutSet(int timeout) throws IOException {
        getSampleTimeout = timeout;
    }

    private void updateSessionTimeout(@Nullable String session) {
        sessionTimeout = RtspUtil.getSessionTimeoutSeconds(session, 0);
        Log.println(Log.ASSERT, "RTSPMediaSource", "Session timeout: " + sessionTimeout);
    }

    @Nullable
    static String getUriForSetup(@NonNull String uri, @Nullable Track track) {
        if (track == null || TextUtils.isEmpty(track.getId()))
            return null;
        return resolveControlUri(uri, track.getId());
    }

    @NonNull
    static String resolveControlUri(@NonNull String baseUri, @Nullable String control) {
        if (control == null || control.trim().isEmpty() || "*".equals(control.trim())) {
            return baseUri;
        }

        String value = control.trim();
        URI controlUri = URI.create(value);
        if (controlUri.isAbsolute()) {
            return withoutUserInfo(controlUri).toASCIIString();
        }

        URI base = URI.create(baseUri);
        String controlPath = controlUri.getPath();
        String path;
        if (controlPath != null && controlPath.startsWith("/")) {
            path = controlPath;
        } else {
            String basePath = base.getPath();
            if (basePath == null || basePath.isEmpty()) {
                basePath = "/";
            } else if (!basePath.endsWith("/")) {
                basePath += "/";
            }
            path = basePath + (controlPath == null ? "" : controlPath);
        }
        String query = controlUri.getQuery() == null ? base.getQuery() : controlUri.getQuery();
        try {
            return new URI(
                    base.getScheme(),
                    null,
                    base.getHost(),
                    base.getPort(),
                    path,
                    query,
                    null
            ).toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid RTSP control URI", e);
        }
    }

    @NonNull
    private static String getDescribeBaseUri(@NonNull String requestUri, @NonNull RtspResponse response) {
        String base = response.getHeader("Content-Base");
        if (TextUtils.isEmpty(base)) {
            base = response.getHeader("Content-Location");
        }
        if (TextUtils.isEmpty(base)) {
            return requestUri;
        }
        return URI.create(requestUri).resolve(base.trim()).toASCIIString();
    }

    @NonNull
    private static String getAggregateControlUri(@NonNull String baseUri, @NonNull Headers params) {
        for (Header param : params) {
            if (param == null || param.getName() == null || param.getValue() == null) {
                continue;
            }
            if ("m".equalsIgnoreCase(param.getName())) {
                break;
            }
            if ("a".equalsIgnoreCase(param.getName()) &&
                    param.getValue().regionMatches(true, 0, "control:", 0, 8)) {
                return resolveControlUri(baseUri, param.getValue().substring(8));
            }
        }
        return baseUri;
    }

    @Nullable
    private static Authentication getAuthenticationFromUri(@NonNull URI uri) {
        String userInfo = uri.getUserInfo();
        if (TextUtils.isEmpty(userInfo)) {
            return null;
        }
        int separator = userInfo.indexOf(':');
        String username = separator < 0 ? userInfo : userInfo.substring(0, separator);
        String password = separator < 0 ? null : userInfo.substring(separator + 1);
        return new Authentication(username, password);
    }

    @NonNull
    private static URI withoutUserInfo(@NonNull URI uri) {
        if (uri.getRawUserInfo() == null) {
            return uri;
        }
        try {
            return new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid RTSP URI", e);
        }
    }

    private void updateTracks(@NonNull Headers params) throws InterruptedIOException {
        Track.Builder cb = null;
        int trackCount = 0;
        for (Header param : params) {
            Util.checkInterrupted();
            if (param == null || param.getName() == null || param.getValue() == null) {
                continue;
            }
            String name = param.getName().toLowerCase(Locale.US);
            String value = param.getValue().trim();
            switch (name) {
                case "m": {
                    addTrack(cb);
                    if (value.regionMatches(true, 0, "video", 0, 5)) {
                        cb = new Track.Builder(Track.VIDEO);
                    } else if (value.regionMatches(true, 0, "audio", 0, 5)) {
                        cb = new AudioTrack.Builder(Track.AUDIO);
                    } else {
                        cb = null;
                    }
                    if (cb != null) {
                        String[] values = value.split("\\s+");
                        try {
                            cb.setPayloadType(values.length > 3 ? Integer.parseInt(values[3]) : -1);
                        } catch (Exception e) {
                            cb.setPayloadType(-1);
                        }
                        cb.setTag(trackCount++);
                    }
                    break;
                }
                case "a": {
                    if (cb != null) {
                        if (value.regionMatches(true, 0, "control:", 0, 8)) {
                            cb.setId(value.substring(8).trim());
                        } else if (value.regionMatches(true, 0, "fmtp:", 0, 5)) {
                            int separator = firstWhitespace(value, 5);
                            parseRtspParamString(
                                    separator < 0 ? "" : value.substring(separator + 1).trim(),
                                    cb
                            );
                        } else if (value.regionMatches(true, 0, "rtpmap:", 0, 7)) {
                            parseRtpMap(value, cb);
                        } else if (value.regionMatches(true, 0, "framesize:", 0, 10)) {
                            parseFrameSize(value, cb);
                        } else if (value.regionMatches(true, 0, "framerate:", 0, 10)) {
                            parseFrameRate(value, cb);
                        }
                    }
                    break;
                }
                case "s": {
                    if (cb != null) {
                        cb.setTitle(value);
                    }
                    break;
                }
                case "i": {
                    if (cb != null) {
                        cb.setDescription(value);
                    }
                    break;
                }
            }
        }
        addTrack(cb);
    }

    private void addTrack(@Nullable Track.Builder builder) {
        if (builder == null || TextUtils.isEmpty(builder.getId())) {
            return;
        }
        Track track = builder.build();
        if (track != null) {
            synchronized (tracks) {
                tracks.put(track);
            }
        }
    }

    private static int firstWhitespace(@NonNull String value, int start) {
        for (int i = start; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static void parseRtpMap(@NonNull String value, @NonNull Track.Builder target) {
        String[] fields = value.split("\\s+", 2);
        if (fields.length < 2) {
            return;
        }
        String[] encoding = fields[1].trim().split("/");
        if (encoding.length == 0) {
            return;
        }
        target.setFormat(encoding[0].toLowerCase(Locale.US));
        if (encoding.length > 1) {
            try {
                target.setClockRate(Integer.parseInt(encoding[1]));
            } catch (NumberFormatException e) {
                target.setClockRate(Constants.UNKNOWN_VALUE);
            }
        }
        if (target.getType() == Track.AUDIO) {
            int channels = 1;
            if (encoding.length > 2) {
                try {
                    channels = Integer.parseInt(encoding[2]);
                } catch (NumberFormatException ignored) {
                    channels = Constants.UNKNOWN_VALUE;
                }
            }
            target.setChannels(channels);
        }
    }

    static void parseFrameSize(@NonNull String value, @NonNull Track.Builder target) {
        if (target.getType() != Track.VIDEO) {
            return;
        }
        String[] fields = value.substring(Math.min(10, value.length())).trim().split("\\s+", 2);
        if (fields.length < 2) {
            return;
        }
        try {
            int payloadType = Integer.parseInt(fields[0]);
            if (target.getPayloadType() >= 0 && payloadType != target.getPayloadType()) {
                return;
            }
            String dimensions = fields[1].trim().split("\\s+", 2)[0];
            String[] size = dimensions.split("[-xX]", 2);
            if (size.length != 2) {
                return;
            }
            int width = Integer.parseInt(size[0]);
            int height = Integer.parseInt(size[1]);
            if (width > 0 && height > 0) {
                target.setWidth(width);
                target.setHeight(height);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    static void parseFrameRate(@NonNull String value, @NonNull Track.Builder target) {
        if (target.getType() != Track.VIDEO) {
            return;
        }
        try {
            float frameRate = Float.parseFloat(value.substring(Math.min(10, value.length())).trim());
            if (Float.isFinite(frameRate) && frameRate > 0) {
                target.setFps(frameRate);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private static void parseRtspParamString(@NonNull String input, @NonNull Track.Builder target) {
        String[] keyValuePairs = input.split(";");

        for (String pair : keyValuePairs) {
            String[] keyValue = pair.trim().split("=", 2);

            if (keyValue.length == 2) {
                String key = keyValue[0].trim();
                String value = keyValue[1].trim();

                if ("sprop-parameter-sets".equalsIgnoreCase(key)) {
                    String[] paramsSpsPps = TextUtils.split(value, ",");
                    try {
                        if (paramsSpsPps.length > 1) {
                            byte[] sps = Base64.decode(paramsSpsPps[0], Base64.NO_WRAP);
                            byte[] pps = Base64.decode(paramsSpsPps[1], Base64.NO_WRAP);
                            target.setCsd(0, sps);
                            target.setCsd(1, pps);
                            SPSParser.VideoParams videoParams = SPSParser.parseSPSStatic(sps, 0, sps.length);
                            if (target.getType() == Track.VIDEO) {
                                target.setWidth(videoParams.width);
                                target.setHeight(videoParams.height);
                                target.setPixelAspectRatio(videoParams.getPixelAspectRatio());
                                if (videoParams.frameRate > 0) {
                                    target.setFps(videoParams.frameRate);
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                } else if ("config".equalsIgnoreCase(key)) {
                    try {
                        target.setCsd(0, Util.getBytesFromHexString(value));
                    } catch (Exception ignored) {
                    }
                } else if ("mode".equalsIgnoreCase(key)) {
                    target.setMode(value);
                } else {
                    // Add the key-value pair to metadata
                    target.setMetadata(key, value);
                }
            }
        }
    }

}
