package com.fivesoft.qplayer.impl.decoder.audio.aac

import com.fivesoft.qplayer.base.Sample
import com.fivesoft.qplayer.base.track.AudioTrack
import com.fivesoft.qplayer.core.FrameBuilder
import com.fivesoft.qplayer.impl.extractor.rtp.RtpParser
import com.fivesoft.qplayer.impl.extractor.rtp.RtpSample
import org.junit.Assert.*
import org.junit.Test

class RtpAACFrameBuilderTest {
    private val track = AudioTrack("audio", 97, 2, "AAC-hbr").apply { clockRate = 48000 }

    @Test
    fun parsesSingleAndMultipleAccessUnitsAtNonZeroOffset() {
        val builder = RtpAACFrameBuilder("AAC-hbr")
        val one = bytes(0xff, 0, 16, 0, 24, 0x11, 0x22, 0x33, 0xff)
        assertArrayEquals(bytes(0x11, 0x22, 0x33), builder.pull(Sample(one, 1, 7, 100, track))!![0].array)
        val multiple = bytes(0, 32, 0, 16, 0, 24, 0x11, 0x22, 0x33, 0x44, 0x55)
        val frames = builder.pull(Sample(multiple, 100, track))!!
        assertEquals(2, frames.size)
        assertArrayEquals(bytes(0x11, 0x22), frames[0].array)
        assertArrayEquals(bytes(0x33, 0x44, 0x55), frames[1].array)
        assertEquals(100L, frames[0].timestamp)
        assertEquals(121L, frames[1].timestamp)
    }

    @Test
    fun assemblesFragmentsAndRejectsLossWithRecovery() {
        val builder = RtpAACFrameBuilder("AAC-hbr")
        assertNull(builder.pull(rtp(65535, false, bytes(0, 16, 0, 32, 0x11, 0x22))))
        assertEquals(2, builder.bufferSize)
        val frame = builder.pull(rtp(0, true, bytes(0, 16, 0, 32, 0x33, 0x44)))!![0]
        assertArrayEquals(bytes(0x11, 0x22, 0x33, 0x44), frame.array)
        assertEquals(0, builder.bufferSize)
        assertNull(builder.pull(rtp(10, false, bytes(0, 16, 0, 32, 1, 2))))
        assertNull(builder.pull(rtp(12, true, bytes(0, 16, 0, 32, 3, 4))))
        assertEquals(0, builder.bufferSize)
        assertNotNull(builder.pull(rtp(13, true, bytes(0, 16, 0, 16, 5, 6))))
    }

    @Test
    fun supportsLbrAndExplicitHeaderLengths() {
        val lbr = RtpAACFrameBuilder("AAC-lbr")
        assertArrayEquals(bytes(1, 2, 3), lbr.pull(Sample(bytes(0, 8, 12, 1, 2, 3), 0, track))!![0].array)
        track.setMetadata("sizelength", "8")
        track.setMetadata("indexlength", "0")
        track.setMetadata("indexdeltalength", "0")
        val frames = RtpAACFrameBuilder("AAC-hbr").pull(Sample(bytes(0, 16, 2, 3, 1, 2, 3, 4, 5), 0, track))!!
        assertEquals(2, frames.size)
    }

    @Test
    fun rejectsMalformedHeadersAndEnforcesMaximumFrameSize() {
        val builder = RtpAACFrameBuilder("AAC-hbr")
        for (payload in listOf(bytes(), bytes(0), bytes(0, 0), bytes(0, 16, 0),
            bytes(0, 15, 0, 24, 1, 2, 3), bytes(0, 16, 0, 0))) {
            assertNull(builder.pull(Sample(payload, 0, track)))
        }
        assertThrows(FrameBuilder.BufferOverflowException::class.java) {
            RtpAACFrameBuilder(2, "AAC-hbr").pull(Sample(bytes(0, 16, 0, 24, 1, 2, 3), 0, track))
        }
        assertArrayEquals(bytes(1, 2, 3), builder.processRtpPacketAndGetSample(bytes(99, 0, 16, 0, 24, 1, 2, 3), 7, 1))
    }

    private fun rtp(sequence: Int, marker: Boolean, data: ByteArray): RtpSample {
        val header = RtpParser.RtpHeader().apply {
            sequenceNumber = sequence
            timestamp = 1000
            ssrc = 1
            this.marker = if (marker) 1 else 0
        }
        return RtpSample(data, 0, data.size, 100, track, header, false)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
