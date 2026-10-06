package com.fivesoft.qplayer.impl.extractor.rtp

import com.fivesoft.qplayer.base.track.AudioTrack
import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLEncoder

class RtpTrackConfigTest {
    @Test
    fun defaultUriDescribesH264AndExplicitUriDescribesAac() {
        val video = RtpTrackConfig.fromUri(URI("rtp://0.0.0.0:5004")).videoTrack!!
        assertEquals("h264", video.format)
        assertEquals(96, video.payloadType)
        assertEquals(90000, video.clockRate)
        val audio = RtpTrackConfig.fromUri(URI(
            "rtp://0.0.0.0:5006?codec=aac&payload-type=97&clock-rate=48000&channels=2&config=1190"
        )).audioTrack!!
        assertEquals("AAC-hbr", audio.mode)
        assertEquals(48000, audio.clockRate)
        assertArrayEquals(byteArrayOf(0x11, 0x90.toByte()), audio.csd.getCsd(0))
    }

    @Test
    fun sdpAssociatesRtpmapAndFmtpWithTheirPayloadsRegardlessOfOrder() {
        val sdp = """
            v=0
            m=video 5004 RTP/AVP 98 99
            a=fmtp:98 packetization-mode=1;profile-level-id=42e01f
            a=rtpmap:99 VP8/90000
            a=rtpmap:98 H264/90000
            m=audio 5004 RTP/AVP 100
            a=fmtp:100 mode=AAC-hbr;config=1210;sizeLength=13;indexLength=3;indexDeltaLength=3
            a=rtpmap:100 MPEG4-GENERIC/44100/2
        """.trimIndent()
        val tracks = RtpTrackConfig.fromSdp(sdp)
        assertEquals(2, tracks.size())
        assertEquals("1", tracks.getByPayloadType(98)!!.getMetadata("packetization-mode"))
        assertNull(tracks.getByPayloadType(99))
        assertEquals(2, (tracks.getByPayloadType(100) as AudioTrack).channels)
        val uri = URI("rtp://0.0.0.0:5004?sdp=" + URLEncoder.encode(sdp, "UTF-8"))
        assertEquals(2, RtpTrackConfig.fromUri(uri).size())
    }

    @Test
    fun rejectsUnsupportedOrAmbiguousConfigurations() {
        for (query in listOf("codec=h265", "codec=aac", "payload-type=200", "clock-rate=0",
            "payload-type=72", "packetization-mode=2", "payload-type=96&payload-type=97",
            "codec=aac&config=xyz", "codec=aac&config=1210&mode=CELP")) {
            assertThrows(IllegalArgumentException::class.java) {
                RtpTrackConfig.fromUri(URI("rtp://0.0.0.0:5004?$query"))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RtpTrackConfig.fromSdp("m=video 5004 RTP/AVP 96\na=rtpmap:96 H264/90000\n" +
                "m=video 5006 RTP/AVP 96\na=rtpmap:96 H264/90000")
        }
    }
}
