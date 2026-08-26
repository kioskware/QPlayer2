package com.fivesoft.qplayer.impl.decoder.audio.aac;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Frame;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.common.X;
import com.fivesoft.qplayer.core.FrameBuilder;

// https://tools.ietf.org/html/rfc3640
//          +---------+-----------+-----------+---------------+
//         | RTP     | AU Header | Auxiliary | Access Unit   |
//         | Header  | Section   | Section   | Data Section  |
//         +---------+-----------+-----------+---------------+
//
//                   <----------RTP Packet Payload----------->

public class RtpAACFrameBuilder extends FrameBuilder {

    public static final int DEFAULT_MAX_FRAME_SIZE = 8191;

    private final ParsableBitArray headerScratchBits;
    private final ParsableByteArray headerScratchBytes;

    private static final int MODE_LBR = 0;
    private static final int MODE_HBR = 1;

    // Number of bits for AAC AU sizes, indexed by mode (LBR and HBR)
    private static final int[] NUM_BITS_AU_SIZES = {6, 13};

    // Number of bits for AAC AU index(-delta), indexed by mode (LBR and HBR)
    private static final int[] NUM_BITS_AU_INDEX = {2, 3};

    // Frame Sizes for AAC AU fragments, indexed by mode (LBR and HBR)
    private static final int[] FRAME_SIZES = {63, 8191};

    private final int _aacMode;
    private boolean completeFrameIndicator = true;

    public RtpAACFrameBuilder(int maxFrameSize, @NonNull String aacMode) {
        super(maxFrameSize);
        _aacMode = aacMode.equalsIgnoreCase("AAC-lbr") ? MODE_LBR : MODE_HBR;
        headerScratchBits = new ParsableBitArray();
        headerScratchBytes = new ParsableByteArray();
    }

    public RtpAACFrameBuilder(@NonNull String aacMode) {
        this(DEFAULT_MAX_FRAME_SIZE, aacMode);
    }

    @Nullable
    @Override
    public Frame[] pull(@NonNull Sample sample) throws BufferOverflowException {
        byte[] data = processRtpPacketAndGetSample(
                sample.getArray(),
                sample.getLength(),
                sample.getOffset());

        if (data == null) {
            return null;
        }

        return new Frame[]{
                new Frame(data, 0, data.length, sample.getTimestamp(), sample.getTrack(), Frame.SYNC_FRAME)
        };
    }

    @Override
    public int getBufferSize() {
        return 0;
    }

    @Override
    public void clear() {

    }

    @Nullable
    public byte[] processRtpPacketAndGetSample(@NonNull byte[] data, int length, int off) {
        int auHeadersCount = 1;
        int numBitsAuSize = NUM_BITS_AU_SIZES[_aacMode];
        int numBitsAuIndex = NUM_BITS_AU_INDEX[_aacMode];

        ParsableByteArray packet = new ParsableByteArray(data, length);

//      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- .. -+-+-+-+-+-+-+-+-+-+
//      |AU-headers-length|AU-header|AU-header|      |AU-header|padding|
//      |                 |   (1)   |   (2)   |      |   (n)   | bits  |
//      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- .. -+-+-+-+-+-+-+-+-+-+
        int auHeadersLength = packet.readShort();//((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        int auHeadersLengthBytes = (auHeadersLength + 7) / 8;

        headerScratchBytes.reset(auHeadersLengthBytes);
        packet.readBytes(headerScratchBytes.getData(), 0, auHeadersLengthBytes);
        headerScratchBits.reset(headerScratchBytes.getData());

        int bitsAvailable = auHeadersLength - (numBitsAuSize + numBitsAuIndex);

        if (bitsAvailable > 0) {// && (numBitsAuSize + numBitsAuSize) > 0) {
            auHeadersCount +=  bitsAvailable / (numBitsAuSize + numBitsAuIndex);
        }

        if (auHeadersCount == 1) {
            int auSize = headerScratchBits.readBits(numBitsAuSize);
            int auIndex = headerScratchBits.readBits(numBitsAuIndex);

            if (completeFrameIndicator) {
                if (auIndex == 0) {
                    if (packet.bytesLeft() == auSize) {
                        return handleSingleAacFrame(packet);

                    } else {
//                        handleFragmentationAacFrame(packet, auSize);
                    }
                }
            } else {
//                handleFragmentationAacFrame(packet, auSize);
            }

        } else {
            if (completeFrameIndicator) {
//                handleMultipleAacFrames(packet, auHeadersLength);
            }
        }
//        byte[] auHeader = new byte[length-2-auHeadersLengthBytes];
//        System.arraycopy(data,2-auHeadersLengthBytes, auHeader,0, auHeader.length);
//        if (DEBUG)
//            Log.d(TAG, "AU headers size: " + auHeadersLengthBytes + ", AU headers: " + auHeadersCount + ", sample length: " + auHeader.length);
//        return auHeader;
        return new byte[0];
    }

    private byte[] handleSingleAacFrame(ParsableByteArray packet) {
        int length = packet.bytesLeft();
        byte[] data = new byte[length];
        System.arraycopy(packet.getData(), packet.getPosition(), data,0, data.length);
        return data;
    }

    /**
     * Wraps a byte array, providing methods that allow it to be read as a bitstream.
     */
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
