package com.fivesoft.qplayer.impl.extractor.rtp

import com.fivesoft.qplayer.base.Frame
import com.fivesoft.qplayer.base.components.DatagramDataSource
import com.fivesoft.qplayer.base.components.MediaDecoder
import com.fivesoft.qplayer.base.components.MediaExtractor
import com.fivesoft.qplayer.common.TimeoutException
import com.fivesoft.qplayer.common.TrackSelector
import com.fivesoft.qplayer.common.resolver.MediaExtractorResolver
import com.fivesoft.qplayer.core.FrameBuilder
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData
import com.fivesoft.qplayer.impl.source.RtpDataSource
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.util.ArrayDeque

class RtpTransportStreamMediaExtractorTest {
    private val uri = URI("rtp://239.1.1.1:1234")

    @Test fun resolverAutoDetectsMagewellStyleMp2tAndPreservesPreparedSamples() {
        val source = Packets(TsTestData.rtp(TsTestData.tables() + TsTestData.media()))
        MediaExtractorResolver.getInstance().registerCreator(RtpMediaExtractor.CREATOR)
        val extractor = MediaExtractorResolver.resolveExtractor(MediaExtractor.Descriptor(source, null, uri))!!
        assertTrue(extractor is RtpStreamMediaExtractor)
        extractor.prepare(1000)
        assertEquals(MediaDecoder.FORMAT_RAW, extractor.sampleFormat)
        assertEquals(2, extractor.tracks.size())
        assertEquals(0, extractor.tracks.otherTracks.size)
        val frames = (0..3).map { extractor.nextSample() as Frame }
        assertEquals(listOf("h264", "h264", "h264", "aac"), frames.map { it.track.format })
        assertEquals(Frame.SYNC_FRAME, frames[2].frameType)
        assertSame(frames[2], FrameBuilder.RAW_FRAME_BUILDER.pull(frames[2])!![0])
        assertEquals(3L, extractor.sampleIndex)
        assertEquals(20L, extractor.position)
        extractor.close()
        assertFalse(extractor.isPrepared)
        assertTrue(source.isConnected)
    }

    @Test fun findsTablesInLaterPacketsAndSelectsElementaryAudio() {
        val source = Packets(TsTestData.rtp(TsTestData.packet(8191, byteArrayOf(0))),
            TsTestData.rtp(TsTestData.tables(), sequence = 2),
            TsTestData.rtp(TsTestData.media(), sequence = 3))
        val extractor = RtpStreamMediaExtractor(source, TrackSelector.AUDIO, uri)
        extractor.prepare(1000)
        val audio = extractor.nextSample()!!
        assertEquals("aac", audio.track.format)
        assertEquals(48000, audio.track.clockRate)
        assertArrayEquals(byteArrayOf(0x11, 0x90.toByte()), audio.track.csd.getCsd(0))
    }

    @Test fun supportsDynamicMp2tMappingAndStaticOrDynamicSdp() {
        val sdp = listOf("m=video 1234 RTP/AVP 33",
            "m=video 1234 RTP/AVP 98\na=rtpmap:98 MP2T/90000")
        val cases = listOf("codec=mpegts&payload-type=98" to 98, "payload-type=33" to 33) +
            sdp.mapIndexed { i, value -> "sdp=${URLEncoder.encode(value, "UTF-8")}" to (if (i == 0) 33 else 98) }
        for ((query, pt) in cases) {
            val source = Packets(TsTestData.rtp(TsTestData.tables() + TsTestData.media(), pt = pt))
            val configuredUri = URI("rtp://239.1.1.1:1234?$query")
            val extractor = RtpMediaExtractor.CREATOR.create(MediaExtractor.Descriptor(source, null, configuredUri))!!
            extractor.prepare(1000)
            assertEquals(MediaDecoder.FORMAT_RAW, extractor.sampleFormat)
            assertEquals("h264", extractor.nextSample()!!.track.format)
            extractor.close()
        }
    }

    @Test fun udpRtpTsAndRawDecoderPipelineWorkWithRealSockets() {
        RtpDataSource("127.0.0.1", 0, 0).use { source ->
            source.connect()
            val extractor = RtpStreamMediaExtractor(source, uri)
            DatagramSocket().use { sender ->
                fun send(payload: ByteArray, sequence: Int) {
                    val data = TsTestData.rtp(payload, sequence)
                    sender.send(DatagramPacket(data, data.size, InetAddress.getLoopbackAddress(), source.localPort))
                }
                send(TsTestData.tables(), 1)
                extractor.prepare(1000)
                send(TsTestData.media(), 2)
                val frames = (0..3).map { FrameBuilder.RAW_FRAME_BUILDER.pull(extractor.nextSample()!!)!![0] }
                assertArrayEquals(byteArrayOf(0, 0, 0, 1) + TsTestData.idr, frames[2].array)
                assertEquals("aac", frames[3].track.format)
                extractor.close()
            }
        }
    }

    @Test fun timeoutDoesNotLeaveExtractorPreparedAndAllowsRetry() {
        val source = Packets()
        val extractor = RtpStreamMediaExtractor(source, uri)
        assertThrows(TimeoutException::class.java) { extractor.prepare(10) }
        assertFalse(extractor.isPrepared)
        source.add(TsTestData.rtp(TsTestData.tables() + TsTestData.media()))
        extractor.prepare(1000)
        val tracks = extractor.tracks
        extractor.nextSample()
        extractor.close()
        source.add(TsTestData.rtp(TsTestData.tables() + TsTestData.media()))
        extractor.prepare(1000)
        assertSame(tracks, extractor.tracks)
        assertEquals(-1L, extractor.sampleIndex)
    }

    @Test fun failsExplicitlyWhenProgramOnlyContainsUnsupportedCodecs() {
        val source = Packets(TsTestData.rtp(TsTestData.tables(listOf(0x24 to 256))))
        val extractor = RtpStreamMediaExtractor(source, uri)
        assertThrows(IOException::class.java) { extractor.prepare(1000) }
        assertFalse(extractor.isPrepared)
    }

    @Test fun ssrcChangeCannotCombineNewStreamWithOldPartialPes() {
        val source = Packets(TsTestData.rtp(TsTestData.tables()))
        val extractor = RtpStreamMediaExtractor(source, TrackSelector.AUDIO, uri)
        extractor.prepare(1000)
        source.add(TsTestData.rtp(TsTestData.packet(TsTestData.AUDIO,
            TsTestData.pes(TsTestData.adts().copyOfRange(0, 8), video = false)), sequence = 2))
        source.add(TsTestData.rtp(TsTestData.tables() + TsTestData.media(), ssrc = 2))
        assertArrayEquals(byteArrayOf(0x11, 0x22, 0x33), extractor.nextSample()!!.array)
        assertEquals(1, extractor.tracks.audioTracks.size)
    }

    private class Packets(vararg initial: ByteArray) : DatagramDataSource() {
        private val packets = ArrayDeque<ByteArray>().apply { addAll(initial) }
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
    }
}
