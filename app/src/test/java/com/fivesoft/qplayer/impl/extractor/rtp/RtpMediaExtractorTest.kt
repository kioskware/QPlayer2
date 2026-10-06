package com.fivesoft.qplayer.impl.extractor.rtp

import com.fivesoft.qplayer.base.components.DatagramDataSource
import com.fivesoft.qplayer.base.components.MediaDecoder
import com.fivesoft.qplayer.base.components.MediaExtractor
import com.fivesoft.qplayer.common.TimeoutException
import com.fivesoft.qplayer.common.TrackSelector
import com.fivesoft.qplayer.core.resolvers.DataSourceResolver
import com.fivesoft.qplayer.core.resolvers.MediaExtractorResolver
import com.fivesoft.qplayer.impl.decoder.video.h264.RtpH264FrameBuilder
import com.fivesoft.qplayer.impl.source.UdpDataSource
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.ByteBuffer
import java.util.ArrayDeque

class RtpMediaExtractorTest {
    @Test
    fun reordersAndDropsDuplicatesAcrossSequenceAndTimestampWrap() {
        val source = Packets()
        val extractor = extractor(source)
        val start = 0xffffff00L
        source.add(rtp(65535, start))
        assertEquals(0L, extractor.nextSample()!!.timestamp)
        source.add(rtp(1, (start + 180000) and 0xffffffffL))
        source.add(rtp(0, (start + 90000) and 0xffffffffL))
        source.add(rtp(0, (start + 90000) and 0xffffffffL))
        val second = extractor.nextSample() as RtpSample
        val third = extractor.nextSample() as RtpSample
        assertEquals(0, second.sequenceNumber)
        assertEquals(1000L, second.timestamp)
        assertFalse(second.discontinuity)
        assertEquals(1, third.sequenceNumber)
        assertEquals(2000L, third.timestamp)
        source.add(rtp(2, (start + 270000) and 0xffffffffL))
        assertEquals(2, (extractor.nextSample() as RtpSample).sequenceNumber)
        assertEquals(3L, extractor.sampleIndex)
        assertEquals(3000L, extractor.position)
    }

    @Test
    fun releasesMissingPacketsWithinDelayAndMarksTheGap() {
        val source = Packets()
        val extractor = extractor(source, delay = 10)
        source.add(rtp(1, 0))
        extractor.nextSample()
        source.add(rtp(3, 90000))
        val sample = extractor.nextSample() as RtpSample
        assertEquals(3, sample.sequenceNumber)
        assertTrue(sample.discontinuity)
        assertEquals(1000L, sample.timestamp)
    }

    @Test
    fun queueCapacityReleasesGapWithoutWaitingForDelay() {
        val source = Packets()
        val extractor = RtpMediaExtractor(source, URI("rtp://0.0.0.0:5004"))
        extractor.setReorderDelay(10000)
        extractor.setMaxPendingPackets(2)
        extractor.prepare(1000)
        source.add(rtp(10, 0))
        extractor.nextSample()
        source.add(rtp(12, 90000))
        source.add(rtp(13, 180000))
        assertTrue((extractor.nextSample() as RtpSample).discontinuity)
        assertEquals(13, (extractor.nextSample() as RtpSample).sequenceNumber)
    }

    @Test
    fun ignoresMalformedPacketsUnknownPayloadsAndOldSources() {
        val source = Packets()
        val extractor = extractor(source)
        source.add(byteArrayOf(1, 2, 3))
        source.add(rtp(1, 0, payloadType = 97))
        source.add(rtp(20, 90000, ssrc = 1))
        assertEquals(20, (extractor.nextSample() as RtpSample).sequenceNumber)
        source.add(rtp(1, 10, ssrc = 2))
        assertTrue((extractor.nextSample() as RtpSample).discontinuity)
        source.add(rtp(21, 180000, ssrc = 1))
        source.add(rtp(2, 90010, ssrc = 2))
        assertEquals(1000L, extractor.nextSample()!!.timestamp)
    }

    @Test
    fun recoversFromSenderRestartWithTheSameSsrc() {
        val source = Packets()
        val extractor = extractor(source)
        source.add(rtp(10000, 90000))
        extractor.nextSample()
        source.add(rtp(1, 0))
        source.add(rtp(2, 9000))
        val restarted = extractor.nextSample() as RtpSample
        assertEquals(2, restarted.sequenceNumber)
        assertTrue(restarted.discontinuity)
        source.add(rtp(3, 99000))
        assertEquals(1000L, extractor.nextSample()!!.timestamp)
    }

    @Test
    fun senderReportsSynchronizeTracksWithDifferentClocks() {
        val tracks = RtpTrackConfig.fromSdp("m=video 5004 RTP/AVP 96\na=rtpmap:96 H264/90000\n" +
            "m=audio 5004 RTP/AVP 97\na=rtpmap:97 MPEG4-GENERIC/48000/2\na=fmtp:97 config=1190")
        val source = Packets()
        val extractor = RtpMediaExtractor(source, null, tracks)
        extractor.prepare(1000)
        fun report(ssrc: Int, timestamp: Int) = ByteBuffer.allocate(28)
            .put(0x80.toByte()).put(200.toByte()).putShort(6)
            .putInt(ssrc).putInt(100).putInt(0).putInt(timestamp).putInt(1).putInt(1).array()
        source.add(report(1, 90000))
        source.add(report(2, 48000))
        source.add(rtp(1, 90000, ssrc = 1))
        source.add(rtp(1, 72000, ssrc = 2, payloadType = 97))
        assertEquals(0L, extractor.nextSample()!!.timestamp)
        assertEquals(500L, extractor.nextSample()!!.timestamp)
        assertTrue(source.feedback.any { (it[1].toInt() and 255) == 201 })
        extractor.close()
        assertEquals(203, source.feedback.last()[1].toInt() and 255)
    }

    @Test
    fun selectedTrackUsesItsOwnClockAndBuffersAreNotOverwritten() {
        val tracks = RtpTrackConfig.fromSdp("m=video 5004 RTP/AVP 96\na=rtpmap:96 H264/90000\n" +
            "m=audio 5004 RTP/AVP 97\na=rtpmap:97 MPEG4-GENERIC/48000/2\na=fmtp:97 config=1190")
        val source = Packets()
        val extractor = RtpMediaExtractor(source, TrackSelector.AUDIO, tracks)
        extractor.prepare(1000)
        source.add(rtp(1, 0, payloadType = 96))
        source.add(rtp(1, 48000, payloadType = 97, payload = byteArrayOf(1, 2)))
        val first = extractor.nextSample()!!
        assertEquals(0, first.offset)
        extractor.setAllowReuseBuffers(true)
        source.add(rtp(2, 96000, payloadType = 97, payload = byteArrayOf(3, 4)))
        val second = extractor.nextSample()!!
        assertEquals(1000L, second.timestamp)
        assertEquals(12, second.offset)
        assertArrayEquals(byteArrayOf(1, 2), first.array)
    }

    @Test
    fun readsSenderReportsAndRetiresByeSources() {
        val source = Packets()
        val extractor = extractor(source)
        source.add(rtp(1, 0))
        val track = extractor.nextSample()!!.track
        source.add(ByteBuffer.allocate(28).put(0x80.toByte()).put(200.toByte()).putShort(6)
            .putInt(1).putInt(100).putInt(Int.MIN_VALUE).putInt(90000).putInt(1).putInt(1).array())
        source.add(rtp(2, 90000))
        extractor.nextSample()
        assertEquals(100500L, track.getMetadata("rtcp-ntp-timestamp-ms"))
        assertEquals(90000L, track.getMetadata("rtcp-rtp-timestamp"))
        source.add(ByteBuffer.allocate(8).put(0x81.toByte()).put(203.toByte()).putShort(1).putInt(1).array())
        source.add(rtp(3, 180000))
        source.add(rtp(1, 0, ssrc = 2))
        assertTrue((extractor.nextSample() as RtpSample).discontinuity)
    }

    @Test
    fun timeoutAndLifecycleFollowExtractorOwnershipContract() {
        val source = Packets()
        val extractor = RtpMediaExtractor(source, URI("rtp://0.0.0.0:5004"))
        assertThrows(IllegalStateException::class.java) { extractor.nextSample() }
        assertThrows(IllegalStateException::class.java) { extractor.tracks }
        extractor.prepare(1000)
        val tracks = extractor.tracks
        extractor.timeout = 10
        assertThrows(TimeoutException::class.java) { extractor.nextSample() }
        assertThrows(IllegalStateException::class.java) { extractor.prepare(1000) }
        extractor.close()
        assertTrue(source.isConnected)
        assertFalse(extractor.isPrepared)
        extractor.prepare(1000)
        assertSame(tracks, extractor.tracks)
        assertEquals(-1L, extractor.sampleIndex)
        assertEquals(MediaDecoder.FORMAT_RTP, extractor.sampleFormat)
    }

    @Test
    fun resolverAndRealUdpFeedExistingH264DecoderPipeline() {
        DataSourceResolver.getInstance().registerCreator(UdpDataSource.CREATOR)
        MediaExtractorResolver.getInstance().registerCreator(RtpMediaExtractor.CREATOR)
        UdpDataSource("127.0.0.1", 0).use { source ->
            source.connect()
            val uri = URI("rtp://127.0.0.1:${source.localPort}")
            val resolvedSource = DataSourceResolver.resolveSource(uri)
            assertTrue(resolvedSource is UdpDataSource)
            val extractor = MediaExtractorResolver.resolveExtractor(MediaExtractor.Descriptor(source, null, uri))!!
            extractor.prepare(1000)
            val builder = RtpH264FrameBuilder()
            DatagramSocket().use { sender ->
                fun send(sequence: Int, vararg payload: Int) {
                    val data = rtp(sequence, 90000, payload = ByteArray(payload.size) { payload[it].toByte() })
                    sender.send(DatagramPacket(data, data.size, InetAddress.getLoopbackAddress(), source.localPort))
                }
                send(10, 0x7c, 0x85, 0x11)
                assertNull(builder.pull(extractor.nextSample()!!))
                send(12, 0x7c, 0x45, 0x33)
                send(11, 0x7c, 0x05, 0x22)
                assertNull(builder.pull(extractor.nextSample()!!))
                val frame = builder.pull(extractor.nextSample()!!)!![0]
                assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x65, 0x11, 0x22, 0x33), frame.array)
            }
        }
    }

    private fun extractor(source: Packets, delay: Int = 50): RtpMediaExtractor =
        RtpMediaExtractor(source, URI("rtp://0.0.0.0:5004")).apply {
            setReorderDelay(delay)
            prepare(1000)
        }

    private fun rtp(sequence: Int, timestamp: Long, ssrc: Int = 1, payloadType: Int = 96,
                    payload: ByteArray = byteArrayOf(0x65, 0x11)): ByteArray =
        ByteBuffer.allocate(12 + payload.size).put(0x80.toByte()).put(payloadType.toByte())
            .putShort(sequence.toShort()).putInt(timestamp.toInt()).putInt(ssrc).put(payload).array()

    private class Packets : DatagramDataSource() {
        private val packets = ArrayDeque<ByteArray>()
        val feedback = mutableListOf<ByteArray>()
        fun add(packet: ByteArray) { packets.add(packet) }
        override fun readDatagram(buffer: ByteArray, offset: Int, length: Int, timeout: Int): Int {
            val packet = packets.poll() ?: throw SocketTimeoutException()
            packet.copyInto(buffer, offset)
            return packet.size
        }
        override fun connect(timeout: Int) {}
        override fun isConnected() = true
        override fun getLength() = UNKNOWN_LENGTH
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun isOutSupported() = false
        override fun getOutputStream() = null
        override fun onTimeoutSet(timeout: Int) {}
        override fun close() {}
        override fun sendControlDatagram(data: ByteArray): Boolean {
            feedback.add(data)
            return true
        }
    }
}
