package com.fivesoft.qplayer.impl.extractor.ts;

import com.fivesoft.qplayer.base.Frame;
import com.fivesoft.qplayer.base.track.AudioTrack;
import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.base.track.Tracks;
import com.fivesoft.qplayer.base.track.VideoTrack;
import com.fivesoft.qplayer.common.TrackSelector;
import com.fivesoft.qplayer.core.FrameBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Incremental ISO/IEC 13818-1 demultiplexer for H.264 Annex B and AAC ADTS. */
public final class TsDemuxer {
    public static final int PACKET_SIZE = 188;
    private static final long PTS_MASK = (1L << 33) - 1;
    private final Tracks tracks = new Tracks();
    private final TrackSelector selector;
    private final int requestedProgram;
    private final int[] continuity = new int[8192];
    private final Map<Integer, SectionReader> sections = new HashMap<>();
    private final Map<Integer, PesReader> streams = new LinkedHashMap<>();
    private final ArrayDeque<Frame> frames = new ArrayDeque<>();
    private int program = -1;
    private int pmtPid = -1;
    private int pmtVersion = -1;
    private int pendingPmtVersion = -1;
    private final Map<Integer, byte[]> pmtSections = new HashMap<>();
    private boolean programMapRead;
    private long ptsOrigin = -1;
    private long lastPts;

    public TsDemuxer(TrackSelector selector, int programNumber) {
        this.selector = selector == null ? TrackSelector.ALL : selector;
        if (programNumber < 0 || programNumber > 65535) throw new IllegalArgumentException("Invalid TS program");
        requestedProgram = programNumber;
        reset();
    }

    public Tracks getTracks() { return tracks; }
    public Frame poll() { return frames.poll(); }
    public boolean isProgramMapRead() { return programMapRead; }
    public boolean hasSelectedTracks() {
        for (PesReader stream : streams.values()) if (stream.selected) return true;
        return false;
    }

    public void reset() {
        tracks.clear();
        frames.clear();
        streams.clear();
        sections.clear();
        sections.put(0, new SectionReader(true));
        Arrays.fill(continuity, -1);
        program = pmtPid = pmtVersion = pendingPmtVersion = -1;
        pmtSections.clear();
        programMapRead = false;
        ptsOrigin = -1;
        lastPts = 0;
    }

    /** Retains complete program tables, but discards partial data after packet loss. */
    public void discontinuity() {
        Arrays.fill(continuity, -1);
        for (SectionReader reader : sections.values()) reader.reset();
        for (PesReader reader : streams.values()) reader.reset();
    }

    public void consume(byte[] data, int offset, int length, long fallbackTimeMs) throws IOException {
        if (offset < 0 || length < 0 || offset > data.length - length) throw new IndexOutOfBoundsException();
        if (length == 0 || length % PACKET_SIZE != 0) {
            discontinuity();
            return;
        }
        for (int position = offset; position < offset + length; position += PACKET_SIZE) {
            consumePacket(data, position, fallbackTimeMs);
        }
    }

    private void consumePacket(byte[] data, int start, long fallbackTimeMs) throws IOException {
        if (data[start] != 0x47) { discontinuity(); return; }
        int pid = ((data[start + 1] & 0x1f) << 8) | (data[start + 2] & 0xff);
        if (pid == 0x1fff || (!sections.containsKey(pid) && !streams.containsKey(pid))) return;
        int flags = data[start + 3] & 0xff;
        int adaptation = (flags >> 4) & 3;
        if ((data[start + 1] & 0x80) != 0 || (flags & 0xc0) != 0 || adaptation == 0) {
            resetPid(pid);
            return;
        }
        int position = start + 4;
        int end = start + PACKET_SIZE;
        if ((adaptation & 2) != 0) {
            int size = data[position++] & 0xff;
            if (size > end - position) { resetPid(pid); return; }
            if (size > 0 && (data[position] & 0x80) != 0) resetPid(pid);
            position += size;
        }
        if ((adaptation & 1) == 0 || position == end) return;
        int counter = flags & 0x0f;
        int previous = continuity[pid];
        if (counter == previous) return;
        if (previous != -1 && counter != ((previous + 1) & 0x0f)) resetPid(pid);
        continuity[pid] = counter;
        boolean unitStart = (data[start + 1] & 0x40) != 0;
        SectionReader section = sections.get(pid);
        if (section != null) section.consume(data, position, end, unitStart);
        else {
            PesReader stream = streams.get(pid);
            if (stream != null && stream.selected) stream.consume(data, position, end, unitStart, fallbackTimeMs);
        }
    }

    private void resetPid(int pid) {
        continuity[pid] = -1;
        SectionReader section = sections.get(pid);
        if (section != null) section.reset();
        PesReader stream = streams.get(pid);
        if (stream != null) stream.reset();
    }

    private void readPat(byte[] data) {
        if (data[0] != 0 || data.length < 12 || (data[5] & 1) == 0) return;
        int chosenProgram = -1;
        int chosenPid = -1;
        for (int position = 8; position + 4 <= data.length - 4; position += 4) {
            int number = unsignedShort(data, position);
            if (number == 0) continue;
            if (number == requestedProgram || (requestedProgram == 0 && (program == -1 || number == program))) {
                chosenProgram = number;
                chosenPid = unsignedShort(data, position + 2) & 0x1fff;
                break;
            }
        }
        if (chosenProgram == -1 || chosenPid == 0 || chosenPid == 0x1fff) return;
        if (chosenProgram != program || chosenPid != pmtPid) {
            if (pmtPid != -1) sections.remove(pmtPid);
            tracks.clear();
            streams.clear();
            frames.clear();
            programMapRead = false;
            pmtVersion = pendingPmtVersion = -1;
            pmtSections.clear();
            program = chosenProgram;
            pmtPid = chosenPid;
            sections.put(pmtPid, new SectionReader(false));
        }
    }

    private void readPmt(byte[] data) {
        if (data[0] != 2 || data.length < 16 || unsignedShort(data, 3) != program || (data[5] & 1) == 0) return;
        int version = (data[5] >> 1) & 31;
        if (version == pmtVersion) return;
        if (version != pendingPmtVersion) {
            pendingPmtVersion = version;
            pmtSections.clear();
        }
        int sectionNumber = data[6] & 0xff;
        int lastSection = data[7] & 0xff;
        if (sectionNumber > lastSection) return;
        pmtSections.put(sectionNumber, data);
        for (int i = 0; i <= lastSection; i++) if (!pmtSections.containsKey(i)) return;
        Map<Integer, Integer> elementaryPids = new LinkedHashMap<>();
        for (int i = 0; i <= lastSection; i++) {
            byte[] section = pmtSections.get(i);
            int position = 12 + (unsignedShort(section, 10) & 0x0fff);
            int end = section.length - 4;
            if (position > end) return;
            while (position < end) {
                if (end - position < 5) return;
                int type = section[position] & 0xff;
                int pid = unsignedShort(section, position + 1) & 0x1fff;
                int infoLength = unsignedShort(section, position + 3) & 0x0fff;
                position += 5;
                if (infoLength > end - position) return;
                if ((type == 0x1b || type == 0x0f) && pid > 0 && pid != 0x1fff && pid != pmtPid) {
                    elementaryPids.put(pid, type);
                }
                position += infoLength;
            }
        }
        Map<Integer, PesReader> updated = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : elementaryPids.entrySet()) {
            int pid = entry.getKey();
            int type = entry.getValue();
            PesReader reader = streams.get(pid);
            if (reader == null || reader.type != type) {
                Track track;
                if (type == 0x1b) {
                    track = new VideoTrack("ts-" + pid, pid);
                    track.setFormat("h264");
                    track.setClockRate(90000);
                } else {
                    AudioTrack audio = new AudioTrack("ts-" + pid, pid);
                    audio.setFormat("aac");
                    audio.setChannels(2);
                    audio.setClockRate(44100);
                    track = audio;
                }
                track.setMetadata("ts-pid", pid);
                track.setMetadata("ts-program", program);
                reader = new PesReader(type, track);
                continuity[pid] = -1;
            }
            updated.put(pid, reader);
        }
        streams.clear();
        streams.putAll(updated);
        tracks.removeIf(track -> !updated.containsKey((Integer) track.getMetadata("ts-pid")));
        for (PesReader reader : streams.values()) tracks.put(reader.track);
        frames.removeIf(frame -> tracks.get(frame.getTrack().getId()) != frame.getTrack());
        pmtVersion = version;
        programMapRead = true;
    }

    private long presentationTime(long pts) {
        if (ptsOrigin == -1) {
            ptsOrigin = lastPts = pts;
        } else {
            long difference = ((pts - (lastPts & PTS_MASK) + (1L << 32)) & PTS_MASK) - (1L << 32);
            lastPts += difference;
        }
        return (lastPts - ptsOrigin) * 1000 / 90000;
    }

    private static int unsignedShort(byte[] data, int position) {
        return ((data[position] & 0xff) << 8) | (data[position + 1] & 0xff);
    }

    private static boolean validCrc(byte[] data) {
        int crc = -1;
        for (byte value : data) {
            crc ^= (value & 0xff) << 24;
            for (int bit = 0; bit < 8; bit++) crc = (crc << 1) ^ (crc < 0 ? 0x04c11db7 : 0);
        }
        return crc == 0;
    }

    private final class SectionReader {
        final boolean pat;
        final byte[] buffer = new byte[4096];
        int length;
        int expected = -1;
        boolean active;

        SectionReader(boolean pat) { this.pat = pat; }
        void reset() { length = 0; expected = -1; active = false; }

        void consume(byte[] data, int position, int end, boolean unitStart) {
            if (unitStart) {
                int pointer = data[position++] & 0xff;
                if (pointer > end - position) { reset(); return; }
                if (active && length > 0) append(data, position, position + pointer, false);
                reset();
                position += pointer;
                active = true;
            }
            if (active) append(data, position, end, true);
        }

        void append(byte[] data, int position, int end, boolean allowNext) {
            while (position < end && active) {
                if (length == 0 && (data[position] & 0xff) == 0xff) { reset(); return; }
                buffer[length++] = data[position++];
                if (length == 3) {
                    expected = 3 + (unsignedShort(buffer, 1) & 0x0fff);
                    if (expected < 12 || expected > 4096 || (buffer[1] & 0x80) == 0) { reset(); return; }
                }
                if (length == expected) {
                    byte[] complete = Arrays.copyOf(buffer, length);
                    length = 0;
                    expected = -1;
                    if (validCrc(complete)) {
                        if (pat) readPat(complete); else readPmt(complete);
                    }
                    if (!allowNext) { reset(); return; }
                }
            }
        }
    }

    private final class PesReader {
        final int type;
        final Track track;
        final boolean selected;
        final ElementaryReader elementary;
        final byte[] header = new byte[264];
        boolean active;
        int headerLength;
        int headerTarget;
        int remaining;
        long timestamp;

        PesReader(int type, Track track) {
            this.type = type;
            this.track = track;
            selected = selector.selectTrack(track);
            elementary = type == 0x1b ? new AvcReader(track) : new AdtsReader((AudioTrack) track);
        }

        void reset() {
            active = false;
            headerLength = 0;
            elementary.reset();
        }

        void consume(byte[] data, int position, int end, boolean unitStart, long fallbackTimeMs) throws IOException {
            if (unitStart) {
                if (active && (headerLength < headerTarget || remaining > 0)) elementary.reset();
                active = true;
                headerLength = 0;
                headerTarget = 9;
                remaining = -1;
                timestamp = fallbackTimeMs;
            }
            if (!active) return;
            while (position < end && headerLength < headerTarget) {
                header[headerLength++] = data[position++];
                if (headerLength == 9) {
                    if (header[0] != 0 || header[1] != 0 || header[2] != 1 || (header[6] & 0xc0) != 0x80) { reset(); return; }
                    headerTarget = 9 + (header[8] & 0xff);
                }
                if (headerLength == headerTarget) {
                    int packetLength = unsignedShort(header, 4);
                    remaining = packetLength == 0 ? -1 : packetLength - 3 - (header[8] & 0xff);
                    if (packetLength != 0 && remaining < 0) { reset(); return; }
                    int ptsFlags = (header[7] >> 6) & 3;
                    if (ptsFlags == 1 || (ptsFlags == 2 && headerTarget < 14)
                            || (ptsFlags == 3 && headerTarget < 19)) { reset(); return; }
                    if (ptsFlags >= 2) {
                        if ((header[9] >> 4 & 0xf) != ptsFlags || (header[9] & 1) == 0
                                || (header[11] & 1) == 0 || (header[13] & 1) == 0) { reset(); return; }
                        long pts = ((long) (header[9] & 0x0e) << 29) | ((long) (header[10] & 0xff) << 22)
                                | ((long) (header[11] & 0xfe) << 14) | ((long) (header[12] & 0xff) << 7)
                                | ((header[13] & 0xfe) >> 1);
                        timestamp = presentationTime(pts);
                    }
                    elementary.packetStarted(timestamp);
                }
            }
            if (headerLength != headerTarget) return;
            int count = remaining == -1 ? end - position : Math.min(end - position, remaining);
            elementary.consume(data, position, count);
            if (remaining != -1) {
                remaining -= count;
                if (remaining == 0) active = false;
            }
        }
    }

    private interface ElementaryReader {
        void reset();
        void packetStarted(long timestamp);
        void consume(byte[] data, int offset, int length) throws IOException;
    }

    private final class AvcReader implements ElementaryReader {
        final Track track;
        final ByteArrayOutputStream nal = new ByteArrayOutputStream();
        boolean started;
        int zeroes;
        long pesTimestamp;
        long nalTimestamp;

        AvcReader(Track track) { this.track = track; }
        public void reset() { nal.reset(); started = false; zeroes = 0; }
        public void packetStarted(long timestamp) { pesTimestamp = timestamp; }

        public void consume(byte[] data, int offset, int length) throws IOException {
            for (int i = offset; i < offset + length; i++) {
                int value = data[i] & 0xff;
                if (value == 0) {
                    if (++zeroes > FrameBuilder.DEFAULT_MAX_FRAME_SIZE) { reset(); throw new IOException("TS H.264 frame exceeds limit"); }
                } else if (value == 1 && zeroes >= 2) {
                    emitNal();
                    nal.reset();
                    started = true;
                    zeroes = 0;
                    nalTimestamp = pesTimestamp;
                } else {
                    if (started) {
                        if (nal.size() + zeroes + 5 > FrameBuilder.DEFAULT_MAX_FRAME_SIZE) { reset(); throw new IOException("TS H.264 frame exceeds limit"); }
                        while (zeroes-- > 0) nal.write(0);
                        nal.write(value);
                    }
                    zeroes = 0;
                }
            }
        }

        void emitNal() {
            if (!started || nal.size() == 0) return;
            byte[] payload = nal.toByteArray();
            int type = payload[0] & 31;
            if ((payload[0] & 0x80) != 0 || type == 0 || type >= 24) return;
            byte flag = type == 5 ? Frame.SYNC_FRAME : Frame.NON_SYNC_FRAME;
            if (type == 7 || type == 8) {
                track.getCsd().setCsd(type == 7 ? 0 : 1, payload);
                flag = Frame.CONFIG_FRAME;
            }
            byte[] annexB = new byte[payload.length + 4];
            annexB[3] = 1;
            System.arraycopy(payload, 0, annexB, 4, payload.length);
            frames.add(new Frame(annexB, nalTimestamp, track, flag));
        }
    }

    private final class AdtsReader implements ElementaryReader {
        final AudioTrack track;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
        final int[] sampleRates = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};
        int frameLength;
        int headerLength;
        int frequencyIndex;
        int channels;
        int objectType;
        int sampleRate;
        long timeBase;
        long samples;
        long pendingTime = Long.MIN_VALUE;

        AdtsReader(AudioTrack track) { this.track = track; }
        public void reset() { buffer.reset(); frameLength = 0; samples = 0; pendingTime = Long.MIN_VALUE; }
        public void packetStarted(long timestamp) {
            if (buffer.size() == 0) { timeBase = timestamp; samples = 0; }
            else pendingTime = timestamp;
        }

        public void consume(byte[] data, int offset, int length) {
            for (int i = offset; i < offset + length; i++) {
                int value = data[i] & 0xff;
                if (buffer.size() == 0 && value != 0xff) continue;
                if (buffer.size() == 1 && (value & 0xf6) != 0xf0) {
                    buffer.reset();
                    if (value == 0xff) buffer.write(value);
                    continue;
                }
                buffer.write(value);
                if (buffer.size() == 7) {
                    byte[] header = buffer.toByteArray();
                    headerLength = (header[1] & 1) != 0 ? 7 : 9;
                    frequencyIndex = (header[2] >> 2) & 15;
                    channels = ((header[2] & 1) << 2) | ((header[3] >> 6) & 3);
                    objectType = ((header[2] >> 6) & 3) + 1;
                    frameLength = ((header[3] & 3) << 11) | ((header[4] & 0xff) << 3) | ((header[5] & 0xe0) >> 5);
                    if (frequencyIndex >= sampleRates.length || channels == 0 || frameLength <= headerLength
                            || (header[6] & 3) != 0) { buffer.reset(); frameLength = 0; continue; }
                    sampleRate = sampleRates[frequencyIndex];
                }
                if (frameLength > 0 && buffer.size() == frameLength) {
                    byte[] adts = buffer.toByteArray();
                    track.setClockRate(sampleRate);
                    track.setChannels(channels);
                    track.getCsd().setCsd(0, new byte[]{(byte) ((objectType << 3) | (frequencyIndex >> 1)),
                            (byte) (((frequencyIndex & 1) << 7) | (channels << 3))});
                    byte[] payload = Arrays.copyOfRange(adts, headerLength, frameLength);
                    frames.add(new Frame(payload, timeBase + samples * 1000 / sampleRate, track, Frame.SYNC_FRAME));
                    samples += 1024;
                    buffer.reset();
                    frameLength = 0;
                    if (pendingTime != Long.MIN_VALUE) {
                        timeBase = pendingTime;
                        samples = 0;
                        pendingTime = Long.MIN_VALUE;
                    }
                }
            }
        }
    }
}
