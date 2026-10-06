package com.fivesoft.qplayer.impl.decoder.video.h264

import com.fivesoft.qplayer.base.Frame
import com.fivesoft.qplayer.base.Sample
import com.fivesoft.qplayer.base.track.VideoTrack
import com.fivesoft.qplayer.core.FrameBuilder
import com.fivesoft.qplayer.impl.extractor.rtp.RtpParser
import com.fivesoft.qplayer.impl.extractor.rtp.RtpSample
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RtpH264FrameBuilderTest {

    private val track = VideoTrack("video", 96)

    @Test
    fun stapAProducesEachNalUnitInOrder() {
        val packet = bytes(
            0x78,
            0x00, 0x03, 0x67, 0x64, 0x00,
            0x00, 0x02, 0x68, 0xee,
            0x00, 0x03, 0x65, 0x11, 0x22
        )

        val frames = requireNotNull(
            RtpH264FrameBuilder().pull(Sample(packet, 1234L, track))
        )

        assertEquals(3, frames.size)
        assertFrame(frames[0], Frame.CONFIG_FRAME, 0x67, 0x64, 0x00)
        assertFrame(frames[1], Frame.CONFIG_FRAME, 0x68, 0xee)
        assertFrame(frames[2], Frame.SYNC_FRAME, 0x65, 0x11, 0x22)
        frames.forEach { assertEquals(1234L, it.timestamp) }
    }

    @Test
    fun malformedStapAIsDiscarded() {
        val packet = bytes(0x78, 0x00, 0x04, 0x67, 0x64)

        val frames = RtpH264FrameBuilder().pull(Sample(packet, 0L, track))

        assertNull(frames)
    }

    private fun assertFrame(frame: Frame, type: Byte, vararg nalUnit: Int) {
        assertEquals(type, frame.frameType)
        assertArrayEquals(bytes(0x00, 0x00, 0x00, 0x01, *nalUnit), frame.array)
    }

    @Test
    fun fragmentedNalSurvivesSequenceWrapAndUsesFirstTimestamp() {
        val builder = RtpH264FrameBuilder()
        assertNull(builder.pull(rtp(65535, 100, 0x7c, 0x85, 0x11)))
        assertNull(builder.pull(rtp(0, 100, 0x7c, 0x05, 0x22)))
        val frame = builder.pull(rtp(1, 100, 0x7c, 0x45, 0x33))!![0]
        assertFrame(frame, Frame.SYNC_FRAME, 0x65, 0x11, 0x22, 0x33)
        assertEquals(100L, frame.timestamp)
        assertEquals(0, builder.bufferSize)
    }

    @Test
    fun dropsLostFragmentsTimestampChangesAndUnsupportedPacketTypes() {
        val builder = RtpH264FrameBuilder()
        assertNull(builder.pull(rtp(1, 100, 0x7c, 0x85, 0x11)))
        assertNull(builder.pull(rtp(3, 100, 0x7c, 0x45, 0x33)))
        assertNull(builder.pull(rtp(4, 200, 0x7c, 0x85, 0x11)))
        assertNull(builder.pull(rtp(5, 300, 0x7c, 0x45, 0x33)))
        assertNull(builder.pull(rtp(6, 300, 0x79, 0x11)))
        assertNull(builder.pull(rtp(7, 300, 0x7c, 0xc5, 0x11)))
        assertEquals(0, builder.bufferSize)
    }

    @Test
    fun boundsSingleNalAndFragmentBuffers() {
        assertThrows(FrameBuilder.BufferOverflowException::class.java) {
            RtpH264FrameBuilder(5).pull(Sample(bytes(0x65, 1), 0, track))
        }
        val builder = RtpH264FrameBuilder(6)
        assertNull(builder.pull(rtp(1, 100, 0x7c, 0x85, 0x11)))
        assertThrows(FrameBuilder.BufferOverflowException::class.java) {
            builder.pull(rtp(2, 100, 0x7c, 0x05, 0x22))
        }
        assertEquals(0, builder.bufferSize)
    }

    private fun rtp(sequence: Int, timestamp: Long, vararg payload: Int): RtpSample {
        val header = RtpParser.RtpHeader().apply {
            sequenceNumber = sequence
            this.timestamp = timestamp
            ssrc = 1
        }
        val data = bytes(*payload)
        return RtpSample(data, 0, data.size, timestamp, track, header, false)
    }

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].toByte() }
}
