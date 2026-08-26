package com.fivesoft.qplayer.impl.decoder.video.h264;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class VideoRtpParser {

    private static final String TAG = VideoRtpParser.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final int BUFFER_SIZE = 1024;

    private static final int NAL_UNIT_TYPE_STAP_A = 24;
    private static final int NAL_UNIT_TYPE_STAP_B = 25;
    private static final int NAL_UNIT_TYPE_MTAP16 = 26;
    private static final int NAL_UNIT_TYPE_MTAP24 = 27;
    private static final int NAL_UNIT_TYPE_FU_A = 28;
    private static final int NAL_UNIT_TYPE_FU_B = 29;

    private final byte[][] buffer = new byte[BUFFER_SIZE][];
    private byte[] nalUnit;
    private boolean nalEndFlag;
    private int bufferLength;
    private int packetNum = 0;

    /**
     * Processes an RTP packet and retrieves the NAL unit if available.
     * @param data   The RTP packet data.
     * @param length The length of the RTP packet data.
     * @return The NAL unit data if the packet contains a complete NAL unit, or null otherwise.
     */

    @Nullable
    public byte[] processRtpPacketAndGetNalUnit(@NonNull byte[] data, int length) {
        if (DEBUG) {
            Log.v(TAG, "processRtpPacketAndGetNalUnit(length=" + length + ")");
        }

        int nalType = data[0] & 0x1F;
        int packFlag = data[1] & 0xC0;

        if (DEBUG) {
            Log.d(TAG, "NAL type: " + nalType + ", pack flag: " + packFlag);
        }

        switch (nalType) {
            case NAL_UNIT_TYPE_FU_A:
                processFuAPacket(data, length, packFlag);
                break;

            case NAL_UNIT_TYPE_STAP_A:
            case NAL_UNIT_TYPE_STAP_B:
            case NAL_UNIT_TYPE_MTAP16:
            case NAL_UNIT_TYPE_MTAP24:
                // TODO: Handle other NAL unit types if needed
                break;

            case NAL_UNIT_TYPE_FU_B:
                // TODO: Handle FU-B packets if needed
                break;

            default:
                processSingleNalUnit(data, length);
                break;
        }

        if (nalEndFlag) {
            return nalUnit;
        } else {
            return null;
        }
    }

    private void processFuAPacket(byte[] data, int length, int packFlag) {
        switch (packFlag) {
            case 0x80:
                nalEndFlag = false;
                packetNum = 1;
                bufferLength = length - 1;
                buffer[1] = new byte[bufferLength];
                buffer[1][0] = (byte) ((data[0] & 0xE0) | (data[1] & 0x1F));
                System.arraycopy(data, 2, buffer[1], 1, length - 2);
                break;

            case 0x00:
                nalEndFlag = false;
                packetNum++;
                bufferLength += length - 2;
                buffer[packetNum] = new byte[length - 2];
                System.arraycopy(data, 2, buffer[packetNum], 0, length - 2);
                break;

            case 0x40:
                nalEndFlag = true;
                int nalUnitLength = bufferLength + length + 2;
                nalUnit = new byte[nalUnitLength];
                nalUnit[0] = 0x00;
                nalUnit[1] = 0x00;
                nalUnit[2] = 0x00;
                nalUnit[3] = 0x01;
                int tmpLen = 4;

                System.arraycopy(buffer[1], 0, nalUnit, tmpLen, buffer[1].length);
                tmpLen += buffer[1].length;

                for (int i = 2; i < packetNum + 1; ++i) {
                    System.arraycopy(buffer[i], 0, nalUnit, tmpLen, buffer[i].length);
                    tmpLen += buffer[i].length;
                }

                System.arraycopy(data, 2, nalUnit, tmpLen, length - 2);
                break;
        }
    }

    private void processSingleNalUnit(byte[] data, int length) {
        if (DEBUG) {
            Log.d(TAG, "Single NAL");
        }

        nalUnit = new byte[4 + length];
        nalUnit[0] = 0x00;
        nalUnit[1] = 0x00;
        nalUnit[2] = 0x00;
        nalUnit[3] = 0x01;
        System.arraycopy(data, 0, nalUnit, 4, length);
        nalEndFlag = true;
    }

}

