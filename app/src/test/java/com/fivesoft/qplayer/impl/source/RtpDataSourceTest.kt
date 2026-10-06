package com.fivesoft.qplayer.impl.source

import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI

class RtpDataSourceTest {
    @Test
    fun receivesRtpAndRtcpOnSeparatePortsAndReturnsFeedbackToTheControlSender() {
        RtpDataSource("127.0.0.1", 0, 0).use { source ->
            source.connect()
            assertTrue(source.isConnected)
            assertNotEquals(source.localPort, source.rtcpLocalPort)
            assertThrows(IllegalStateException::class.java) { source.connect() }
            DatagramSocket().use { mediaSender ->
                DatagramSocket().use { controlSender ->
                    val media = byteArrayOf(1, 2, 3)
                    val control = byteArrayOf(4, 5, 6)
                    send(mediaSender, source.localPort, media)
                    send(controlSender, source.rtcpLocalPort, control)
                    val buffer = ByteArray(100)
                    assertEquals(3, source.readDatagram(buffer, 0, 100, 1000))
                    assertArrayEquals(control, buffer.copyOf(3))
                    assertEquals(3, source.readDatagram(buffer, 0, 100, 1000))
                    assertArrayEquals(media, buffer.copyOf(3))
                    assertTrue(source.sendControlDatagram(byteArrayOf(9, 8)))
                    controlSender.soTimeout = 1000
                    val reply = DatagramPacket(buffer, buffer.size)
                    controlSender.receive(reply)
                    assertEquals(source.rtcpLocalPort, reply.port)
                    assertArrayEquals(byteArrayOf(9, 8), buffer.copyOf(reply.length))
                }
            }
            source.close()
            assertFalse(source.isConnected)
            source.connect()
            assertTrue(source.isConnected)
        }
    }

    @Test
    fun muxUsesTheSameSocketForMediaControlAndFeedback() {
        RtpDataSource(URI("rtp://127.0.0.1:0?rtcp-mux=true")).use { source ->
            source.connect()
            assertEquals(source.localPort, source.rtcpLocalPort)
            DatagramSocket().use { sender ->
                send(sender, source.localPort, byteArrayOf(1, 2))
                val buffer = ByteArray(100)
                source.readDatagram(buffer, 0, 100, 1000)
                assertTrue(source.sendControlDatagram(byteArrayOf(3, 4)))
                sender.soTimeout = 1000
                val reply = DatagramPacket(buffer, buffer.size)
                sender.receive(reply)
                assertEquals(source.localPort, reply.port)
                assertArrayEquals(byteArrayOf(3, 4), buffer.copyOf(reply.length))
            }
        }
    }

    @Test
    fun validatesControlPortsAndPrefersRtpCreatorOverGenericUdp() {
        assertEquals(2, RtpDataSource.CREATOR.accept(URI("rtp://127.0.0.1:5004")))
        assertEquals(0, RtpDataSource.CREATOR.accept(URI("udp://127.0.0.1:5004")))
        for (uri in listOf("rtp://127.0.0.1:5004?rtcp-port=5004", "rtp://127.0.0.1:65535",
            "rtp://127.0.0.1:5004?rtcp-port=0", "rtp://127.0.0.1:5004?rtcp-mux=yes")) {
            assertThrows(IllegalArgumentException::class.java) { RtpDataSource(URI(uri)) }
        }
    }

    @Test
    fun failedControlBindReleasesTheMediaSocket() {
        DatagramSocket(0).use { occupied ->
            RtpDataSource("127.0.0.1", 0, occupied.localPort).use { source ->
                assertThrows(java.io.IOException::class.java) { source.connect() }
                assertFalse(source.isConnected)
                assertEquals(-1, source.localPort)
            }
        }
    }

    private fun send(sender: DatagramSocket, port: Int, data: ByteArray) {
        sender.send(DatagramPacket(data, data.size, InetAddress.getLoopbackAddress(), port))
    }
}
