package com.fivesoft.qplayer.impl.extractor.rtp;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Validates compound RTCP datagrams and extracts sender reports and BYE sources. */
public final class RtcpParser {
    private RtcpParser() {}

    public static final class Report {
        public final long ssrc;
        public final boolean bye;
        public final long ntpTimestampMs;
        public final long rtpTimestamp;

        private Report(long ssrc, boolean bye, long ntpTimestampMs, long rtpTimestamp) {
            this.ssrc = ssrc;
            this.bye = bye;
            this.ntpTimestampMs = ntpTimestampMs;
            this.rtpTimestamp = rtpTimestamp;
        }
    }

    public static boolean isRtcp(byte[] data, int length) {
        return length >= 2 && (data[1] & 0xff) >= 192 && (data[1] & 0xff) <= 223;
    }

    @Nullable
    public static List<Report> parsePacket(byte[] data, int length) {
        if (length < 4 || length > data.length) return null;
        List<Report> reports = new ArrayList<>();
        int offset = 0;
        while (offset < length) {
            if (length - offset < 4 || (data[offset] & 0xc0) != 0x80) return null;
            int type = data[offset + 1] & 0xff;
            if (type < 192 || type > 223) return null;
            int count = data[offset] & 0x1f;
            int packetLength = ((((data[offset + 2] & 0xff) << 8)
                    | (data[offset + 3] & 0xff)) + 1) * 4;
            if (packetLength > length - offset) return null;
            int bodyLength = packetLength;
            if ((data[offset] & 0x20) != 0) {
                int padding = data[offset + packetLength - 1] & 0xff;
                if (offset + packetLength != length || padding == 0 || padding > packetLength - 4) return null;
                bodyLength -= padding;
            }
            if (type == 200) {
                if (bodyLength < 28 + count * 24) return null;
                long ntpMs = unsignedInt(data, offset + 8) * 1000
                        + (unsignedInt(data, offset + 12) * 1000 >>> 32);
                reports.add(new Report(unsignedInt(data, offset + 4), false, ntpMs,
                        unsignedInt(data, offset + 16)));
            } else if (type == 201 && bodyLength < 8 + count * 24) {
                return null;
            } else if (type == 203) {
                if (bodyLength < 4 + count * 4) return null;
                for (int i = 0; i < count; i++) {
                    reports.add(new Report(unsignedInt(data, offset + 4 + i * 4), true, 0, 0));
                }
            }
            offset += packetLength;
        }
        return reports;
    }

    private static long unsignedInt(byte[] data, int offset) {
        return ((long) (data[offset] & 0xff) << 24) | ((long) (data[offset + 1] & 0xff) << 16)
                | ((long) (data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
    }
}
