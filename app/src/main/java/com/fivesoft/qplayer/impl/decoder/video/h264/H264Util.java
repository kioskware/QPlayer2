package com.fivesoft.qplayer.impl.decoder.video.h264;

import androidx.annotation.Nullable;

import com.fivesoft.qplayer.common.Constants;

import java.util.Objects;

public class H264Util {

    public static final int NAL_UNIT_TYPE_UNSPECIFIED = 0;
    public static final int NAL_UNIT_TYPE_NON_IDR_SLICE = 1; // Coded slice of a non-IDR picture
    public static final int NAL_UNIT_TYPE_DPA_SLICE = 2; // Coded slice data partition A
    public static final int NAL_UNIT_TYPE_DPB_SLICE = 3; // Coded slice data partition B
    public static final int NAL_UNIT_TYPE_DPC_SLICE = 4; // Coded slice data partition C
    public static final int NAL_UNIT_TYPE_IDR_SLICE = 5; // Coded slice of an IDR picture
    public static final int NAL_UNIT_TYPE_SEI = 6; // Supplemental Enhancement Information
    public static final int NAL_UNIT_TYPE_SPS = 7; // Sequence Parameter Set
    public static final int NAL_UNIT_TYPE_PPS = 8; // Picture Parameter Set
    public static final int NAL_UNIT_TYPE_AUD = 9; // Access Unit Delimiter
    public static final int NAL_UNIT_TYPE_END_OF_SEQ = 10; // End of Sequence
    public static final int NAL_UNIT_TYPE_END_OF_STREAM = 11; // End of Stream
    public static final int NAL_UNIT_TYPE_FILLER_DATA = 12; // Filler Data
    public static final int NAL_UNIT_TYPE_SEQ_PARAMETER_SET_EXT = 13; // Sequence Parameter Set Extension
    public static final int NAL_UNIT_TYPE_PREFIX_NAL_UNIT = 14; // Prefix NAL Unit
    public static final int NAL_UNIT_TYPE_SUBSET_SEQ_PARAMETER_SET = 15; // Subset Sequence Parameter Set
    public static final int NAL_UNIT_TYPE_RESERVED16 = 16; // Reserved
    public static final int NAL_UNIT_TYPE_RESERVED17 = 17; // Reserved
    public static final int NAL_UNIT_TYPE_RESERVED18 = 18; // Reserved
    public static final int NAL_UNIT_TYPE_AUXILIARY_SLICE = 19; // Coded slice of an auxiliary coded picture without partitioning
    public static final int NAL_UNIT_TYPE_RESERVED20 = 20; // Reserved
    public static final int NAL_UNIT_TYPE_RESERVED21 = 21; // Reserved
    public static final int NAL_UNIT_TYPE_RESERVED22 = 22; // Reserved
    public static final int NAL_UNIT_TYPE_RESERVED23 = 23; // Reserved
    public static final int NAL_UNIT_TYPE_UNSPECIFIED24 = 24; // Unspecified
    public static final int NAL_UNIT_TYPE_UNSPECIFIED25 = 25; // Unspecified
    public static final int NAL_UNIT_TYPE_UNSPECIFIED26 = 26; // Unspecified
    public static final int NAL_UNIT_TYPE_UNSPECIFIED27 = 27; // Unspecified
    public static final int NAL_UNIT_TYPE_UNSPECIFIED28 = 28; // Unspecified
    public static final int NAL_UNIT_TYPE_UNSPECIFIED29 = 29; // Unspecified
    public static final int NAL_UNIT_TYPE_UNSPECIFIED30 = 30; // Unspecified
    public static final int NAL_UNIT_TYPE_UNSPECIFIED31 = 31; // Unspecified


    public static final int NAL_UNIT_TYPE_SLICE = 1;

    public static final int NAL_UNIT_TYPE_DPA = 2;

    public static final int NAL_UNIT_TYPE_DPB = 3;

    public static final int NAL_UNIT_TYPE_DPC = 4;

    public static final int NAL_UNIT_TYPE_IDR = 5;

    public static final int NAL_UNIT_TYPE_END_OF_SEQUENCE = 10;

    public static final int NAL_UNIT_TYPE_SPS_EXT = 13;

    public static final int NAL_UNIT_TYPE_STAP_A = 24;
    public static final int NAL_UNIT_TYPE_STAP_B = 25;
    public static final int NAL_UNIT_TYPE_MTAP16 = 26;
    public static final int NAL_UNIT_TYPE_MTAP24 = 27;
    public static final int NAL_UNIT_TYPE_FU_A = 28;
    public static final int NAL_UNIT_TYPE_FU_B = 29;

    public static byte getNalUnitType(byte[] nal, int off, int len) {
        Objects.requireNonNull(nal, "nal");
        if (off < 0 || len < 1 || off > nal.length - len) {
            throw new IllegalArgumentException("Invalid NAL unit range");
        }

        int prefixLength = startsWithNalPrefix(nal, off, len);
        if (prefixLength >= len) {
            throw new IllegalArgumentException("NAL unit contains only a prefix");
        }

        if (prefixLength == 0) {
            return (byte) (nal[off] & 0x1F);
        } else {
            return (byte) (nal[off + prefixLength] & 0x1F);
        }
    }

    public static boolean isNalUnitKeyFrame(@Nullable byte[] data, int offset, int length) {
        return isNalUnitKeyFrame(getNalUnitType(data, offset, length));
    }

    public static boolean isNalUnitKeyFrame(byte nalUnitType) {
        return nalUnitType == NAL_UNIT_TYPE_IDR;
    }

    /**
     * Returns true if the given NAL unit type is a configuration NAL unit.
     * @param nalUnitType the NAL unit type
     * @return true if the given NAL unit type is a configuration NAL unit
     */

    public static boolean isNAlUnitConfig(byte nalUnitType) {
        return nalUnitType == NAL_UNIT_TYPE_SPS || nalUnitType == NAL_UNIT_TYPE_PPS;
    }

    /**
     * Returns the length of the NAL prefix in bytes (3 or 4) or 0 if the data does not start with.
     * @return the length of the NAL prefix in bytes (3 or 4) or 0 if the data does not start with
     */

    public static int startsWithNalPrefix(byte[] data, int off, int len) {
        Objects.requireNonNull(data);

        if (off < 0 || len < 0 || off > data.length - len ||
                len < Constants.H264_NAL_PREFIX_OLD.length)
            return 0;

        if(data[off] == 0 && data[off + 1] == 0 && data[off + 2] == 1)
            return Constants.H264_NAL_PREFIX_OLD.length;

        if(len < Constants.H264_NAL_PREFIX.length)
            return 0;

        if(data[off] == 0 && data[off + 1] == 0 && data[off + 2] == 0 && data[off + 3] == 1)
            return Constants.H264_NAL_PREFIX.length;

        return 0;
    }

    public static byte[] ensureStartsWithNalPrefix(byte[] src) {
        if (src == null)
            return null;

        if (startsWithNalPrefix(src, 0, src.length) != 0) {
            return src;
        }

        byte[] dst = new byte[src.length + 4];
        dst[0] = 0;
        dst[1] = 0;
        dst[2] = 0;
        dst[3] = 1;
        System.arraycopy(src, 0, dst, 4, src.length);

        return dst;
    }


}
