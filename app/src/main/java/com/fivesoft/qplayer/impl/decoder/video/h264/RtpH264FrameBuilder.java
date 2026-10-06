package com.fivesoft.qplayer.impl.decoder.video.h264;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Frame;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.common.Constants;
import com.fivesoft.qplayer.core.FrameBuilder;
import com.fivesoft.qplayer.impl.extractor.rtp.RtpSample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** RFC 6184 packetization modes 0 and 1: single NAL, STAP-A and FU-A. */
public class RtpH264FrameBuilder extends FrameBuilder {
    private final byte[] buffer;
    private int length;
    private int fragmentHeader;
    private long fragmentTimestamp;
    private long frameTimestamp;
    private Track fragmentTrack;
    private int lastSequence = -1;

    public RtpH264FrameBuilder(int maxFrameSize) {
        super(maxFrameSize);
        if (maxFrameSize < 5) throw new IllegalArgumentException("Frame size is too small");
        buffer = new byte[maxFrameSize];
    }

    public RtpH264FrameBuilder() {
        this(DEFAULT_MAX_FRAME_SIZE);
    }

    @Nullable
    @Override
    public Frame[] pull(@NonNull Sample sample) throws BufferOverflowException {
        byte[] data = sample.getArray();
        int offset = sample.getOffset();
        int size = sample.getLength();
        if (sample instanceof RtpSample && ((RtpSample) sample).discontinuity) clear();
        if (size == 0 || (data[offset] & 0x80) != 0) {
            clear();
            return null;
        }
        int type = data[offset] & 0x1f;
        if (type == H264Util.NAL_UNIT_TYPE_FU_A) return processFuA(sample);
        clear();
        if (type == H264Util.NAL_UNIT_TYPE_STAP_A) return processStapA(sample);
        if (type < 1 || type > 23) return null;
        checkSize(Constants.H264_NAL_PREFIX.length + size);
        byte[] frameData = new byte[Constants.H264_NAL_PREFIX.length + size];
        System.arraycopy(Constants.H264_NAL_PREFIX, 0, frameData, 0, Constants.H264_NAL_PREFIX.length);
        System.arraycopy(data, offset, frameData, Constants.H264_NAL_PREFIX.length, size);
        return new Frame[]{new Frame(frameData, sample.getTimestamp(), sample.getTrack(), frameType(type))};
    }

    @Nullable
    private Frame[] processFuA(Sample sample) {
        byte[] data = sample.getArray();
        int offset = sample.getOffset();
        int size = sample.getLength();
        if (size < 2) { clear(); return null; }
        int flags = data[offset + 1] & 0xff;
        int type = flags & 0x1f;
        boolean start = (flags & 0x80) != 0;
        boolean end = (flags & 0x40) != 0;
        // RFC 6184 requires receivers to ignore the reserved FU header bit.
        if ((start && end) || type < 1 || type > 23) {
            clear();
            return null;
        }
        int header = (data[offset] & 0xe0) | type;
        long timestamp = sample instanceof RtpSample ? ((RtpSample) sample).rtpTimestamp : sample.getTimestamp();
        int sequence = sample instanceof RtpSample ? ((RtpSample) sample).sequenceNumber : -1;
        if (start) {
            clear();
            checkSize(Constants.H264_NAL_PREFIX.length + 1 + size - 2);
            System.arraycopy(Constants.H264_NAL_PREFIX, 0, buffer, 0, Constants.H264_NAL_PREFIX.length);
            length = Constants.H264_NAL_PREFIX.length;
            buffer[length++] = (byte) header;
            fragmentHeader = header;
            fragmentTimestamp = timestamp;
            frameTimestamp = sample.getTimestamp();
            fragmentTrack = sample.getTrack();
        } else if (length == 0 || header != fragmentHeader || timestamp != fragmentTimestamp
                || sample.getTrack() != fragmentTrack
                || (sequence != -1 && lastSequence != -1 && sequence != ((lastSequence + 1) & 0xffff))) {
            clear();
            return null;
        }
        checkSize(length + size - 2);
        System.arraycopy(data, offset + 2, buffer, length, size - 2);
        length += size - 2;
        lastSequence = sequence;
        if (!end) return null;
        Frame frame = new Frame(Arrays.copyOf(buffer, length), frameTimestamp, fragmentTrack, frameType(type));
        clear();
        return new Frame[]{frame};
    }

    @Nullable
    private Frame[] processStapA(Sample sample) {
        byte[] data = sample.getArray();
        int position = sample.getOffset() + 1;
        int end = sample.getOffset() + sample.getLength();
        List<Frame> frames = new ArrayList<>();
        while (position < end) {
            if (end - position < 2) return null;
            int nalSize = ((data[position] & 0xff) << 8) | (data[position + 1] & 0xff);
            position += 2;
            if (nalSize == 0 || nalSize > end - position) return null;
            int type = data[position] & 0x1f;
            if ((data[position] & 0x80) != 0 || type < 1 || type > 23) return null;
            int frameSize = Constants.H264_NAL_PREFIX.length + nalSize;
            checkSize(frameSize);
            byte[] frameData = new byte[frameSize];
            System.arraycopy(Constants.H264_NAL_PREFIX, 0, frameData, 0, Constants.H264_NAL_PREFIX.length);
            System.arraycopy(data, position, frameData, Constants.H264_NAL_PREFIX.length, nalSize);
            frames.add(new Frame(frameData, sample.getTimestamp(), sample.getTrack(), frameType(type)));
            position += nalSize;
        }
        return frames.isEmpty() ? null : frames.toArray(new Frame[0]);
    }

    private static byte frameType(int type) {
        if (H264Util.isNalUnitKeyFrame((byte) type)) return Frame.SYNC_FRAME;
        if (H264Util.isNAlUnitConfig((byte) type)) return Frame.CONFIG_FRAME;
        return Frame.NON_SYNC_FRAME;
    }

    private void checkSize(int size) {
        if (size > maxFrameSize) {
            clear();
            throw new BufferOverflowException();
        }
    }

    @Override
    public int getBufferSize() { return length; }

    @Override
    public void clear() {
        length = 0;
        fragmentHeader = 0;
        fragmentTrack = null;
        lastSequence = -1;
    }
}
