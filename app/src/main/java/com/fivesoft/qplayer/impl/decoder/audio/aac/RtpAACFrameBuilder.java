package com.fivesoft.qplayer.impl.decoder.audio.aac;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Frame;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.core.FrameBuilder;
import com.fivesoft.qplayer.impl.extractor.rtp.RtpSample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** RFC 3640 MPEG4-GENERIC AAC-hbr/AAC-lbr access-unit aggregation and fragmentation. */
public class RtpAACFrameBuilder extends FrameBuilder {
    public static final int DEFAULT_MAX_FRAME_SIZE = 8191;
    private final int defaultSizeBits;
    private final int defaultIndexBits;
    private byte[] fragment;
    private int fragmentLength;
    private long fragmentRtpTimestamp;
    private long fragmentTimestamp;
    private Track fragmentTrack;
    private int lastSequence = -1;

    public RtpAACFrameBuilder(int maxFrameSize, @NonNull String aacMode) {
        super(maxFrameSize);
        if (maxFrameSize <= 0) throw new IllegalArgumentException("Frame size must be positive");
        if (!"AAC-lbr".equalsIgnoreCase(aacMode) && !"AAC-hbr".equalsIgnoreCase(aacMode)) {
            throw new IllegalArgumentException("Unsupported AAC RTP mode: " + aacMode);
        }
        defaultSizeBits = "AAC-lbr".equalsIgnoreCase(aacMode) ? 6 : 13;
        defaultIndexBits = "AAC-lbr".equalsIgnoreCase(aacMode) ? 2 : 3;
    }

    public RtpAACFrameBuilder(@NonNull String aacMode) {
        this(DEFAULT_MAX_FRAME_SIZE, aacMode);
    }

    @Nullable
    @Override
    public Frame[] pull(@NonNull Sample sample) throws BufferOverflowException {
        if (sample instanceof RtpSample && ((RtpSample) sample).discontinuity) clear();
        int sizeBits = parameter(sample, "sizelength", "sizeLength", defaultSizeBits);
        int indexBits = parameter(sample, "indexlength", "indexLength", defaultIndexBits);
        int deltaBits = parameter(sample, "indexdeltalength", "indexDeltaLength", defaultIndexBits);
        AccessUnits units = parseHeaders(sample.getArray(), sample.getOffset(), sample.getLength(),
                sizeBits, indexBits, deltaBits);
        if (units == null) { clear(); return null; }
        for (int size : units.sizes) {
            if (size > maxFrameSize) { clear(); throw new BufferOverflowException(); }
        }
        int payloadSize = sample.getOffset() + sample.getLength() - units.payloadOffset;
        long rtpTimestamp = sample instanceof RtpSample
                ? ((RtpSample) sample).rtpTimestamp : sample.getTimestamp();
        int sequence = sample instanceof RtpSample ? ((RtpSample) sample).sequenceNumber : -1;
        if (fragment != null && (units.sizes.size() != 1 || fragment.length != units.sizes.get(0)
                || fragmentRtpTimestamp != rtpTimestamp || sample.getTrack() != fragmentTrack
                || (sequence != -1 && lastSequence != -1 && sequence != ((lastSequence + 1) & 0xffff)))) {
            clear();
        }
        if (units.sizes.size() == 1 && (fragment != null || payloadSize < units.sizes.get(0))) {
            int size = units.sizes.get(0);
            if (fragment == null) {
                if (payloadSize == 0 || (sample instanceof RtpSample && ((RtpSample) sample).marker)) return null;
                fragment = new byte[size];
                fragmentRtpTimestamp = rtpTimestamp;
                fragmentTimestamp = sample.getTimestamp();
                fragmentTrack = sample.getTrack();
            }
            if (payloadSize > size - fragmentLength) { clear(); return null; }
            System.arraycopy(sample.getArray(), units.payloadOffset, fragment, fragmentLength, payloadSize);
            fragmentLength += payloadSize;
            lastSequence = sequence;
            if (fragmentLength != size) {
                if (sample instanceof RtpSample && ((RtpSample) sample).marker) clear();
                return null;
            }
            Frame frame = new Frame(fragment, fragmentTimestamp, fragmentTrack, Frame.SYNC_FRAME);
            clear();
            return new Frame[]{frame};
        }
        clear();
        long totalSize = 0;
        for (int size : units.sizes) totalSize += size;
        if (totalSize != payloadSize) return null;
        int duration = parameter(sample, "constantduration", "constantDuration", 1024);
        if (duration <= 0 || sample.getTrack().getClockRate() <= 0) return null;
        Frame[] frames = new Frame[units.sizes.size()];
        int position = units.payloadOffset;
        long index = 0;
        for (int i = 0; i < frames.length; i++) {
            int size = units.sizes.get(i);
            if (i > 0) index += (long) units.indices.get(i) + 1;
            byte[] data = Arrays.copyOfRange(sample.getArray(), position, position + size);
            long timestamp = sample.getTimestamp() + index * duration * 1000 / sample.getTrack().getClockRate();
            frames[i] = new Frame(data, timestamp, sample.getTrack(), Frame.SYNC_FRAME);
            position += size;
        }
        return frames;
    }

    @Override
    public int getBufferSize() { return fragmentLength; }

    @Override
    public void clear() {
        fragment = null;
        fragmentLength = 0;
        fragmentTrack = null;
        lastSequence = -1;
    }

    /** Compatibility helper for a packet containing one complete access unit. */
    @Nullable
    public byte[] processRtpPacketAndGetSample(@NonNull byte[] data, int length, int off) {
        AccessUnits units = parseHeaders(data, off, length, defaultSizeBits, defaultIndexBits, defaultIndexBits);
        if (units == null || units.sizes.size() != 1
                || units.sizes.get(0) != off + length - units.payloadOffset) return null;
        return Arrays.copyOfRange(data, units.payloadOffset, off + length);
    }

    private static int parameter(Sample sample, String key, String legacyKey, int fallback) {
        Object value = sample.getTrack().getMetadata(key);
        if (value == null) value = sample.getTrack().getMetadata(legacyKey);
        if (value == null) return fallback;
        try { return Integer.parseInt(value.toString()); }
        catch (NumberFormatException invalid) { return -1; }
    }

    @Nullable
    private static AccessUnits parseHeaders(byte[] data, int offset, int length,
                                           int sizeBits, int indexBits, int deltaBits) {
        if (offset < 0 || length < 2 || offset > data.length - length
                || sizeBits < 1 || sizeBits > 31 || indexBits < 0 || indexBits > 31
                || deltaBits < 0 || deltaBits > 31) return null;
        int headerBits = ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
        int headerBytes = (headerBits + 7) / 8;
        if (headerBits < sizeBits + indexBits || headerBytes > length - 2) return null;
        int firstBits = sizeBits + indexBits;
        int remaining = headerBits - firstBits;
        if (remaining % (sizeBits + deltaBits) != 0) return null;
        int count = 1 + remaining / (sizeBits + deltaBits);
        ParsableBitArray bits = new ParsableBitArray(data, offset + 2 + headerBytes);
        bits.setPosition((offset + 2) * 8);
        AccessUnits result = new AccessUnits(offset + 2 + headerBytes);
        for (int i = 0; i < count; i++) {
            int size = bits.readBits(sizeBits);
            if (size <= 0) return null;
            result.sizes.add(size);
            result.indices.add(bits.readBits(i == 0 ? indexBits : deltaBits));
        }
        return result;
    }

    private static final class AccessUnits {
        final int payloadOffset;
        final List<Integer> sizes = new ArrayList<>();
        final List<Integer> indices = new ArrayList<>();
        AccessUnits(int payloadOffset) { this.payloadOffset = payloadOffset; }
    }

    public static final class ParsableBitArray {

        public byte[] data;

        // The offset within the data, stored as the current byte offset, and the bit offset within that
        // byte (from 0 to 7).
        private int byteOffset;
        private int bitOffset;
        private int byteLimit;

        /**
         * Creates a new instance that initially has no backing data.
         */
        public ParsableBitArray() {
            data = new byte[0];
        }

        /**
         * Creates a new instance that wraps an existing array.
         *
         * @param data The data to wrap.
         */
        public ParsableBitArray(byte[] data) {
            this(data, data.length);
        }

        /**
         * Creates a new instance that wraps an existing array.
         *
         * @param data  The data to wrap.
         * @param limit The limit in bytes.
         */
        public ParsableBitArray(byte[] data, int limit) {
            this.data = data;
            byteLimit = limit;
        }

        /**
         * Updates the instance to wrap {@code data}, and resets the position to zero.
         *
         * @param data The array to wrap.
         */
        public void reset(byte[] data) {
            reset(data, data.length);
        }

        /**
         * Sets this instance's data, position and limit to match the provided {@code parsableByteArray}.
         * Any modifications to the underlying data array will be visible in both instances
         *
         * @param parsableByteArray The {@link ParsableByteArray}.
         */
        public void reset(ParsableByteArray parsableByteArray) {
            reset(parsableByteArray.getData(), parsableByteArray.limit());
            setPosition(parsableByteArray.getPosition() * 8);
        }

        /**
         * Updates the instance to wrap {@code data}, and resets the position to zero.
         *
         * @param data  The array to wrap.
         * @param limit The limit in bytes.
         */
        public void reset(byte[] data, int limit) {
            this.data = data;
            byteOffset = 0;
            bitOffset = 0;
            byteLimit = limit;
        }

        /**
         * Returns the number of bits yet to be read.
         */
        public int bitsLeft() {
            return (byteLimit - byteOffset) * 8 - bitOffset;
        }

        /**
         * Returns the current bit offset.
         */
        public int getPosition() {
            return byteOffset * 8 + bitOffset;
        }

        /**
         * Returns the current byte offset. Must only be called when the position is byte aligned.
         *
         * @throws IllegalStateException If the position isn't byte aligned.
         */
        public int getBytePosition() {
            return byteOffset;
        }

        /**
         * Sets the current bit offset.
         *
         * @param position The position to set.
         */
        public void setPosition(int position) {
            byteOffset = position / 8;
            bitOffset = position - (byteOffset * 8);
        }

        /**
         * Skips a single bit.
         */
        public void skipBit() {
            if (++bitOffset == 8) {
                bitOffset = 0;
                byteOffset++;
            }
        }

        /**
         * Skips bits and moves current reading position forward.
         *
         * @param numBits The number of bits to skip.
         */
        public void skipBits(int numBits) {
            int numBytes = numBits / 8;
            byteOffset += numBytes;
            bitOffset += numBits - (numBytes * 8);
            if (bitOffset > 7) {
                byteOffset++;
                bitOffset -= 8;
            }
        }

        /**
         * Reads a single bit.
         *
         * @return Whether the bit is set.
         */
        public boolean readBit() {
            boolean returnValue = (data[byteOffset] & (0x80 >> bitOffset)) != 0;
            skipBit();
            return returnValue;
        }

        /**
         * Reads up to 32 bits.
         *
         * @param numBits The number of bits to read.
         * @return An integer whose bottom {@code numBits} bits hold the read data.
         */
        public int readBits(int numBits) {
            if (numBits == 0) {
                return 0;
            }
            int returnValue = 0;
            bitOffset += numBits;
            while (bitOffset > 8) {
                bitOffset -= 8;
                returnValue |= (data[byteOffset++] & 0xFF) << bitOffset;
            }
            returnValue |= (data[byteOffset] & 0xFF) >> (8 - bitOffset);
            returnValue &= 0xFFFFFFFF >>> (32 - numBits);
            if (bitOffset == 8) {
                bitOffset = 0;
                byteOffset++;
            }
            return returnValue;
        }

        /**
         * Reads the next {@code length} bytes into {@code buffer}. Must only be called when the position
         * is byte aligned.
         *
         * @param buffer The array into which the read data should be written.
         * @param offset The offset in {@code buffer} at which the read data should be written.
         * @param length The number of bytes to read.
         * @throws IllegalStateException If the position isn't byte aligned.
         * @see System#arraycopy(Object, int, Object, int, int)
         */
        public void readBytes(byte[] buffer, int offset, int length) {
            System.arraycopy(data, byteOffset, buffer, offset, length);
            byteOffset += length;
        }


    }

    /**
     * Wraps a byte array, providing a set of methods for parsing data from it. Numerical values are
     * parsed with the assumption that their constituent bytes are in big endian order.
     */
    public static final class ParsableByteArray {

        private final char[] CR_AND_LF = {'\r', '\n'};
        private final char[] LF = {'\n'};

        private byte[] data;
        private int position;
        private int limit;

        /** Creates a new instance that initially has no backing data. */
        public ParsableByteArray() {
            data = new byte[0];
        }

        /**
         * Creates a new instance that wraps an existing array.
         *
         * @param data The data to wrap.
         * @param limit The limit to set.
         */
        public ParsableByteArray(byte[] data, int limit) {
            this.data = data;
            this.limit = limit;
        }

        /**
         * Resets the position to zero and the limit to the specified value. This might replace or wipe
         * the {@link #getData() underlying array}, potentially invalidating any local references.
         *
         * @param limit The limit to set.
         */
        public void reset(int limit) {
            reset(capacity() < limit ? new byte[limit] : data, limit);
        }

        /**
         * Updates the instance to wrap {@code data}, and resets the position to zero.
         *
         * @param data The array to wrap.
         * @param limit The limit to set.
         */
        public void reset(byte[] data, int limit) {
            this.data = data;
            this.limit = limit;
            position = 0;
        }

        /** Returns the number of bytes yet to be read. */
        public int bytesLeft() {
            return limit - position;
        }

        /** Returns the limit. */
        public int limit() {
            return limit;
        }

        /** Returns the current offset in the array, in bytes. */
        public int getPosition() {
            return position;
        }

        /**
         * Returns the underlying array.
         *
         * <p>Changes to this array are reflected in the results of the {@code read...()} methods.

         */
        public byte[] getData() {
            return data;
        }

        /** Returns the capacity of the array, which may be larger than the limit. */
        public int capacity() {
            return data.length;
        }

        /**
         * Reads the next {@code length} bytes into {@code buffer} at {@code offset}.
         *
         * @see System#arraycopy(Object, int, Object, int, int)
         * @param buffer The array into which the read data should be written.
         * @param offset The offset in {@code buffer} at which the read data should be written.
         * @param length The number of bytes to read.
         */
        public void readBytes(byte[] buffer, int offset, int length) {
            System.arraycopy(data, position, buffer, offset, length);
            position += length;
        }

        /** Reads the next two bytes as a signed value. */
        public short readShort() {
            return (short) ((data[position++] & 0xFF) << 8 | (data[position++] & 0xFF));
        }

    }
}
