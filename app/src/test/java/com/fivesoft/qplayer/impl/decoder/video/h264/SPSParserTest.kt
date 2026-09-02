package com.fivesoft.qplayer.impl.decoder.video.h264

import com.fivesoft.qplayer.base.track.VideoTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

class SPSParserTest {

    @Test
    fun parsesCroppedDimensionsFromAnnexBAtNonZeroOffset() {
        val sps = createSps(width = 1920, height = 1080, profile = 66)
        val data = byteArrayOf(0x55) + byteArrayOf(0x00, 0x00, 0x00, 0x01) + sps

        val params = SPSParser.parseSPSStatic(data, 1, data.size - 1)

        assertEquals(1920, params.width)
        assertEquals(1080, params.height)
        assertEquals(1f, params.pixelAspectRatio)
    }

    @Test
    fun parsesHighProfileEmulationPreventionSarAndFrameRate() {
        val sps = createSps(
            width = 720,
            height = 576,
            profile = 100,
            sarWidth = 16,
            sarHeight = 15,
            frameRate = 25
        )

        assertTrue(sps.containsSequence(byteArrayOf(0x00, 0x00, 0x03)))
        val params = SPSParser.parseSPSStatic(sps, 0, sps.size)

        assertEquals(720, params.width)
        assertEquals(576, params.height)
        assertEquals(16f / 15f, params.pixelAspectRatio)
        assertEquals(25f, params.frameRate)
    }

    @Test
    fun videoTrackAppliesPixelAspectRatioToDisplaySize() {
        val track = VideoTrack("video", 96).apply {
            width = 720
            height = 576
            pixelAspectRatio = 16f / 15f
        }

        assertEquals(4f / 3f, track.displayAspectRatio)
    }

    private fun createSps(
        width: Int,
        height: Int,
        profile: Int,
        sarWidth: Int = 1,
        sarHeight: Int = 1,
        frameRate: Int? = null
    ): ByteArray {
        val codedWidth = ceil(width / 16.0).toInt() * 16
        val codedHeight = ceil(height / 16.0).toInt() * 16
        require((codedWidth - width) % 2 == 0 && (codedHeight - height) % 2 == 0)

        val bits = BitWriter()
        bits.writeBits(profile.toLong(), 8)
        bits.writeBits(0, 8)
        bits.writeBits(40, 8)
        bits.writeUnsignedExpGolomb(0)
        if (profile == 100) {
            bits.writeUnsignedExpGolomb(1) // 4:2:0 chroma
            bits.writeUnsignedExpGolomb(0)
            bits.writeUnsignedExpGolomb(0)
            bits.writeBit(false)
            bits.writeBit(false)
        }
        bits.writeUnsignedExpGolomb(0)
        bits.writeUnsignedExpGolomb(0)
        bits.writeUnsignedExpGolomb(0)
        bits.writeUnsignedExpGolomb(1)
        bits.writeBit(false)
        bits.writeUnsignedExpGolomb(codedWidth / 16 - 1)
        bits.writeUnsignedExpGolomb(codedHeight / 16 - 1)
        bits.writeBit(true)
        bits.writeBit(true)

        val cropRight = (codedWidth - width) / 2
        val cropBottom = (codedHeight - height) / 2
        bits.writeBit(cropRight != 0 || cropBottom != 0)
        if (cropRight != 0 || cropBottom != 0) {
            bits.writeUnsignedExpGolomb(0)
            bits.writeUnsignedExpGolomb(cropRight)
            bits.writeUnsignedExpGolomb(0)
            bits.writeUnsignedExpGolomb(cropBottom)
        }

        val hasVui = sarWidth != 1 || sarHeight != 1 || frameRate != null
        bits.writeBit(hasVui)
        if (hasVui) {
            bits.writeBit(sarWidth != 1 || sarHeight != 1)
            if (sarWidth != 1 || sarHeight != 1) {
                bits.writeBits(255, 8)
                bits.writeBits(sarWidth.toLong(), 16)
                bits.writeBits(sarHeight.toLong(), 16)
            }
            bits.writeBit(false)
            bits.writeBit(false)
            bits.writeBit(false)
            bits.writeBit(frameRate != null)
            if (frameRate != null) {
                bits.writeBits(1, 32)
                bits.writeBits((frameRate * 2).toLong(), 32)
                bits.writeBit(true)
            }
        }
        bits.finishRbsp()

        return byteArrayOf(0x67) + addEmulationPreventionBytes(bits.toByteArray())
    }

    private fun addEmulationPreventionBytes(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        var zeroCount = 0
        data.forEach { byte ->
            val value = byte.toInt() and 0xff
            if (zeroCount >= 2 && value <= 3) {
                output.write(3)
                zeroCount = 0
            }
            output.write(value)
            zeroCount = if (value == 0) zeroCount + 1 else 0
        }
        return output.toByteArray()
    }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean =
        indices.any { start ->
            start + sequence.size <= size && sequence.indices.all { index ->
                this[start + index] == sequence[index]
            }
        }

    private class BitWriter {
        private val bytes = mutableListOf<Int>()
        private var currentByte = 0
        private var bitCount = 0

        fun writeBit(value: Boolean) {
            currentByte = (currentByte shl 1) or if (value) 1 else 0
            bitCount++
            if (bitCount == 8) {
                bytes += currentByte
                currentByte = 0
                bitCount = 0
            }
        }

        fun writeBits(value: Long, count: Int) {
            for (index in count - 1 downTo 0) {
                writeBit((value ushr index) and 1L != 0L)
            }
        }

        fun writeUnsignedExpGolomb(value: Int) {
            val codeNum = value + 1
            val bitLength = Int.SIZE_BITS - codeNum.countLeadingZeroBits()
            repeat(bitLength - 1) { writeBit(false) }
            writeBits(codeNum.toLong(), bitLength)
        }

        fun finishRbsp() {
            writeBit(true)
            while (bitCount != 0) writeBit(false)
        }

        fun toByteArray(): ByteArray = bytes.map(Int::toByte).toByteArray()
    }
}
