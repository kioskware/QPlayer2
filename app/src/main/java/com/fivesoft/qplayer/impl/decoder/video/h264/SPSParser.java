package com.fivesoft.qplayer.impl.decoder.video.h264;

import com.fivesoft.qplayer.common.Constants;

import java.util.Arrays;
import java.util.Objects;

public class SPSParser {

    private static final int[][] ASPECT_RATIOS = {
            {0, 0}, {1, 1}, {12, 11}, {10, 11}, {16, 11}, {40, 33},
            {24, 11}, {20, 11}, {32, 11}, {80, 33}, {18, 11}, {15, 11},
            {64, 33}, {160, 99}, {4, 3}, {3, 2}, {2, 1}
    };

    public VideoParams parseSPS(byte[] src, int off) {
        Objects.requireNonNull(src, "src");
        return parseSPS(src, off, src.length - off);
    }

    public VideoParams parseSPS(byte[] src, int off, int len) {
        Objects.requireNonNull(src, "src");
        if (off < 0 || len <= 0 || off > src.length - len) {
            throw new IllegalArgumentException("Invalid SPS range");
        }

        int nalOffset = off + H264Util.startsWithNalPrefix(src, off, len);
        int end = off + len;
        if (nalOffset >= end || (src[nalOffset] & 0x1F) != H264Util.NAL_UNIT_TYPE_SPS) {
            throw new IllegalArgumentException("NAL unit is not an SPS");
        }

        byte[] rbsp = removeEmulationPreventionBytes(src, nalOffset + 1, end - nalOffset - 1);
        BitReader bits = new BitReader(rbsp);

        int profileIdc = bits.readInt(8);
        bits.skipBits(8); // constraint_set flags and reserved_zero_2bits
        bits.skipBits(8); // level_idc
        bits.readUnsignedExpGolomb(); // seq_parameter_set_id

        int chromaFormatIdc = 1;
        boolean separateColourPlane = false;
        if (isExtendedProfile(profileIdc)) {
            chromaFormatIdc = bits.readUnsignedExpGolomb();
            if (chromaFormatIdc > 3) {
                throw new IllegalArgumentException("Invalid chroma_format_idc");
            }
            if (chromaFormatIdc == 3) {
                separateColourPlane = bits.readBit();
            }
            bits.readUnsignedExpGolomb(); // bit_depth_luma_minus8
            bits.readUnsignedExpGolomb(); // bit_depth_chroma_minus8
            bits.skipBits(1); // qpprime_y_zero_transform_bypass_flag
            if (bits.readBit()) { // seq_scaling_matrix_present_flag
                int scalingListCount = chromaFormatIdc == 3 ? 12 : 8;
                for (int i = 0; i < scalingListCount; i++) {
                    if (bits.readBit()) {
                        skipScalingList(bits, i < 6 ? 16 : 64);
                    }
                }
            }
        }

        bits.readUnsignedExpGolomb(); // log2_max_frame_num_minus4
        int picOrderCntType = bits.readUnsignedExpGolomb();
        if (picOrderCntType == 0) {
            bits.readUnsignedExpGolomb(); // log2_max_pic_order_cnt_lsb_minus4
        } else if (picOrderCntType == 1) {
            bits.skipBits(1); // delta_pic_order_always_zero_flag
            bits.readSignedExpGolomb(); // offset_for_non_ref_pic
            bits.readSignedExpGolomb(); // offset_for_top_to_bottom_field
            int cycleLength = bits.readUnsignedExpGolomb();
            for (int i = 0; i < cycleLength; i++) {
                bits.readSignedExpGolomb();
            }
        } else if (picOrderCntType > 2) {
            throw new IllegalArgumentException("Invalid pic_order_cnt_type");
        }

        bits.readUnsignedExpGolomb(); // max_num_ref_frames
        bits.skipBits(1); // gaps_in_frame_num_value_allowed_flag
        int picWidthInMbsMinus1 = bits.readUnsignedExpGolomb();
        int picHeightInMapUnitsMinus1 = bits.readUnsignedExpGolomb();
        boolean frameMbsOnly = bits.readBit();
        if (!frameMbsOnly) {
            bits.skipBits(1); // mb_adaptive_frame_field_flag
        }
        bits.skipBits(1); // direct_8x8_inference_flag

        int cropLeft = 0;
        int cropRight = 0;
        int cropTop = 0;
        int cropBottom = 0;
        if (bits.readBit()) { // frame_cropping_flag
            cropLeft = bits.readUnsignedExpGolomb();
            cropRight = bits.readUnsignedExpGolomb();
            cropTop = bits.readUnsignedExpGolomb();
            cropBottom = bits.readUnsignedExpGolomb();
        }

        int sarWidth = 1;
        int sarHeight = 1;
        float frameRate = Constants.UNKNOWN_VALUE;
        if (bits.bitsLeft() > 0 && bits.readBit()) { // vui_parameters_present_flag
            try {
                VuiParams vui = parseVui(bits);
                sarWidth = vui.sarWidth;
                sarHeight = vui.sarHeight;
                frameRate = vui.frameRate;
            } catch (IllegalArgumentException ignored) {
                // Dimensions are still useful when a camera sends truncated optional VUI data.
            }
        }

        int chromaArrayType = separateColourPlane ? 0 : chromaFormatIdc;
        int subWidthC = chromaArrayType == 1 || chromaArrayType == 2 ? 2 : 1;
        int subHeightC = chromaArrayType == 1 ? 2 : 1;
        int cropUnitX = chromaArrayType == 0 ? 1 : subWidthC;
        int cropUnitY = (chromaArrayType == 0 ? 1 : subHeightC) * (frameMbsOnly ? 1 : 2);

        long width = (long) (picWidthInMbsMinus1 + 1) * 16
                - (long) (cropLeft + cropRight) * cropUnitX;
        long height = (long) (picHeightInMapUnitsMinus1 + 1) * 16 * (frameMbsOnly ? 1 : 2)
                - (long) (cropTop + cropBottom) * cropUnitY;
        if (width <= 0 || height <= 0 || width > Integer.MAX_VALUE || height > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid SPS dimensions");
        }

        return new VideoParams((int) width, (int) height, frameRate, sarWidth, sarHeight);
    }

    public static VideoParams parseSPSStatic(byte[] src, int off) {
        return new SPSParser().parseSPS(src, off);
    }

    public static VideoParams parseSPSStatic(byte[] src, int off, int len) {
        return new SPSParser().parseSPS(src, off, len);
    }

    private static boolean isExtendedProfile(int profileIdc) {
        switch (profileIdc) {
            case 44:
            case 83:
            case 86:
            case 100:
            case 110:
            case 118:
            case 122:
            case 128:
            case 134:
            case 135:
            case 138:
            case 139:
            case 244:
                return true;
            default:
                return false;
        }
    }

    private static void skipScalingList(BitReader bits, int size) {
        int lastScale = 8;
        int nextScale = 8;
        for (int i = 0; i < size; i++) {
            if (nextScale != 0) {
                nextScale = (lastScale + bits.readSignedExpGolomb() + 256) % 256;
            }
            if (nextScale != 0) {
                lastScale = nextScale;
            }
        }
    }

    private static VuiParams parseVui(BitReader bits) {
        int sarWidth = 1;
        int sarHeight = 1;
        if (bits.readBit()) { // aspect_ratio_info_present_flag
            int aspectRatioIdc = bits.readInt(8);
            if (aspectRatioIdc == 255) {
                int extendedSarWidth = bits.readInt(16);
                int extendedSarHeight = bits.readInt(16);
                if (extendedSarWidth > 0 && extendedSarHeight > 0) {
                    sarWidth = extendedSarWidth;
                    sarHeight = extendedSarHeight;
                }
            } else if (aspectRatioIdc > 0 && aspectRatioIdc < ASPECT_RATIOS.length) {
                sarWidth = ASPECT_RATIOS[aspectRatioIdc][0];
                sarHeight = ASPECT_RATIOS[aspectRatioIdc][1];
            }
        }
        if (bits.readBit()) { // overscan_info_present_flag
            bits.skipBits(1);
        }
        if (bits.readBit()) { // video_signal_type_present_flag
            bits.skipBits(4);
            if (bits.readBit()) { // colour_description_present_flag
                bits.skipBits(24);
            }
        }
        if (bits.readBit()) { // chroma_loc_info_present_flag
            bits.readUnsignedExpGolomb();
            bits.readUnsignedExpGolomb();
        }

        float frameRate = Constants.UNKNOWN_VALUE;
        if (bits.readBit()) { // timing_info_present_flag
            long numUnitsInTick = bits.readBits(32);
            long timeScale = bits.readBits(32);
            boolean fixedFrameRate = bits.readBit();
            if (fixedFrameRate && numUnitsInTick > 0 && timeScale > 0) {
                frameRate = (float) (timeScale / (2.0 * numUnitsInTick));
            }
        }
        return new VuiParams(sarWidth, sarHeight, frameRate);
    }

    private static byte[] removeEmulationPreventionBytes(byte[] src, int off, int len) {
        byte[] result = new byte[len];
        int output = 0;
        int zeroCount = 0;
        for (int i = off; i < off + len; i++) {
            int value = src[i] & 0xFF;
            if (zeroCount == 2 && value == 3) {
                zeroCount = 0;
                continue;
            }
            result[output++] = src[i];
            zeroCount = value == 0 ? zeroCount + 1 : 0;
        }
        return Arrays.copyOf(result, output);
    }

    private static final class VuiParams {
        final int sarWidth;
        final int sarHeight;
        final float frameRate;

        VuiParams(int sarWidth, int sarHeight, float frameRate) {
            this.sarWidth = sarWidth;
            this.sarHeight = sarHeight;
            this.frameRate = frameRate;
        }
    }

    private static final class BitReader {
        private final byte[] data;
        private int bitOffset;

        BitReader(byte[] data) {
            this.data = data;
        }

        boolean readBit() {
            return readBits(1) != 0;
        }

        int readInt(int count) {
            return (int) readBits(count);
        }

        long readBits(int count) {
            if (count < 0 || count > 32 || count > bitsLeft()) {
                throw new IllegalArgumentException("Unexpected end of SPS");
            }
            long result = 0;
            for (int i = 0; i < count; i++) {
                int byteIndex = bitOffset >> 3;
                int bitIndex = 7 - (bitOffset & 7);
                result = (result << 1) | ((data[byteIndex] >> bitIndex) & 1);
                bitOffset++;
            }
            return result;
        }

        void skipBits(int count) {
            readBits(count);
        }

        int readUnsignedExpGolomb() {
            int leadingZeros = 0;
            while (!readBit()) {
                if (++leadingZeros > 31) {
                    throw new IllegalArgumentException("Invalid Exp-Golomb value");
                }
            }
            long suffix = leadingZeros == 0 ? 0 : readBits(leadingZeros);
            long value = (1L << leadingZeros) - 1 + suffix;
            if (value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Exp-Golomb value is too large");
            }
            return (int) value;
        }

        int readSignedExpGolomb() {
            int value = readUnsignedExpGolomb();
            return (value & 1) == 0 ? -(value / 2) : (value + 1) / 2;
        }

        int bitsLeft() {
            return data.length * 8 - bitOffset;
        }
    }

    public static class VideoParams {
        public final int width;
        public final int height;
        public final float frameRate;
        public final int sarWidth;
        public final int sarHeight;

        public VideoParams(int width, int height, float frameRate) {
            this(width, height, frameRate, 1, 1);
        }

        public VideoParams(int width, int height, float frameRate, int sarWidth, int sarHeight) {
            this.width = Math.max(Constants.UNKNOWN_VALUE, width);
            this.height = Math.max(Constants.UNKNOWN_VALUE, height);
            this.frameRate = Math.max(Constants.UNKNOWN_VALUE, frameRate);
            this.sarWidth = sarWidth > 0 ? sarWidth : 1;
            this.sarHeight = sarHeight > 0 ? sarHeight : 1;
        }

        public float getPixelAspectRatio() {
            return (float) sarWidth / sarHeight;
        }

        @Override
        public String toString() {
            return "VideoParams{" +
                    "width=" + width +
                    ", height=" + height +
                    ", frameRate=" + frameRate +
                    ", sarWidth=" + sarWidth +
                    ", sarHeight=" + sarHeight +
                    '}';
        }
    }
}
