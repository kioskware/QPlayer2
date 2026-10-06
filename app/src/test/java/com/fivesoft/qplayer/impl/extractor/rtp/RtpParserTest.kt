package com.fivesoft.qplayer.impl.extractor.rtp

import org.junit.Assert.*
import org.junit.Test

class RtpParserTest {
    @Test
    fun parsesUnsignedFieldsAndPayloadAtNonZeroOffset() {
        val data = bytes(0, 0, 0x80, 0xe0, 0xff, 0xff, 0xff, 0xff, 0xff, 0xfe,
            0x80, 0, 0, 1, 0x65, 0x11, 0)
        val packet = requireNotNull(RtpParser.parsePacket(data, 2, 14))
        assertEquals(65535, packet.header.sequenceNumber)
        assertEquals(0xfffffffeL, packet.header.timestamp)
        assertEquals(0x80000001L, packet.header.ssrc)
        assertEquals(96, packet.header.payloadType)
        assertEquals(1, packet.header.marker)
        assertEquals(14, packet.payloadOffset)
        assertEquals(2, packet.header.payloadSize)
    }

    @Test
    fun skipsCsrcExtensionAndPadding() {
        val data = bytes(0xb1, 96, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3,
            0, 0, 0, 4, 0xbe, 0xde, 0, 1, 9, 8, 7, 6, 0x65, 0x11, 0, 2)
        val packet = requireNotNull(RtpParser.parsePacket(data, 0, data.size))
        assertEquals(24, packet.payloadOffset)
        assertEquals(2, packet.header.payloadSize)
    }

    @Test
    fun rejectsTruncatedHeadersExtensionsAndInvalidPadding() {
        val valid = bytes(0x80, 96, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3, 0x65)
        assertNull(RtpParser.parsePacket(valid, 0, 11))
        assertNull(RtpParser.parsePacket(valid, -1, valid.size))
        assertNull(RtpParser.parsePacket(valid, 1, valid.size))
        for (firstByte in listOf(0x40, 0x8f, 0x90, 0xa0)) {
            val malformed = valid.copyOf().also { it[0] = firstByte.toByte() }
            assertNull(RtpParser.parsePacket(malformed, 0, malformed.size))
        }
        valid[0] = 0xa0.toByte()
        valid[12] = 0
        assertNull(RtpParser.parsePacket(valid, 0, valid.size))
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
