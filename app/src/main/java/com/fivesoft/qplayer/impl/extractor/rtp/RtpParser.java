package com.fivesoft.qplayer.impl.extractor.rtp;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.common.NetUtils;
import com.fivesoft.qplayer.common.Util;

import java.io.IOException;
import java.io.InputStream;

public class RtpParser {

    private static final String TAG = RtpParser.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final static int RTP_HEADER_SIZE = 12;

    public static class RtpHeader {

        public int version;
        public int padding;
        public int extension;
        public int cc;
        public int marker;
        public int payloadType;
        public int sequenceNumber;
        public long timestamp;
        public long ssrc;
        public int payloadSize;

        // If RTP header found, return 4 bytes of the header
        private static boolean searchForNextRtpHeader(@NonNull InputStream inputStream, @NonNull byte[] header /*out*/) throws IOException {
            if (header.length < 4)
                throw new IOException("Invalid allocated buffer size");

            int bytesRemaining = 100000; // 100 KB max to check
            boolean foundFirstByte = false;
            boolean foundSecondByte = false;
            byte[] oneByte = new byte[1];
            // Search for {0x24, 0x00}
            do {
                Util.checkInterrupted();

                if (bytesRemaining-- < 0)
                    return false;
                // Read 1 byte
                NetUtils.readData(inputStream, oneByte, 0, 1);
                if (foundFirstByte) {
                    // Interleaved channels are one byte. A conservative range avoids
                    // mistaking a '$' inside an RTSP text response for a packet marker.
                    if ((oneByte[0] & 0xFF) <= 31)
                        foundSecondByte = true;
                    else
                        foundFirstByte = false;
                }
                if (!foundFirstByte && oneByte[0] == 0x24) {
                    // Found 0x24
                    foundFirstByte = true;
                }
            } while (!foundSecondByte);
            header[0] = 0x24;
            header[1] = oneByte[0];
            // Read 2 bytes more (packet size)
            NetUtils.readData(inputStream, header, 2, 2);
            return true;
        }

        @Nullable
        private static RtpHeader parseData(@NonNull byte[] header, int packetSize) {
            RtpHeader rtpHeader = new RtpHeader();
            rtpHeader.version = (header[0] & 0xFF) >> 6;
            if (rtpHeader.version != 2) {
                if (DEBUG)
                    Log.e(TAG,"Not a RTP packet (" + rtpHeader.version + ")");
                return null;
            }

            // 80 60 40 91 fd ab d4 2a
            // 80 c8 00 06
            rtpHeader.padding = (header[0] & 0x20) >> 5; // 0b00100100
            rtpHeader.extension = (header[0] & 0x10) >> 4;
            rtpHeader.cc = header[0] & 0x0F;
            rtpHeader.marker = (header[1] & 0x80) >> 7;
            rtpHeader.payloadType = header[1] & 0x7F;
            rtpHeader.sequenceNumber = (header[3] & 0xFF) + ((header[2] & 0xFF) << 8);
            rtpHeader.timestamp = (header[7] & 0xFF) + ((header[6] & 0xFF) << 8) + ((header[5] & 0xFF) << 16) + ((long) (header[4] & 0xFF) << 24) & 0xffffffffL;
            rtpHeader.ssrc = (header[11] & 0xFF) + ((header[10] & 0xFF) << 8) + ((header[9] & 0xFF) << 16) + ((long) (header[8] & 0xFF) << 24) & 0xffffffffL;
            rtpHeader.payloadSize = packetSize - RTP_HEADER_SIZE;
            return rtpHeader;
        }

        private static int getPacketSize(@NonNull byte[] header) {
            int packetSize = ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
            if (DEBUG)
                Log.d(TAG, "Packet size: " + packetSize);
            return packetSize;
        }

        public void dumpHeader() {
            Log.d("RTP","RTP header version: " + version
                    + ", padding: " + padding
                    + ", ext: " + extension
                    + ", cc: " + cc
                    + ", marker: " + marker
                    + ", payload type: " + payloadType
                    + ", seq num: " + sequenceNumber
                    + ", ts: " + timestamp
                    + ", ssrc: " + ssrc
                    + ", payload size: " + payloadSize);
        }
    }

    @Nullable
    public static RtpHeader readHeader(@NonNull InputStream inputStream) throws IOException {
        // 24 01 00 1c 80 c8 00 06  7f 1d d2 c4
        // 24 01 00 1c 80 c8 00 06  13 9b cf 60
        // 24 02 01 12 80 e1 01 d2  00 07 43 f0
        byte[] header = new byte[RTP_HEADER_SIZE];
        // Skip 4 bytes (TCP only). No those bytes in UDP.
        NetUtils.readData(inputStream, header, 0, 4);
        if (DEBUG && header[0] == 0x24)
            Log.d(TAG, header[1] == 0 ? "RTP packet" : "RTCP packet");

        int packetSize = RtpHeader.getPacketSize(header);
        if (packetSize < RTP_HEADER_SIZE) {
            return null;
        }
        if (DEBUG)
            Log.d(TAG, "Packet size: " + packetSize);

        if (NetUtils.readData(inputStream, header, 0, header.length) == header.length) {
            RtpHeader rtpHeader = RtpHeader.parseData(header, packetSize);
            if (rtpHeader == null) {
                // Header not found. Possible keep-alive response. Search for another RTP header.
                boolean foundHeader = RtpHeader.searchForNextRtpHeader(inputStream, header);
                if (foundHeader) {
                    packetSize = RtpHeader.getPacketSize(header);
                    if (packetSize >= RTP_HEADER_SIZE &&
                            NetUtils.readData(inputStream, header, 0, header.length) == header.length) {
                        rtpHeader = RtpHeader.parseData(header, packetSize);
                        return rtpHeader == null ? null : consumeVariableHeader(inputStream, rtpHeader);
                    }
                }
            } else {
                return consumeVariableHeader(inputStream, rtpHeader);
            }
        }
        return null;
    }

    @Nullable
    private static RtpHeader consumeVariableHeader(@NonNull InputStream inputStream,
                                                   @NonNull RtpHeader header) throws IOException {
        int csrcBytes = header.cc * 4;
        if (!discard(inputStream, csrcBytes)) {
            return null;
        }
        header.payloadSize -= csrcBytes;

        if (header.extension != 0) {
            byte[] extensionHeader = new byte[4];
            if (NetUtils.readData(inputStream, extensionHeader, 0, extensionHeader.length) != extensionHeader.length) {
                return null;
            }
            int extensionBytes = (((extensionHeader[2] & 0xFF) << 8) |
                    (extensionHeader[3] & 0xFF)) * 4;
            header.payloadSize -= extensionHeader.length + extensionBytes;
            if (header.payloadSize < 0 || !discard(inputStream, extensionBytes)) {
                return null;
            }
        }

        return header.payloadSize < 0 ? null : header;
    }

    private static boolean discard(@NonNull InputStream inputStream, int length) throws IOException {
        if (length <= 0) {
            return true;
        }
        byte[] discardBuffer = new byte[Math.min(length, 1024)];
        int remaining = length;
        while (remaining > 0) {
            int toRead = Math.min(remaining, discardBuffer.length);
            if (NetUtils.readData(inputStream, discardBuffer, 0, toRead) != toRead) {
                return false;
            }
            remaining -= toRead;
        }
        return true;
    }
}
