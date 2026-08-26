package com.fivesoft.qplayer.impl.decoder.video.h264;

import com.fivesoft.qplayer.common.Constants;

import java.util.Arrays;

public class SPSParser {

    private int spsParserOffset;

    private int readBits(byte[] buffer, int count) {
        int result = 0;
        int index = (spsParserOffset / 8);
        int bitNumber = (spsParserOffset - (index * 8));
        int outBitNumber = count - 1;
        for (int c = 0; c < count; c++) {
            if ((buffer[index] << bitNumber & 0x80) != 0) {
                result |= (1 << outBitNumber);
            }
            if (++bitNumber > 7) {
                bitNumber = 0;
                index++;
            }
            outBitNumber--;
        }
        spsParserOffset += count;
        return result;
    }

    private int readUEG(byte[] buffer) {
        int bitcount = 0;

        while (true) {
            if (readBits(buffer, 1) == 0) {
                bitcount++;
            } else {
                break;
            }
        }

        int result = 0;
        if (bitcount > 0) {
            int val = readBits(buffer, bitcount);
            result = (1 << bitcount) - 1 + val;
        }

        return result;
    }

    private int readEG(byte[] buffer) {
        int value = readUEG(buffer);
        if ((value & 0x01) != 0) {
            return (value + 1) / 2;
        } else {
            return -(value / 2);
        }
    }

    private void skipScalingList(byte[] buffer, int count) {
        int deltaScale, lastScale = 8, nextScale = 8;
        for (int j = 0; j < count; j++) {
            if (nextScale != 0) {
                deltaScale = readEG(buffer);
                nextScale = (lastScale + deltaScale + 256) % 256;
            }
            lastScale = (nextScale == 0 ? lastScale : nextScale);
        }
    }

    public VideoParams parseSPS(byte[] src, int off) {

        src = Arrays.copyOfRange(src, off, src.length);

        int profileIdc;
        int pictOrderCntType;
        int picWidthInMbsMinus1;
        int picHeightInMapUnitsMinus1;
        int frameMbsOnlyFlag;
        int frameCropLeftOffset = 0;
        int frameCropRightOffset = 0;
        int frameCropTopOffset = 0;
        int frameCropBottomOffset = 0;

        spsParserOffset = off;
        readBits(src, 8);
        profileIdc = readBits(src, 8);
        readBits(src, 16);
        readUEG(src);

        if (profileIdc == 100 || profileIdc == 110 || profileIdc == 122 ||
            profileIdc == 244 || profileIdc == 44 || profileIdc == 83 ||
            profileIdc == 86 || profileIdc == 118 || profileIdc == 128) {
            int chromaFormatIdc = readUEG(src);
            if (chromaFormatIdc == 3) {
                readBits(src, 1);
            }
            readUEG(src);
            readUEG(src);
            readBits(src, 1);
            if (readBits(src, 1) != 0) {
                for (int i = 0; i < (chromaFormatIdc != 3 ? 8 : 12); i++) {
                    if (readBits(src, 1) != 0) {
                        if (i < 6) {
                            skipScalingList(src, 16);
                        } else {
                            skipScalingList(src, 64);
                        }
                    }
                }
            }
        }

        readUEG(src);
        pictOrderCntType = readUEG(src);

        if (pictOrderCntType == 0) {
            readUEG(src);
        } else if (pictOrderCntType == 1) {
            readBits(src, 1);
            readEG(src);
            readEG(src);
            for (int i = 0; i < readUEG(src); i++) {
                readEG(src);
            }
        }

        readUEG(src);
        readBits(src, 1);
        picWidthInMbsMinus1 = readUEG(src);
        picHeightInMapUnitsMinus1 = readUEG(src);
        frameMbsOnlyFlag = readBits(src, 1);
        if (frameMbsOnlyFlag == 0) {
            readBits(src, 1);
        }
        readBits(src, 1);
        if (readBits(src, 1) != 0) {
            frameCropLeftOffset = readUEG(src);
            frameCropRightOffset = readUEG(src);
            frameCropTopOffset = readUEG(src);
            frameCropBottomOffset = readUEG(src);
        }

        boolean vuiParametersPresentFlag = readBits(src, 1) == 1;
        int frameRate = 0;

        if (vuiParametersPresentFlag) {
            boolean aspectRatioInfoPresentFlag = readBits(src, 1) == 1;
            if (aspectRatioInfoPresentFlag) {
                int aspectRatioIdc = readBits(src, 8);
                if (aspectRatioIdc == 255) {
                    readBits(src, 16); // sar_width
                    readBits(src, 16); // sar_height
                }
            }

            boolean overscanInfoPresentFlag = readBits(src, 1) == 1;
            if (overscanInfoPresentFlag) {
                readBits(src, 1); // overscan_appropriate_flag
            }

            boolean videoSignalTypePresentFlag = readBits(src, 1) == 1;
            if (videoSignalTypePresentFlag) {
                readBits(src, 3); // video_format
                readBits(src, 1); // video_full_range_flag
                boolean colourDescriptionPresentFlag = readBits(src, 1) == 1;
                if (colourDescriptionPresentFlag) {
                    readBits(src, 8); // colour_primaries
                    readBits(src, 8); // transfer_characteristics
                    readBits(src, 8); // matrix_coefficients
                }
            }

            boolean chromaLocInfoPresentFlag = readBits(src, 1) == 1;
            if (chromaLocInfoPresentFlag) {
                readUEG(src); // chroma_sample_loc_type_top_field
                readUEG(src); // chroma_sample_loc_type_bottom_field
            }

            boolean timingInfoPresentFlag = readBits(src, 1) == 1;
            if (timingInfoPresentFlag) {
                int numUnitsInTick = readBits(src, 32); // num_units_in_tick
                int timeScale = readBits(src, 32); // time_scale
                boolean fixedFrameRateFlag = readBits(src, 1) == 1;
                if (fixedFrameRateFlag) {
                    frameRate = (int) Math.ceil((float) timeScale / numUnitsInTick) / 2;
                }
            }
        }
        int width = ((picWidthInMbsMinus1 + 1) * 16) - frameCropLeftOffset * 2 - frameCropRightOffset * 2;
        int height = (2 - frameMbsOnlyFlag) * (picHeightInMapUnitsMinus1 + 1) * 16 - ((frameMbsOnlyFlag != 0 ? 2 : 4) * (frameCropTopOffset + frameCropBottomOffset));

        return new VideoParams(width, height, frameRate);
    }

    public static VideoParams parseSPSStatic(byte[] src, int off) {
        return new SPSParser().parseSPS(src, off);
    }

    public static class VideoParams {
        public final int width;
        public final int height;
        public final int frameRate;

        public VideoParams(int width, int height, int frameRate) {
            this.width = Math.max(Constants.UNKNOWN_VALUE, width);
            this.height = Math.max(Constants.UNKNOWN_VALUE, height);
            this.frameRate = Math.max(Constants.UNKNOWN_VALUE, frameRate);
        }

        @Override
        public String toString() {
            return "VideoParams{" +
                    "width=" + width +
                    ", height=" + height +
                    ", frameRate=" + frameRate +
                    '}';
        }
    }

}
