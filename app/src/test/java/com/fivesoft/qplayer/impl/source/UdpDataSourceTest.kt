package com.fivesoft.qplayer.impl.source

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.NetworkInterface
import java.net.Inet4Address
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import org.junit.Assume.assumeNotNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class UdpDataSourceTest {
    @Test
    fun resolverAcceptsRtpAndUdpWithExplicitPorts() {
        assertEquals(1, UdpDataSource.CREATOR.accept(URI("rtp://127.0.0.1:5004")))
        assertEquals(1, UdpDataSource.CREATOR.accept(URI("udp://239.1.2.3:5004")))
        assertEquals(0, UdpDataSource.CREATOR.accept(URI("rtp://127.0.0.1")))
        assertEquals(0, UdpDataSource.CREATOR.accept(URI("rtsp://127.0.0.1:554")))
    }

    @Test
    fun preservesPacketBoundariesAndHandlesLargerSecondPacket() {
        UdpDataSource("127.0.0.1", 0).use { source ->
            source.connect()
            source.timeout = 1000
            assertTrue(source.isConnected)
            assertSame(source.inputStream, source.inputStream)
            assertFalse(source.isOutSupported)
            assertNull(source.outputStream)
            send(source, byteArrayOf(1, 2))
            send(source, byteArrayOf(3, 4, 5, 6))
            val buffer = ByteArray(16)
            assertEquals(2, source.readDatagram(buffer, 1, 15))
            assertEquals(1, buffer[1].toInt())
            assertEquals(4, source.readDatagram(buffer, 0, 16))
            assertEquals(6, buffer[3].toInt())
            source.close()
            assertFalse(source.isConnected)
            source.connect()
            assertTrue(source.isConnected)
        }
    }

    @Test
    fun inputStreamSupportsPartialReadsWithoutMergingPackets() {
        UdpDataSource("127.0.0.1", 0).use { source ->
            source.connect()
            source.timeout = 1000
            send(source, byteArrayOf(0xff.toByte(), 2, 3))
            send(source, byteArrayOf(4, 5))
            val input = source.inputStream
            assertEquals(255, input.read())
            assertEquals(2, input.available())
            val buffer = ByteArray(20)
            assertEquals(2, input.read(buffer))
            assertArrayEquals(byteArrayOf(2, 3), buffer.copyOf(2))
            assertEquals(2, input.read(buffer))
            assertArrayEquals(byteArrayOf(4, 5), buffer.copyOf(2))
        }
    }

    @Test
    fun timesOutAndRejectsTruncation() {
        UdpDataSource("127.0.0.1", 0).use { source ->
            source.connect()
            source.timeout = 30
            assertThrows(SocketTimeoutException::class.java) {
                source.readDatagram(ByteArray(20), 0, 20)
            }
            send(source, ByteArray(30))
            assertThrows(IOException::class.java) { source.readDatagram(ByteArray(2), 0, 2) }
        }
    }

    @Test
    fun interruptAndCloseUnblockUnlimitedReceive() {
        for (interrupt in listOf(true, false)) {
            UdpDataSource("127.0.0.1", 0).use { source ->
                source.connect()
                val started = CountDownLatch(1)
                val finished = CountDownLatch(1)
                val error = AtomicReference<Throwable>()
                val reader = Thread {
                    started.countDown()
                    try { source.readDatagram(ByteArray(100), 0, 100, 0) }
                    catch (caught: Throwable) { error.set(caught) }
                    finally { finished.countDown() }
                }
                reader.start()
                assertTrue(started.await(1, TimeUnit.SECONDS))
                if (interrupt) reader.interrupt() else source.close()
                assertTrue(finished.await(2, TimeUnit.SECONDS))
                if (interrupt) assertTrue(error.get() is InterruptedIOException)
                else assertTrue(error.get() is IOException || error.get() is IllegalStateException)
            }
        }
    }

    @Test
    fun joinsMulticastGroupOnAnExplicitInterface() {
        val network = NetworkInterface.getNetworkInterfaces().toList().firstOrNull {
            it.isUp && it.supportsMulticast() && it.inetAddresses.toList().any { address -> address is Inet4Address }
        }
        assumeNotNull(network)
        MulticastSocketDataSource("239.255.42.42", 0).use { source ->
            source.setNetworkInterface(network)
            source.connect()
            assertTrue(source.isConnected)
            DatagramChannel.open(StandardProtocolFamily.INET).use { sender ->
                sender.setOption(StandardSocketOptions.IP_MULTICAST_IF, network)
                sender.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true)
                val data = byteArrayOf(1, 2, 3, 4)
                sender.send(ByteBuffer.wrap(data), InetSocketAddress("239.255.42.42", source.localPort))
                val buffer = ByteArray(100)
                assertEquals(4, source.readDatagram(buffer, 0, 100, 1500))
                assertArrayEquals(data, buffer.copyOf(4))
                assertEquals(source.localPort, source.multicastDestination!!.port)
            }
        }
    }

    private fun send(source: UdpDataSource, bytes: ByteArray) {
        DatagramSocket().use { socket ->
            socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getLoopbackAddress(), source.localPort))
        }
    }
}
