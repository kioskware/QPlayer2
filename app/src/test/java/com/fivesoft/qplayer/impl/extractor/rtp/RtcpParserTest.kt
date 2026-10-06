package com.fivesoft.qplayer.impl.extractor.rtp

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class RtcpParserTest {
    @Test
    fun readsCompoundSenderReportAndByeWithUnsignedSsrc() {
        val report = ByteBuffer.allocate(28).put(0x80.toByte()).put(200.toByte()).putShort(6)
            .putInt(-1).putInt(100).putInt(Int.MIN_VALUE).putInt(-2).putInt(5).putInt(10).array()
        val bye = ByteBuffer.allocate(8).put(0x81.toByte()).put(203.toByte()).putShort(1).putInt(-1).array()
        val reports = requireNotNull(RtcpParser.parsePacket(report + bye, 36))
        assertEquals(2, reports.size)
        assertEquals(0xffffffffL, reports[0].ssrc)
        assertEquals(100500L, reports[0].ntpTimestampMs)
        assertEquals(0xfffffffeL, reports[0].rtpTimestamp)
        assertTrue(reports[1].bye)
    }

    @Test
    fun rejectsTruncatedAndInvalidCompoundReports() {
        assertNull(RtcpParser.parsePacket(byteArrayOf(0x80.toByte(), 200.toByte(), 0, 6), 4))
        assertNull(RtcpParser.parsePacket(byteArrayOf(0x81.toByte(), 203.toByte(), 0, 0), 4))
        assertNull(RtcpParser.parsePacket(byteArrayOf(0x80.toByte(), 201.toByte(), 0, 0), 4))
        assertNull(RtcpParser.parsePacket(ByteArray(3), 3))
    }
}
