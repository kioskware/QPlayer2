package com.fivesoft.qplayer.impl.decoder.video.h264

import com.fivesoft.qplayer.base.Frame
import com.fivesoft.qplayer.base.Sample
import com.fivesoft.qplayer.base.track.VideoTrack
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].toByte() }
}
