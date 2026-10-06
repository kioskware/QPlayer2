package com.fivesoft.qplayer.impl.extractor.ts

import com.fivesoft.qplayer.base.Frame
import com.fivesoft.qplayer.common.TrackSelector
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.AUDIO
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.PMT
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.VIDEO
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.adts
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.annexB
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.idr
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.media
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.packet
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.packetize
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.pat
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.pes
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.pmt
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.pps
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.sps
import com.fivesoft.qplayer.impl.extractor.ts.TsTestData.tables
import org.junit.Assert.*
import org.junit.Test

class TsDemuxerTest {
    private fun TsDemuxer.accept(data: ByteArray) = consume(data, 0, data.size, 1234)
    private fun demuxer(selector: TrackSelector = TrackSelector.ALL) = TsDemuxer(selector, 0).apply { accept(tables()) }
    private fun TsDemuxer.drain(): List<Frame> = generateSequence { poll() }.toList()

    @Test fun discoversTracksAndProducesDecoderReadyAvcAndAacWithCommonPtsClock() {
        val demuxer = demuxer()
        demuxer.accept(media())
        val frames = demuxer.drain()
        assertEquals(4, frames.size)
        assertEquals(listOf(Frame.CONFIG_FRAME, Frame.CONFIG_FRAME, Frame.SYNC_FRAME, Frame.SYNC_FRAME), frames.map { it.frameType })
        assertArrayEquals(byteArrayOf(0, 0, 0, 1) + idr, frames[2].array)
        assertArrayEquals(byteArrayOf(0x11, 0x22, 0x33), frames[3].array)
        assertEquals(0L, frames[2].timestamp)
        assertEquals(20L, frames[3].timestamp)
        assertEquals("h264", demuxer.tracks.videoTrack!!.format)
        assertEquals(48000, demuxer.tracks.audioTrack!!.clockRate)
        assertEquals(2, demuxer.tracks.audioTrack!!.channels)
        assertArrayEquals(byteArrayOf(0x11, 0x90.toByte()), demuxer.tracks.audioTrack!!.csd.getCsd(0))
        assertArrayEquals(sps, demuxer.tracks.videoTrack!!.csd.getCsd(0))
        assertArrayEquals(pps, demuxer.tracks.videoTrack!!.csd.getCsd(1))
    }

    @Test fun selectsAudioWithoutTryingToSelectTheTransportContainer() {
        val demuxer = demuxer(TrackSelector.AUDIO)
        demuxer.accept(media())
        assertEquals(listOf("aac"), demuxer.drain().map { it.track.format })
        assertEquals(2, demuxer.tracks.size())
    }

    @Test fun selectsExplicitProgramFromPat() {
        val demuxer = TsDemuxer(TrackSelector.ALL, 2)
        demuxer.accept(packet(0, byteArrayOf(0) + pat(1 to 100, 2 to 200)))
        demuxer.accept(packet(100, byteArrayOf(0) + pmt()))
        assertFalse(demuxer.isProgramMapRead)
        demuxer.accept(packet(200, byteArrayOf(0) + pmt(program = 2)))
        assertTrue(demuxer.isProgramMapRead)
        assertEquals(2, demuxer.tracks.videoTrack!!.getMetadata("ts-program"))
    }

    @Test fun validatesPsiCrcAndReassemblesSectionsWithPointerFields() {
        val demuxer = TsDemuxer(TrackSelector.ALL, 0)
        val pat = pat(1 to PMT)
        val corrupted = pat.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        demuxer.accept(packet(0, byteArrayOf(0) + corrupted))
        demuxer.accept(packet(PMT, byteArrayOf(0) + pmt()))
        assertFalse(demuxer.isProgramMapRead)
        demuxer.accept(packet(0, byteArrayOf(0) + pat.copyOfRange(0, 7), cc = 1))
        // The pointer completes the preceding section, then starts a repeated section.
        val tail = pat.copyOfRange(7, pat.size)
        demuxer.accept(packet(0, byteArrayOf(tail.size.toByte()) + tail + pat, cc = 2))
        val pmt = pmt()
        demuxer.accept(packet(PMT, byteArrayOf(0) + pmt.copyOfRange(0, 9)))
        demuxer.accept(packet(PMT, pmt.copyOfRange(9, pmt.size), start = false, cc = 1))
        assertTrue(demuxer.isProgramMapRead)
        assertEquals(2, demuxer.tracks.size())
    }

    @Test fun waitsForCompletePatAndFollowsProgramRemoval() {
        val demuxer = TsDemuxer(TrackSelector.ALL, 0)
        demuxer.accept(packet(0, byteArrayOf(0) + pat(2 to 200, sectionNumber = 1, lastSection = 1)))
        demuxer.accept(packet(200, byteArrayOf(0) + pmt(program = 2)))
        assertFalse(demuxer.isProgramMapRead)
        demuxer.accept(packet(0, byteArrayOf(0) + pat(1 to 100, lastSection = 1), cc = 1))
        demuxer.accept(packet(100, byteArrayOf(0) + pmt()))
        assertEquals(1, demuxer.tracks.videoTrack!!.getMetadata("ts-program"))
        demuxer.accept(packet(0, byteArrayOf(0) + pat(2 to 200, version = 1), cc = 2))
        assertFalse(demuxer.isProgramMapRead)
        assertEquals(0, demuxer.tracks.size())
        demuxer.accept(packet(200, byteArrayOf(0) + pmt(program = 2)))
        assertEquals(2, demuxer.tracks.videoTrack!!.getMetadata("ts-program"))
    }

    @Test fun assemblesPesHeaderNalPrefixesAndAdtsAcrossTsPackets() {
        val demuxer = demuxer()
        val largeNal = byteArrayOf(0x65) + ByteArray(400) { 0x11 }
        demuxer.accept(packetize(VIDEO, pes(annexB(largeNal, byteArrayOf(9, 0x10))), firstSize = 4))
        assertArrayEquals(byteArrayOf(0, 0, 0, 1) + largeNal, demuxer.poll()!!.array)
        val audio = pes(adts(ByteArray(300) { 0x22 }), video = false)
        demuxer.accept(packetize(AUDIO, audio, firstSize = 16))
        assertArrayEquals(ByteArray(300) { 0x22 }, demuxer.poll()!!.array)
    }

    @Test fun preservesElementaryDataAcrossPesBoundariesAndStripsAdtsCrc() {
        val demuxer = demuxer()
        val audio = adts(crc = true)
        demuxer.accept(packetize(AUDIO, pes(audio.copyOfRange(0, 5), video = false)))
        demuxer.accept(packetize(AUDIO, pes(audio.copyOfRange(5, audio.size), video = false), cc = 1))
        assertArrayEquals(byteArrayOf(0x11, 0x22, 0x33), demuxer.poll()!!.array)
        demuxer.accept(packetize(VIDEO, pes(byteArrayOf(0, 0))))
        demuxer.accept(packetize(VIDEO, pes(byteArrayOf(1) + idr + annexB(byteArrayOf(9, 0x10))), cc = 1))
        assertArrayEquals(byteArrayOf(0, 0, 0, 1) + idr, demuxer.poll()!!.array)
    }

    @Test fun detectsContinuityLossDiscardsPartialNalAndRecoversAtNextPes() {
        val demuxer = demuxer()
        demuxer.accept(packet(VIDEO, pes(annexB(byteArrayOf(0x65, 0x11)), unbounded = true)))
        demuxer.accept(packet(VIDEO, byteArrayOf(0x22), start = false, cc = 2))
        assertNull(demuxer.poll())
        demuxer.accept(packetize(VIDEO, pes(annexB(idr, byteArrayOf(9, 0x10))), cc = 3))
        assertArrayEquals(byteArrayOf(0, 0, 0, 1) + idr, demuxer.poll()!!.array)
        assertNull(demuxer.poll())
    }

    @Test fun ignoresDuplicatePayloadAndAcceptsContinuityCounterWrap() {
        val demuxer = demuxer()
        val packet = packet(AUDIO, pes(adts(), video = false), cc = 15)
        demuxer.accept(packet)
        demuxer.accept(packet)
        demuxer.accept(packet(AUDIO, pes(adts(), pts = 91800, video = false), cc = 0))
        assertEquals(listOf(0L, 20L), demuxer.drain().map { it.timestamp })
    }

    @Test fun unwraps33BitPtsAndTimestampsAggregatedAudioFrames() {
        val demuxer = demuxer()
        demuxer.accept(packetize(AUDIO, pes(adts() + adts(), pts = (1L shl 33) - 900, video = false)))
        demuxer.accept(packetize(AUDIO, pes(adts(), pts = 4500, video = false), cc = 1))
        assertEquals(listOf(0L, 21L, 60L), demuxer.drain().map { it.timestamp })
    }

    @Test fun ignoresStuffingBeyondFinitePesAndRejectsNegativePayloadLength() {
        val demuxer = demuxer()
        val malformed = pes(adts(), video = false).apply { this[5] = 7 }
        demuxer.accept(packet(AUDIO, malformed))
        assertNull(demuxer.poll())
        demuxer.accept(packet(AUDIO, pes(adts(), video = false) + adts(), cc = 1))
        assertEquals(1, demuxer.drain().size)
    }

    @Test fun pmtUpdatesPreserveUnchangedTrackAndRemoveRetiredFrames() {
        val demuxer = demuxer()
        val video = demuxer.tracks.videoTrack
        demuxer.accept(media())
        demuxer.accept(packet(PMT, byteArrayOf(0) + pmt(version = 1, entries = listOf(0x1b to VIDEO)), cc = 1))
        assertSame(video, demuxer.tracks.videoTrack)
        assertNull(demuxer.tracks.audioTrack)
        assertEquals(3, demuxer.drain().size)
    }

    @Test fun transportDiscontinuityKeepsTablesAndDropsPartialFrames() {
        val demuxer = demuxer()
        demuxer.accept(packet(AUDIO, pes(adts().copyOfRange(0, 8), video = false)))
        demuxer.discontinuity()
        assertTrue(demuxer.isProgramMapRead)
        demuxer.accept(packet(AUDIO, byteArrayOf(0x22, 0x33), start = false, cc = 2))
        assertNull(demuxer.poll())
        demuxer.accept(packet(AUDIO, pes(adts(), video = false), cc = 3, discontinuity = true))
        assertNotNull(demuxer.poll())
    }

    @Test fun rejectsCorruptTransportPacketsAndResetAllowsFreshProgram() {
        val demuxer = demuxer()
        for (corrupt in listOf(media().copyOfRange(0, 187), packet(AUDIO, pes(adts())).apply { this[0] = 0 },
            packet(AUDIO, pes(adts())).apply { this[3] = 0x70 })) demuxer.accept(corrupt)
        assertNull(demuxer.poll())
        demuxer.reset()
        assertFalse(demuxer.isProgramMapRead)
        assertEquals(0, demuxer.tracks.size())
        demuxer.accept(tables() + media())
        assertEquals(4, demuxer.drain().size)
    }
}
