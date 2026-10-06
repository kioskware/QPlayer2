package com.fivesoft.qplayer.impl.extractor.rtp;

import android.util.Base64;

import com.fivesoft.qplayer.base.track.AudioTrack;
import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.base.track.Tracks;
import com.fivesoft.qplayer.base.track.VideoTrack;
import com.fivesoft.qplayer.impl.decoder.video.h264.SPSParser;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Maps explicit URI/SDP codec descriptions to the library's existing Track model. */
public final class RtpTrackConfig {
    private RtpTrackConfig() {}

    public static Tracks fromUri(URI uri) {
        Map<String, String> params = query(uri);
        if (params.containsKey("sdp")) {
            return fromSdp(params.get("sdp"));
        }
        String codec = params.getOrDefault("codec", integer(params, "payload-type", 96) == 33 ? "mp2t" : "h264");
        boolean transportStream = isTransportStream(codec);
        boolean audio = "aac".equalsIgnoreCase(codec) || "mpeg4-generic".equalsIgnoreCase(codec);
        Track.Builder builder = new Track.Builder(audio ? Track.AUDIO : Track.VIDEO)
                .setId(audio ? "rtp-audio" : "rtp-video")
                .setFormat(transportStream ? "mp2t" : audio ? "mpeg4-generic" : codec.toLowerCase(Locale.US))
                .setPayloadType(integer(params, "payload-type", transportStream ? 33 : 96))
                .setClockRate(integer(params, "clock-rate", audio ? 44100 : 90000))
                .setChannels(integer(params, "channels", 2))
                .setMode(params.getOrDefault("mode", "AAC-hbr"));
        applyFmtp(builder, params);
        return new Tracks(validate(builder));
    }

    /**
     * Parses H.264 and MPEG4-GENERIC media descriptions. The caller supplies the
     * transport; SDP connection addresses and ports do not open additional sockets.
     */
    public static Tracks fromSdp(String sdp) {
        List<MediaDescription> descriptions = new ArrayList<>();
        MediaDescription current = null;
        for (String rawLine : sdp.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.startsWith("m=")) {
                String[] fields = line.substring(2).split("\\s+");
                current = null;
                if (fields.length >= 4 && !"0".equals(fields[1])
                        && ("RTP/AVP".equalsIgnoreCase(fields[2])
                        || "RTP/AVPF".equalsIgnoreCase(fields[2]))) {
                    int type = "video".equals(fields[0]) ? Track.VIDEO
                            : "audio".equals(fields[0]) ? Track.AUDIO : -1;
                    if (type != -1) {
                        current = new MediaDescription();
                        for (int i = 3; i < fields.length; i++) {
                            int payload = Integer.parseInt(fields[i]);
                            current.builders.put(payload, new Track.Builder(type)
                                    .setId("rtp-" + descriptions.size() + "-" + payload)
                                    .setPayloadType(payload).setTag(descriptions.size()));
                            if (payload == 33) current.builders.get(payload).setFormat("mp2t").setClockRate(90000);
                        }
                        descriptions.add(current);
                    }
                }
            } else if (current != null && line.startsWith("a=rtpmap:")) {
                String[] fields = line.substring(9).trim().split("\\s+", 2);
                if (fields.length != 2) throw new IllegalArgumentException("Invalid SDP rtpmap");
                Track.Builder builder = current.builders.get(Integer.parseInt(fields[0]));
                if (builder != null) {
                    String[] encoding = fields[1].split("/");
                    if (encoding.length < 2) throw new IllegalArgumentException("Missing RTP clock rate");
                    builder.setFormat(encoding[0].toLowerCase(Locale.US))
                            .setClockRate(Integer.parseInt(encoding[1]))
                            .setChannels(encoding.length > 2 ? Integer.parseInt(encoding[2]) : 1);
                }
            } else if (current != null && line.startsWith("a=fmtp:")) {
                String[] fields = line.substring(7).trim().split("\\s+", 2);
                if (fields.length == 2) {
                    Map<String, String> fmtp = new HashMap<>();
                    for (String pair : fields[1].split(";")) {
                        String[] values = pair.trim().split("=", 2);
                        if (values.length == 2) {
                            fmtp.put(values[0].trim().toLowerCase(Locale.US), values[1].trim());
                        }
                    }
                    current.fmtp.put(Integer.parseInt(fields[0]), fmtp);
                }
            } else if (current != null && line.startsWith("a=framerate:")) {
                float fps = Float.parseFloat(line.substring(12).trim());
                if (!Float.isFinite(fps) || fps <= 0) throw new IllegalArgumentException("Invalid frame rate");
                for (Track.Builder builder : current.builders.values()) builder.setFps(fps);
            }
        }
        Tracks tracks = new Tracks();
        for (MediaDescription description : descriptions) {
            for (Map.Entry<Integer, Track.Builder> entry : description.builders.entrySet()) {
                Track.Builder builder = entry.getValue();
                String format = builder.getFormat();
                if (!"h264".equals(format) && !"mpeg4-generic".equals(format) && !isTransportStream(format)) continue;
                if (isTransportStream(format)) builder.setFormat("mp2t");
                applyFmtp(builder, description.fmtp.getOrDefault(entry.getKey(), new HashMap<>()));
                Track track = validate(builder);
                if (tracks.getByPayloadType(track.getPayloadType()) != null) {
                    throw new IllegalArgumentException("Ambiguous RTP payload type: " + track.getPayloadType());
                }
                tracks.put(track);
            }
        }
        if (tracks.size() == 0) throw new IllegalArgumentException("SDP has no supported H.264/AAC/MP2T tracks");
        return tracks;
    }

    static void validateTrack(Track track) {
        int payload = track.getPayloadType();
        if (payload < 0 || payload > 127 || (payload >= 64 && payload <= 95)) {
            throw new IllegalArgumentException("Invalid or RTCP-mux reserved RTP payload type: " + payload);
        }
        if (track.getClockRate() <= 0) throw new IllegalArgumentException("RTP clock rate must be positive");
        if (track instanceof RtpTransportStreamTrack) {
            if (track.getClockRate() != 90000) throw new IllegalArgumentException("MP2T requires a 90000 Hz RTP clock");
        } else if (track instanceof AudioTrack) {
            AudioTrack audio = (AudioTrack) track;
            if (!"mpeg4-generic".equalsIgnoreCase(track.getFormat())
                    && !"aac".equalsIgnoreCase(track.getFormat())) {
                throw new IllegalArgumentException("Unsupported RTP audio codec: " + track.getFormat());
            }
            if (audio.getChannels() <= 0 || audio.getChannels() > 8) {
                throw new IllegalArgumentException("Invalid AAC channel count");
            }
            if (track.getCsd().getCsd(0) == null) {
                throw new IllegalArgumentException("AAC requires an AudioSpecificConfig (config)");
            }
            validateIntegerParameter(track, "sizelength", 1, 31);
            validateIntegerParameter(track, "indexlength", 0, 31);
            validateIntegerParameter(track, "indexdeltalength", 0, 31);
            validateIntegerParameter(track, "constantduration", 1, Integer.MAX_VALUE);
            String mode = audio.getMode();
            if (mode == null) audio.setMode("AAC-hbr");
            else if (!"AAC-hbr".equalsIgnoreCase(mode) && !"AAC-lbr".equalsIgnoreCase(mode)) {
                throw new IllegalArgumentException("Unsupported AAC RTP mode: " + mode);
            }
            for (String field : new String[]{"ctsdeltalength", "dtsdeltalength", "randomaccessindication",
                    "streamstateindication", "auxiliarydatasizelength", "maxdisplacement"}) {
                Object value = track.getMetadata(field);
                if (value != null && Integer.parseInt(value.toString()) != 0) {
                    throw new IllegalArgumentException("Unsupported AAC RTP parameter: " + field);
                }
            }
        } else if (!(track instanceof VideoTrack) || !"h264".equalsIgnoreCase(track.getFormat())) {
            throw new IllegalArgumentException("Unsupported RTP codec: " + track.getFormat());
        } else {
            Object mode = track.getMetadata("packetization-mode");
            if (mode != null && !"0".equals(mode.toString()) && !"1".equals(mode.toString())) {
                throw new IllegalArgumentException("H.264 interleaved packetization mode is unsupported");
            }
        }
    }

    private static Track validate(Track.Builder builder) {
        if (builder.getClockRate() <= 0) throw new IllegalArgumentException("Missing RTP clock rate");
        Track track;
        if ("mp2t".equals(builder.getFormat())) {
            track = new RtpTransportStreamTrack(builder.getPayloadType());
            track.setClockRate(builder.getClockRate());
            for (Map.Entry<String, Object> entry : builder.getMetadata().entrySet()) track.setMetadata(entry.getKey(), entry.getValue());
        } else track = builder.build();
        if (track == null) throw new IllegalArgumentException("Invalid RTP track");
        validateTrack(track);
        return track;
    }

    static boolean isAutoDetectUri(URI uri) {
        Map<String, String> params = query(uri);
        return !params.containsKey("codec") && !params.containsKey("payload-type") && !params.containsKey("sdp");
    }

    private static boolean isTransportStream(String codec) {
        return "mp2t".equalsIgnoreCase(codec) || "mpegts".equalsIgnoreCase(codec)
                || "mpeg-ts".equalsIgnoreCase(codec) || "ts".equalsIgnoreCase(codec)
                || "video/mp2t".equalsIgnoreCase(codec);
    }

    private static void validateIntegerParameter(Track track, String key, int minimum, int maximum) {
        Object value = track.getMetadata(key);
        if (value == null) return;
        int number = Integer.parseInt(value.toString());
        if (number < minimum || number > maximum) throw new IllegalArgumentException("Invalid AAC RTP parameter: " + key);
    }

    private static void applyFmtp(Track.Builder builder, Map<String, String> params) {
        for (Map.Entry<String, String> param : params.entrySet()) {
            String key = param.getKey().toLowerCase(Locale.US);
            String value = param.getValue();
            if ("config".equals(key)) {
                builder.setCsd(0, decodeHex(value));
            } else if ("sprop-parameter-sets".equals(key)) {
                String[] sets = value.split(",");
                if (sets.length != 2) throw new IllegalArgumentException("Expected SPS and PPS");
                byte[] sps = Base64.decode(sets[0], Base64.NO_WRAP);
                builder.setCsd(0, sps);
                builder.setCsd(1, Base64.decode(sets[1], Base64.NO_WRAP));
                if (builder.getType() == Track.VIDEO) {
                    SPSParser.VideoParams video = SPSParser.parseSPSStatic(sps, 0, sps.length);
                    builder.setWidth(video.width).setHeight(video.height)
                            .setPixelAspectRatio(video.getPixelAspectRatio());
                    if (video.frameRate > 0) builder.setFps(video.frameRate);
                }
            } else if ("mode".equals(key)) {
                builder.setMode(value);
            } else {
                builder.setMetadata(key, value);
            }
        }
        if (builder.getType() == Track.AUDIO && builder.getMode() == null) builder.setMode("AAC-hbr");
    }

    private static byte[] decodeHex(String value) {
        if (value.length() < 4 || (value.length() & 1) != 0) {
            throw new IllegalArgumentException("Invalid AAC config hex string");
        }
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid AAC config hex string");
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> result = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) return result;
        try {
            for (String pair : query.split("&")) {
                String[] values = pair.split("=", 2);
                String key = URLDecoder.decode(values[0], "UTF-8").toLowerCase(Locale.US);
                String value = values.length > 1 ? URLDecoder.decode(values[1], "UTF-8") : "";
                if (result.put(key, value) != null) throw new IllegalArgumentException("Duplicate RTP parameter: " + key);
            }
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
        return result;
    }

    private static int integer(Map<String, String> params, String key, int defaultValue) {
        return params.containsKey(key) ? Integer.parseInt(params.get(key)) : defaultValue;
    }

    private static final class MediaDescription {
        final Map<Integer, Track.Builder> builders = new LinkedHashMap<>();
        final Map<Integer, Map<String, String>> fmtp = new HashMap<>();
    }
}
