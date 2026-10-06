package com.fivesoft.qplayer.impl.extractor.rtp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Builds RFC 3550 compound receiver reports with the required SDES CNAME. */
final class RtcpReportBuilder {
    private RtcpReportBuilder() {}

    static byte[] receiverReport(int receiverSsrc, List<Block> blocks) {
        int count = Math.min(31, blocks.size());
        byte[] cname = ("qplayer-" + Integer.toHexString(receiverSsrc)).getBytes(StandardCharsets.UTF_8);
        int sdesSize = (4 + 4 + 2 + cname.length + 1 + 3) & ~3;
        ByteBuffer data = ByteBuffer.allocate(8 + count * 24 + sdesSize);
        data.put((byte) (0x80 | count)).put((byte) 201).putShort((short) (1 + count * 6)).putInt(receiverSsrc);
        for (int i = 0; i < count; i++) {
            Block block = blocks.get(i);
            int loss = (int) Math.max(-0x800000, Math.min(0x7fffff, block.lost));
            data.putInt((int) block.ssrc).put((byte) block.fractionLost)
                    .put((byte) (loss >> 16)).put((byte) (loss >> 8)).put((byte) loss)
                    .putInt((int) block.highestSequence).putInt((int) block.jitter)
                    .putInt((int) block.lastSenderReport).putInt((int) block.delaySinceReport);
        }
        data.put((byte) 0x81).put((byte) 202).putShort((short) (sdesSize / 4 - 1))
                .putInt(receiverSsrc).put((byte) 1).put((byte) cname.length).put(cname).put((byte) 0);
        return data.array();
    }

    static byte[] bye(int receiverSsrc) {
        return ByteBuffer.allocate(8).put((byte) 0x81).put((byte) 203).putShort((short) 1)
                .putInt(receiverSsrc).array();
    }

    static final class Block {
        long ssrc;
        int fractionLost;
        long lost;
        long highestSequence;
        long jitter;
        long lastSenderReport;
        long delaySinceReport;
    }
}
