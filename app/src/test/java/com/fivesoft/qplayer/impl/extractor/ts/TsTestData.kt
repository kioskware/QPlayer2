package com.fivesoft.qplayer.impl.extractor.ts

import java.nio.ByteBuffer

/** Small wire-format fixtures with MPEG-2 CRCs, PTS and adaptation-field stuffing. */
object TsTestData {
    const val PMT = 100
    const val VIDEO = 256
    const val AUDIO = 257
    val sps = byteArrayOf(0x67, 0x42, 0, 0x1e, 0x11)
    val pps = byteArrayOf(0x68, 0x22)
    val idr = byteArrayOf(0x65, 0x33, 0x44)

    fun concat(vararg arrays: ByteArray): ByteArray = arrays.fold(byteArrayOf()) { a, b -> a + b }
    fun annexB(vararg nals: ByteArray): ByteArray =
        nals.fold(byteArrayOf()) { a, b -> a + byteArrayOf(0, 0, 1) + b }

    fun packet(pid: Int, payload: ByteArray, start: Boolean = true, cc: Int = 0,
               discontinuity: Boolean = false): ByteArray {
        require(payload.size in 1..184)
        val data = ByteArray(188) { 0xff.toByte() }
        data[0] = 0x47
        data[1] = ((pid shr 8) or (if (start) 0x40 else 0)).toByte()
        data[2] = pid.toByte()
        val padding = 184 - payload.size
        data[3] = ((if (padding == 0) 0x10 else 0x30) or (cc and 15)).toByte()
        if (padding > 0) {
            data[4] = (padding - 1).toByte()
            if (padding > 1) data[5] = (if (discontinuity) 0x80 else 0).toByte()
        }
        payload.copyInto(data, 4 + padding)
        return data
    }

    fun packetize(pid: Int, payload: ByteArray, cc: Int = 0, firstSize: Int = 184): ByteArray {
        var offset = 0
        var counter = cc
        var output = byteArrayOf()
        while (offset < payload.size) {
            val size = minOf(if (offset == 0) firstSize else 184, payload.size - offset)
            output += packet(pid, payload.copyOfRange(offset, offset + size), offset == 0, counter++)
            offset += size
        }
        return output
    }

    fun pat(vararg programs: Pair<Int, Int>, version: Int = 0, sectionNumber: Int = 0,
            lastSection: Int = 0): ByteArray {
        var body = byteArrayOf(0, 0xb0.toByte(), 0, 0, 1, (0xc1 or (version shl 1)).toByte(),
            sectionNumber.toByte(), lastSection.toByte())
        for ((program, pid) in programs) body += byteArrayOf((program shr 8).toByte(), program.toByte(),
            (0xe0 or (pid shr 8)).toByte(), pid.toByte())
        return section(body)
    }

    fun pmt(program: Int = 1, version: Int = 0, entries: List<Pair<Int, Int>> =
        listOf(0x1b to VIDEO, 0x0f to AUDIO)): ByteArray {
        var body = byteArrayOf(2, 0xb0.toByte(), 0, (program shr 8).toByte(), program.toByte(),
            (0xc1 or (version shl 1)).toByte(), 0, 0, 0xe1.toByte(), 0, 0xf0.toByte(), 0)
        for ((type, pid) in entries) body += byteArrayOf(type.toByte(), (0xe0 or (pid shr 8)).toByte(),
            pid.toByte(), 0xf0.toByte(), 0)
        return section(body)
    }

    fun section(body: ByteArray): ByteArray {
        val length = body.size + 1
        body[1] = ((body[1].toInt() and 0xf0) or (length shr 8)).toByte()
        body[2] = length.toByte()
        var crc = -1
        for (value in body) {
            crc = crc xor ((value.toInt() and 255) shl 24)
            repeat(8) { crc = (crc shl 1) xor (if (crc < 0) 0x04c11db7 else 0) }
        }
        return body + ByteBuffer.allocate(4).putInt(crc).array()
    }

    fun tables(entries: List<Pair<Int, Int>> = listOf(0x1b to VIDEO, 0x0f to AUDIO)): ByteArray =
        packet(0, byteArrayOf(0) + pat(1 to PMT)) + packet(PMT, byteArrayOf(0) + pmt(entries = entries))

    fun pes(payload: ByteArray, pts: Long = 90000, video: Boolean = true,
            unbounded: Boolean = false): ByteArray {
        val size = if (unbounded) 0 else payload.size + 8
        return byteArrayOf(0, 0, 1, (if (video) 0xe0 else 0xc0).toByte(), (size shr 8).toByte(),
            size.toByte(), 0x80.toByte(), 0x80.toByte(), 5,
            (0x21 or (((pts shr 30) and 7).toInt() shl 1)).toByte(), (pts shr 22).toByte(),
            (1 or (((pts shr 15) and 127).toInt() shl 1)).toByte(), (pts shr 7).toByte(),
            (1 or ((pts and 127).toInt() shl 1)).toByte()) + payload
    }

    fun adts(payload: ByteArray = byteArrayOf(0x11, 0x22, 0x33), crc: Boolean = false): ByteArray {
        val size = payload.size + if (crc) 9 else 7
        return byteArrayOf(0xff.toByte(), (if (crc) 0xf0 else 0xf1).toByte(), 0x4c,
            (0x80 or (size shr 11)).toByte(), (size shr 3).toByte(),
            (((size and 7) shl 5) or 31).toByte(), 0xfc.toByte()) +
            (if (crc) byteArrayOf(0x12, 0x34) else byteArrayOf()) + payload
    }

    fun media(): ByteArray = packetize(VIDEO, pes(annexB(sps, pps, idr, byteArrayOf(9, 0x10)))) +
        packetize(AUDIO, pes(adts(), pts = 91800, video = false))

    fun rtp(payload: ByteArray, sequence: Int = 1, pt: Int = 33, ssrc: Int = 1): ByteArray =
        ByteBuffer.allocate(12 + payload.size).put(0x80.toByte()).put(pt.toByte())
            .putShort(sequence.toShort()).putInt(90000).putInt(ssrc).put(payload).array()
}
