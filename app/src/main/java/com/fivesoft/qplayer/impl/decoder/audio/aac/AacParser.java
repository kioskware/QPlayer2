package com.fivesoft.qplayer.impl.decoder.audio.aac;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// https://tools.ietf.org/html/rfc3640
//          +---------+-----------+-----------+---------------+
//         | RTP     | AU Header | Auxiliary | Access Unit   |
//         | Header  | Section   | Section   | Data Section  |
//         +---------+-----------+-----------+---------------+
//
//                   <----------RTP Packet Payload----------->
public class AacParser {

    private static final String TAG = AacParser.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final int _aacMode;
    private boolean completeFrameIndicator = true;

    private static final int MODE_LBR = 0;
    private static final int MODE_HBR = 1;

    // Number of bits for AAC AU sizes, indexed by mode (LBR and HBR)
    private static final int[] NUM_BITS_AU_SIZES = {6, 13};

    // Number of bits for AAC AU index(-delta), indexed by mode (LBR and HBR)
    private static final int[] NUM_BITS_AU_INDEX = {2, 3};

    // Frame Sizes for AAC AU fragments, indexed by mode (LBR and HBR)
    private static final int[] FRAME_SIZES = {63, 8191};

    public AacParser(@NonNull String aacMode) {
        _aacMode = aacMode.equalsIgnoreCase("AAC-lbr") ? MODE_LBR : MODE_HBR;
    }

    @Nullable
    public byte[] processRtpPacketAndGetSample(@NonNull byte[] data, int length) {
        if (DEBUG)
            Log.v(TAG, "processRtpPacketAndGetSample(length=" + length + ")");
        int auHeadersCount = 1;
        int numBitsAuSize = NUM_BITS_AU_SIZES[_aacMode];
        int numBitsAuIndex = NUM_BITS_AU_INDEX[_aacMode];

        int auHeadersLength = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        int auHeadersLengthBytes = (auHeadersLength + 7) / 8;

        int bitsAvailable = auHeadersLength - (numBitsAuSize + numBitsAuIndex);

        if (bitsAvailable > 0) {
            auHeadersCount +=  bitsAvailable / (numBitsAuSize + numBitsAuIndex);
        }

        if (auHeadersCount == 1) {
            int auSize = readBits(data, 2, numBitsAuSize);
            int auIndex = readBits(data, 2 + numBitsAuSize, numBitsAuIndex);

            if (completeFrameIndicator) {
                if (auIndex == 0) {
                    if (length == auSize + 2 + auHeadersLengthBytes) {
                        return handleSingleAacFrame(data, 2 + auHeadersLengthBytes, auSize);
                    } else {
//                        handleFragmentationAacFrame(data, 2 + auHeadersLengthBytes, auSize);
                    }
                }
            } else {
//                handleFragmentationAacFrame(data, 2 + auHeadersLengthBytes, auSize);
            }

        } else {
            if (completeFrameIndicator) {
//                handleMultipleAacFrames(data, 2 + auHeadersLengthBytes, auHeadersLength);
            }
        }

        return new byte[0];
    }

    private int readBits(byte[] data, int offset, int numBits) {
        int result = 0;
        for (int i = 0; i < numBits; i++) {
            result <<= 1;
            if ((data[offset + i / 8] & (0x80 >>> (i % 8))) != 0) {
                result |= 1;
            }
        }
        return result;
    }

    private byte[] handleSingleAacFrame(byte[] data, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(data, offset, result, 0, length);
        return result;
    }
}
