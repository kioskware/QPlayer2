package com.fivesoft.qplayer.impl.decoder.video.h264;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Frame;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.common.Constants;
import com.fivesoft.qplayer.core.FrameBuilder;

public class RtpH264FrameBuilder extends FrameBuilder {

    private volatile Frame[] resArray = new Frame[1];

    private final byte[] buf;
    private volatile int off;
    private volatile int len;

    private volatile byte[] nalUnit;
    private volatile long currentTimestamp;
    private volatile byte frameType;
    private volatile byte currentFragmentNalType = H264Util.NAL_UNIT_TYPE_UNSPECIFIED;

    /**
     * Creates Frame builder with specified max frame size.<br>
     *
     * @param maxFrameSize If a frame is bigger than this value, {@link BufferOverflowException}
     *                     will be thrown by {@link #pull(Sample sampe)} method.<br>
     */
    public RtpH264FrameBuilder(int maxFrameSize) {
        super(maxFrameSize);
        buf = new byte[maxFrameSize];
    }

    /**
     * Creates Frame builder with default max frame size ({@link #DEFAULT_MAX_FRAME_SIZE}).<br>
     * @see #RtpH264FrameBuilder(int)
     */

    public RtpH264FrameBuilder() {
        this(DEFAULT_MAX_FRAME_SIZE);
    }

    @Nullable
    @Override
    public Frame[] pull(@NonNull Sample sample) throws BufferOverflowException {

        byte[] data = sample.getArray();
        int length = sample.getLength();

        int nalType = data[0] & 0x1F;
        int packFlag = data[1] & 0xC0;

        switch (nalType) {
            case H264Util.NAL_UNIT_TYPE_FU_A:
                processFuAPacket(data, length, packFlag);
                break;

            case H264Util.NAL_UNIT_TYPE_STAP_A:
            case H264Util.NAL_UNIT_TYPE_STAP_B:
            case H264Util.NAL_UNIT_TYPE_MTAP16:
            case H264Util.NAL_UNIT_TYPE_MTAP24:
            case H264Util.NAL_UNIT_TYPE_FU_B:
                // TODO: Handle FU-B packets if needed
                // TODO: Handle other NAL unit types if needed
                Log.println(Log.ASSERT, "tag", "NAL unit type not supported: " + nalType);
                break;

            default:
                processSingleNalUnit(data, 0, length);
                break;
        }

        if(nalUnit == null)
            return null;

        try {
            resArray[0] = new Frame(nalUnit, sample.getTimestamp(), sample.getTrack(), frameType);
            return resArray;
        } finally {
            frameType = 0;
            currentFragmentNalType = H264Util.NAL_UNIT_TYPE_UNSPECIFIED;
            nalUnit = null;
        }
    }

    @Override
    public int getBufferSize() {
        return len;
    }

    @Override
    public void clear() {
        off = 0;
        len = 0;
        nalUnit = null;
    }

    private void processFuAPacket(byte[] data, int len, int packFlag) {
        byte nalHeader = (byte) ((data[0] & 0xE0) | (data[1] & 0x1F));
        byte nalUnitType = (byte) (nalHeader & 0x1F);

        switch (packFlag) {
            case 0x80:
                // Start of a fragmented NAL unit
                currentFragmentNalType = nalUnitType;
                clear();
                writeToBuffer(Constants.H264_NAL_PREFIX); // NAL unit prefix
                writeToBuffer(nalHeader); // NAL unit header
                writeToBuffer(data, 2, len - 2); // NAL unit payload
                break;
            case 0x00:
                // Middle part of a fragmented NAL unit
                if (currentFragmentNalType == nalUnitType) {
                    writeToBuffer(data, 2, len - 2); // NAL unit payload
                }
                //else Nal type mismatch
                break;
            case 0x40:
                // End of a fragmented NAL unit
                if(currentFragmentNalType != nalUnitType){
                    //Nal type mismatch
                    return;
                }

                int nalUnitLength = this.len + len - 2;
                nalUnit = new byte[nalUnitLength];

                synchronized (buf) {
                    System.arraycopy(buf, 0, nalUnit, 0, this.len); // Buffered data
                    System.arraycopy(data, 2, nalUnit, this.len, len - 2); // NAL unit payload just received
                }

                if(H264Util.isNalUnitKeyFrame(nalUnitType)){
                    frameType = Frame.SYNC_FRAME;
                } else if(H264Util.isNAlUnitConfig(nalUnitType)){
                    frameType = Frame.CONFIG_FRAME;
                } else {
                    frameType = Frame.NON_SYNC_FRAME;
                }

                break;
        }
    }

    private void processSingleNalUnit(byte[] data, int off, int length) {
        nalUnit = new byte[4 + length];
        nalUnit[0] = 0x00;
        nalUnit[1] = 0x00;
        nalUnit[2] = 0x00;
        nalUnit[3] = 0x01;

        byte nalUnitType = H264Util.getNalUnitType(data, off, length);

        if(H264Util.isNalUnitKeyFrame(nalUnitType)){
            frameType = Frame.SYNC_FRAME;
        } else if(H264Util.isNAlUnitConfig(nalUnitType)){
            frameType = Frame.CONFIG_FRAME;
        } else {
            frameType = Frame.NON_SYNC_FRAME;
        }

        System.arraycopy(data, off, nalUnit, 4, length);
    }

    private void processStapA(byte[] data, int off, int length) {
        int pos = off + 1;
        int end = off + length;

        while (pos < end) {
            int nalUnitLength = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;

            if (pos + nalUnitLength > end) {
                // Invalid STAP-A packet
                return;
            }

            byte nalUnitType = (byte) (data[pos] & 0x1F);

            if (H264Util.isNalUnitKeyFrame(nalUnitType)) {
                frameType = Frame.SYNC_FRAME;
            } else if (H264Util.isNAlUnitConfig(nalUnitType)) {
                frameType = Frame.CONFIG_FRAME;
            } else {
                frameType = Frame.NON_SYNC_FRAME;
            }

            pos += nalUnitLength;
        }
    }

    private void processStapB(byte[] data, int off, int length) {
        int pos = off + 2;
        int end = off + length;

        while (pos < end) {
            int nalUnitLength = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;

            if (pos + nalUnitLength > end) {
                // Invalid STAP-B packet
                return;
            }

            byte nalUnitType = (byte) (data[pos] & 0x1F);

            if (H264Util.isNalUnitKeyFrame(nalUnitType)) {
                frameType = Frame.SYNC_FRAME;
            } else if (H264Util.isNAlUnitConfig(nalUnitType)) {
                frameType = Frame.CONFIG_FRAME;
            } else {
                frameType = Frame.NON_SYNC_FRAME;
            }

            pos += nalUnitLength;
        }
    }

    private void processMtap16(byte[] data, int off, int length) {
        int pos = off + 2;
        int end = off + length;

        while (pos < end) {
            int nalUnitLength = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;

            if (pos + nalUnitLength > end) {
                // Invalid MTAP16 packet
                return;
            }

            byte nalUnitType = (byte) (data[pos] & 0x1F);

            if (H264Util.isNalUnitKeyFrame(nalUnitType)) {
                frameType = Frame.SYNC_FRAME;
            } else if (H264Util.isNAlUnitConfig(nalUnitType)) {
                frameType = Frame.CONFIG_FRAME;
            } else {
                frameType = Frame.NON_SYNC_FRAME;
            }

            pos += nalUnitLength;
        }
    }

    private void processMtap24(byte[] data, int off, int length) {
        int pos = off + 3;
        int end = off + length;

        while (pos < end) {
            int nalUnitLength = ((data[pos] & 0xFF) << 16) | ((data[pos + 1] & 0xFF) << 8) | (data[pos + 2] & 0xFF);
            pos += 3;

            if (pos + nalUnitLength > end) {
                // Invalid MTAP24 packet
                return;
            }

            byte nalUnitType = (byte) (data[pos] & 0x1F);

            if (H264Util.isNalUnitKeyFrame(nalUnitType)) {
                frameType = Frame.SYNC_FRAME;
            } else if (H264Util.isNAlUnitConfig(nalUnitType)) {
                frameType = Frame.CONFIG_FRAME;
            } else {
                frameType = Frame.NON_SYNC_FRAME;
            }

            pos += nalUnitLength;
        }
    }

    private void writeToBuffer(byte[] data, int off, int len){
        synchronized (buf) {
            System.arraycopy(data, off, buf, this.off, len);
            this.off += len;
            this.len += len;
        }
    }

    private void writeToBuffer(byte[] data){
        writeToBuffer(data, 0, data.length);
    }

    private void writeToBuffer(byte data){
        synchronized (buf) {
            buf[off++] = data;
            len++;
        }
    }

}
