package com.fivesoft.qplayer.impl.extractor.rtp

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class RtcpReportBuilderTest {
    @Test
    fun receiverReportContainsLossJitterTimingAndSdesCname() {
        val block = RtcpReportBuilder.Block().apply {
            ssrc = 123
            fractionLost = 64
            lost = 2
            highestSequence = 65537
            jitter = 77
            lastSenderReport = 0x12345678
            delaySinceReport = 65536
        }
        val data = RtcpReportBuilder.receiverReport(456, listOf(block))
        val report = ByteBuffer.wrap(data)
        assertEquals(0x81, report.get().toInt() and 255)
        assertEquals(201, report.get().toInt() and 255)
        assertEquals(7, report.short.toInt())
        assertEquals(456, report.int)
        assertEquals(123, report.int)
        assertEquals(64, report.get().toInt() and 255)
        assertEquals(0, report.get().toInt())
        assertEquals(0, report.get().toInt())
        assertEquals(2, report.get().toInt())
        assertEquals(65537, report.int)
        assertEquals(77, report.int)
        assertEquals(0x12345678, report.int)
        assertEquals(65536, report.int)
        assertEquals(0x81, report.get().toInt() and 255)
        assertEquals(202, report.get().toInt() and 255)
        report.short
        assertEquals(456, report.int)
        assertEquals(1, report.get().toInt())
        val cname = ByteArray(report.get().toInt() and 255)
        report.get(cname)
        assertEquals("qplayer-1c8", String(cname, Charsets.UTF_8))
        assertNotNull(RtcpParser.parsePacket(data, data.size))
        val bye = RtcpReportBuilder.bye(456)
        assertTrue(RtcpParser.parsePacket(bye, bye.size)!![0].bye)
    }
}
