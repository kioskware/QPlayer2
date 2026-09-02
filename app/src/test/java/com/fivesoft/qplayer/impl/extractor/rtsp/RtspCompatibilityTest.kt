package com.fivesoft.qplayer.impl.extractor.rtsp

import com.fivesoft.qplayer.common.Headers
import com.fivesoft.qplayer.base.track.Track
import com.fivesoft.qplayer.base.track.VideoTrack
import com.fivesoft.qplayer.impl.extractor.rtp.RtpParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class RtspCompatibilityTest {

    @Test
    fun responseParserPreservesHeaderValuesContainingColonsAndLfLineEndings() {
        val response = """
            RTSP/1.0 200 OK
            Content-Base: rtsp://camera.local:8554/live/
            Session: abc123;timeout=45

        """.trimIndent().replace("\r\n", "\n")

        val header = RtspUtil.readResponseHeader(
            ByteArrayInputStream(response.toByteArray(StandardCharsets.ISO_8859_1))
        )

        assertEquals(200, header.code)
        assertEquals("OK", header.message)
        assertEquals("rtsp://camera.local:8554/live/", header.headers.get("Content-Base"))
        assertEquals("abc123", RtspUtil.getSessionId(header.headers.get("Session")))
        assertEquals(45, RtspUtil.getSessionTimeoutSeconds(header.headers.get("Session"), 0))
    }

    @Test
    fun digestAuthenticationSupportsQopAuth() {
        val headers = Headers().apply {
            add(
                "WWW-Authenticate",
                "Digest realm=\"testrealm@host.com\", " +
                    "qop=\"auth,auth-int\", " +
                    "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", " +
                    "opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""
            )
        }
        val challenge = RtspUtil.getHeaderWwwAuthenticateDigestRealmAndNonce(headers)

        assertNotNull(challenge)
        val authorization = RtspUtil.getDigestAuthHeader(
            "Mufasa",
            "Circle Of Life",
            "GET",
            "/dir/index.html",
            challenge!!,
            1,
            "0a4f113b"
        )

        assertNotNull(authorization)
        assertTrue(authorization!!.contains("response=\"6629fae49393a05397450978507c4ef1\""))
        assertTrue(authorization.contains("qop=auth"))
        assertTrue(authorization.contains("nc=00000001"))
    }

    @Test
    fun sessionReadsResponseBodyDeliveredInSmallChunks() {
        val response = (
            "RTSP/1.0 200 OK\r\n" +
                "Content-Length: 5\r\n" +
                "\r\n" +
                "abcde"
            ).toByteArray(StandardCharsets.ISO_8859_1)
        val input = object : ByteArrayInputStream(response) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                return super.read(buffer, offset, minOf(length, 2))
            }
        }
        val session = RtspSession(
            input,
            ByteArrayOutputStream(),
            null,
            "rtsp://camera.local/live",
            null
        )

        val parsed = session.describe()

        assertEquals("abcde", parsed.contentAsText)
    }

    @Test
    fun relativeControlUrisKeepCameraPathAndQuery() {
        assertEquals(
            "rtsp://camera.local/live/trackID=1?channel=2",
            RtspMediaExtractor.resolveControlUri(
                "rtsp://camera.local/live?channel=2",
                "trackID=1"
            )
        )
        assertEquals(
            "rtsp://camera.local/trackID=2?channel=2",
            RtspMediaExtractor.resolveControlUri(
                "rtsp://camera.local/live?channel=2",
                "/trackID=2"
            )
        )
    }

    @Test
    fun rtpParserSkipsCsrcAndHeaderExtension() {
        val packet = byteArrayOf(
            0x24, 0x02, 0x00, 0x1b,
            0x91.toByte(), 0x60, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x02,
            0x01, 0x02, 0x03, 0x04,
            0x10, 0x11, 0x12, 0x13,
            0xBE.toByte(), 0xDE.toByte(), 0x00, 0x01,
            0x20, 0x21, 0x22, 0x23,
            0x30, 0x31, 0x32
        )
        val input = ByteArrayInputStream(packet)

        val header = RtpParser.readHeader(input)

        assertNotNull(header)
        assertEquals(1, header!!.cc)
        assertEquals(1, header.extension)
        assertEquals(3, header.payloadSize)
        assertEquals(listOf(0x30, 0x31, 0x32), input.readBytes().map { it.toInt() and 0xFF })
    }

    @Test
    fun rtpParserSkipsRtcpInterleavedChannel() {
        val rtcpPacket = byteArrayOf(
            0x24, 0x01, 0x00, 0x1c,
            0x80.toByte(), 0xc8.toByte(), 0x00, 0x06
        ) + ByteArray(24)
        val rtpPacket = byteArrayOf(
            0x24, 0x00, 0x00, 0x0d,
            0x80.toByte(), 0x60, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x02,
            0x01, 0x02, 0x03, 0x04,
            0x65
        )
        val input = ByteArrayInputStream(rtcpPacket + rtpPacket)

        val header = RtpParser.readHeader(input)

        assertNotNull(header)
        assertEquals(96, header!!.payloadType)
        assertEquals(1, header.payloadSize)
        assertEquals(0x65, input.read())
    }

    @Test
    fun videoDimensionsAndFrameRateAreReadFromSdpAttributes() {
        val builder = Track.Builder(Track.VIDEO)
            .setId("trackID=1")
            .setPayloadType(96)

        RtspMediaExtractor.parseFrameSize("framesize:96 1920-1080", builder)
        RtspMediaExtractor.parseFrameRate("framerate:29.97", builder)
        val track = builder.build() as VideoTrack

        assertEquals(1920, track.width)
        assertEquals(1080, track.height)
        assertEquals(29.97f, track.fps)
    }
}
