package com.fivesoft.qplayer.impl.decoder.video.h264;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Csd;
import com.fivesoft.qplayer.base.Frame;
import com.fivesoft.qplayer.base.MediaDecoderException;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.UnsupportedSampleException;
import com.fivesoft.qplayer.base.components.MediaDecoderOutput;
import com.fivesoft.qplayer.base.track.VideoTrack;
import com.fivesoft.qplayer.common.Constants;
import com.fivesoft.qplayer.common.Size;
import com.fivesoft.qplayer.common.Util;
import com.fivesoft.qplayer.common.resolver.Creator;
import com.fivesoft.qplayer.core.FrameBuilder;
import com.fivesoft.qplayer.core.decoders.VideoDecoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class H264Decoder extends VideoDecoder {

    private static final MediaDecoderOutput<Surface> EMPTY_OUTPUT = new MediaDecoderOutput<>(null, null);

    public static final List<Integer> SUPPORTED_SAMPLE_FORMATS =
            new ArrayList<>(Arrays.asList(FORMAT_RTP, FORMAT_RAW));

    public static final Creator<Descriptor, VideoDecoder> CREATOR =
            new Creator<Descriptor, VideoDecoder>() {

        @Override
        public int accept(Descriptor t) {
            Log.println(Log.ASSERT, "H264Decoder", "checking if accept: " + t.track.getFormat());

            if(t == null)
                return 0;

            if(!(t.track instanceof VideoTrack))
                return 0;

            String format = t.track.getFormat();
            if(format == null)
                return 0;

            format = format.toLowerCase()
                    .replace(" ", "");

            if(format.equals("h264") || format.equals("h.264") || format.equals("avc") ||
                    format.equals("h264/avc") || format.equals("h.264/avc") || format.equals("advancedvideocoding"))
                return 1;

            if(format.contains("/avc") || format.contains("/h264") || format.contains("/h.264"))
                return 1;

            if(!SUPPORTED_SAMPLE_FORMATS.contains(t.sampleFormat))
                return 0;

            return 1;
        }

        @Nullable
        @Override
        public H264Decoder create(Descriptor t) {
            if(accept(t) > 0)
                return new H264Decoder((VideoTrack) t.track, t.sampleFormat, t.maxEncodedFrameSize);

            return null;
        }
    };

    public static final int MIN_VIDEO_WIDTH = 256; // 144p
    public static final int MIN_VIDEO_HEIGHT = 144; // 144p

    public static final int MAX_VIDEO_WIDTH = 4096; // 4K
    public static final int MAX_VIDEO_HEIGHT = 2304; // 4K

    private static final long DEQUEUE_INPUT_TIMEOUT_US = TimeUnit.MILLISECONDS.toMicros(0);
    private static final long DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US = TimeUnit.MILLISECONDS.toMicros(0);

    public static final String MIME = "video/avc";

    private int videoWidth = Constants.UNKNOWN_VALUE, videoHeight = Constants.UNKNOWN_VALUE;
    private float videoFrameRate = Constants.UNKNOWN_VALUE;

    private volatile MediaCodec.BufferInfo bufferInfo;
    private volatile MediaCodec codec;
    private volatile MediaFormat format;
    private volatile Csd csd;

    private volatile boolean released = false;

    private volatile Surface outputSurface;
    private volatile Surface codecSurface;

    //Used to calculate presentationTimeUs
    private long frameIndex = 0;

    private volatile boolean wasValidSurface = false;
    private volatile boolean waitForKeyFrame = true;

    private volatile boolean firstFrame = true;

    private volatile MediaDecoderOutput<Surface> output = EMPTY_OUTPUT;

    private volatile boolean ppsReceived, spsReceived = false;

    private volatile boolean lowLatencyMode = true;

    private Bundle codecParams = new Bundle();

    @NonNull
    private final FrameBuilder frameBuilder;

    private final MediaCodec.OnFrameRenderedListener onFrameRenderedListener =
            (codec, presentationTimeUs, nanoTime) -> {
                track.notifyFrameRendered(presentationTimeUs);
            };

    /**
     * Creates a new media decoder for the specified video track.
     *
     * @param track The track that the decoder decodes. Cannot be null.
     * @throws UnsupportedSampleFormatException If the specified sample format is not supported.
     */

    public H264Decoder(@NonNull VideoTrack track, int sampleFormat, int maxEncodedFrameSize) throws UnsupportedSampleFormatException {
        super(track, sampleFormat, maxEncodedFrameSize);
        if (sampleFormat == FORMAT_RAW) {
            frameBuilder = FrameBuilder.RAW_FRAME_BUILDER;
        } else if (sampleFormat == FORMAT_RTP) {
            frameBuilder = new RtpH264FrameBuilder(maxEncodedFrameSize);
        } else {
            throw new UnsupportedSampleFormatException("Unsupported sample format: " + sampleFormat);
        }
    }

    @Override
    public int getVideoWidth() {
        return videoWidth;
    }

    @Override
    public int getVideoHeight() {
        return videoHeight;
    }

    @Override
    public float getVideoFrameRate() {
        return videoFrameRate;
    }

    /**
     * Creates a new media decoder for the specified video track.
     *
     * @param track        The track that the decoder decodes. Cannot be null.
     * @param sampleFormat The sample format.
     * @throws UnsupportedSampleFormatException If the specified sample format is not supported.
     */

    public H264Decoder(@NonNull VideoTrack track, int sampleFormat) throws UnsupportedSampleFormatException {
        this(track, sampleFormat, FrameBuilder.DEFAULT_MAX_FRAME_SIZE);
        setCsd(track.getCsd());
    }

    @Override
    public void setCsd(@Nullable Csd csd) throws IllegalStateException {
        this.csd = csd;
    }

    @Override
    public void setLowLatencyMode(boolean lowLatencyMode) throws IllegalStateException {
        if (
                this.lowLatencyMode != lowLatencyMode
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            this.lowLatencyMode = lowLatencyMode;
            MediaCodec codec = this.codec;
            if(codec != null) {
                codecParams.putInt(MediaFormat.KEY_LOW_LATENCY, lowLatencyMode ? 1 : 0);
                codec.setParameters(codecParams);
            }
        }
    }

    @Override
    public void setOutput(@Nullable MediaDecoderOutput<Surface> output) throws IllegalStateException, IllegalArgumentException {
        checkReleased();
        this.output = output == null ? EMPTY_OUTPUT : output;
        this.outputSurface = this.output.getRenderer();
    }

    @Nullable
    @Override
    public synchronized Frame[] feed(@NonNull Sample sample) throws IllegalStateException, UnsupportedSampleException, NullPointerException {
        checkReleased();
        return frameBuilder.pull(sample);
    }

    @Override
    public synchronized int decode(@NonNull Frame frame)
            throws IllegalStateException, UnsupportedSampleException, MediaDecoderException, NullPointerException {
        checkReleased();

        try {
            //Ensure that output surface matches set output surface
            updateCodecSurface();
            //Ensure that codec is configured
            MediaCodec codec = ensureMediaCodec();

            if (frame.frameType == Frame.CONFIG_FRAME) {
                byte nalUnitType = H264Util.getNalUnitType(frame.getArray(), frame.getOffset(), frame.getLength());
                if (nalUnitType == H264Util.NAL_UNIT_TYPE_SPS) {
                    spsReceived = true;
                    obtainVideoParamsFromSPS(frame.getArray(), frame.getOffset(), frame.getLength());
                } else if (nalUnitType == H264Util.NAL_UNIT_TYPE_PPS) {
                    ppsReceived = true;
                }
            } else if (!spsReceived || !ppsReceived) {
                //Codec not configured yet
                waitForKeyFrame = true;
                return ACTION_WAITING_FOR_CONFIG;
            } else if(waitForKeyFrame){
                if(frame.frameType == Frame.SYNC_FRAME){
                    Log.println(Log.ASSERT, "sdfsdf", "Received key frame, flushing codec");
                    flush();
                    waitForKeyFrame = false;
                } else {
                    return ACTION_WAITING_FOR_KEY_FRAME;
                }
            }

            int inIndex, outIndex;

            inIndex = codec.dequeueInputBuffer(DEQUEUE_INPUT_TIMEOUT_US);
            if (inIndex >= 0) {
                ByteBuffer bb = codec.getInputBuffer(inIndex);
                if (bb != null) {
                    bb.rewind();

                    //Write nal prefix if needed
                    if (H264Util.startsWithNalPrefix(frame.getArray(), frame.getOffset(), frame.getLength()) == 0) {
                        bb.put(Constants.H264_NAL_PREFIX);
                    }

                    //Write all buffered samples
                    bb.put(frame.getArray(), frame.getOffset(), frame.getLength());

                    codec.queueInputBuffer(inIndex, 0, frame.getLength(), frame.getTimestamp(), 0);
                } else {
                    //TODO Is it ok?
                    return ACTION_DROP_FRAME;
                }
            }

            outIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US);
            boolean rendered = false;

            switch (outIndex) {
                default:
                    rendered = renderBufferOnSurface(outIndex);
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER: {
                    return ACTION_DROP_FRAME;
                }
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    break;
            }

            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                if(rendered){
                    return ACTION_RENDER_FRAME;
                } else if(frame.frameType == Frame.CONFIG_FRAME){
                    return ACTION_CONFIGURED;
                } else {
                    return ACTION_INVALID_OUTPUT;
                }
            } else {
                inIndex = codec.dequeueInputBuffer(DEQUEUE_INPUT_TIMEOUT_US);

                if (inIndex >= 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }

                return ACTION_END_OF_STREAM_REACHED;
            }

        } catch (Exception e) {
            if (!isReleased()) {
                destroyMediaCodec();
                throw new MediaDecoderException(e);
            }
        } finally {
            if (isReleased()) {
                destroyMediaCodec();
            }
        }
        return ACTION_CONFIGURED;
    }

    private boolean renderBufferOnSurface(int outIndex){
        if (outIndex >= 0) {
            try {
                boolean doRender = bufferInfo.size != 0
                        && !isReleased()
                        && outputSurface != null && outputSurface.isValid();

                codec.releaseOutputBuffer(outIndex, doRender);
                if(doRender){
                    firstFrame = false;
                }
                return doRender;
            } catch (IllegalStateException e) {
                //when surface becomes invalid after check, this may happen
                //release without rendering
                codec.releaseOutputBuffer(outIndex, false);
            }
        }
        return false;
    }

    private synchronized void updateCodecSurface() throws IOException {
        MediaCodec codec = this.codec;
        Surface output = this.output.getRenderer();

        if(output != null && !output.isValid()){
            output = null;
            this.outputSurface = null;
        }

        if (codec != null && codecSurface != output) {
            try {
                codec.setOutputSurface(output);
                Log.println(Log.ASSERT, "errr", "Set output surface " + output);
                codecSurface = output;
                firstFrame = true;
            } catch (IllegalStateException | IllegalArgumentException e) {
                Log.println(Log.ASSERT, "errr", "Failed to set output surface " + e);
                //Reconfigure codec and try again
                destroyMediaCodec();
                ensureMediaCodec();
            }
        }
    }

    @Override
    public void flush() {
        MediaCodec codec = this.codec;
        if (codec != null) {
            flushCodecQuietly();
            configCsd(format, codec, csd);
        }
    }

    @Override
    public void release() {
        MediaCodec codec = this.codec;
        if (codec != null) {
            destroyMediaCodec();
            this.codec = null;
        }
        released = true;
    }

    @Override
    public boolean isReleased() {
        return released;
    }

    @NonNull
    private MediaCodec initCodec() throws IOException {
        MediaCodec codec = this.codec;
        Csd csd = this.csd;
        outputSurface = output.getRenderer();

        if (codec == null) {
            Bundle params = new Bundle();
            codec = MediaCodec.createDecoderByType(MIME);

            Size safeSize = getDecoderSafeWidthHeight(codec);
            format = MediaFormat.createVideoFormat(MIME, safeSize.width, safeSize.height);
            //format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            bufferInfo = new MediaCodec.BufferInfo();
            frameIndex = 0;

            codec.setOnFrameRenderedListener(onFrameRenderedListener, null);

            //Set low latency mode if available
            if (Build.VERSION.SDK_INT >= 30) {
                params.putInt(MediaFormat.KEY_LOW_LATENCY, lowLatencyMode ? 1 : 0);
            }

            configCsd(format, codec, csd);

            Surface output = this.output.getRenderer();
            if(output != null && !output.isValid()){
                output = null;
                this.outputSurface = null;
            }

            codec.configure(format, output, null, 0);
            codecSurface = outputSurface;

            codecParams = params;
            codec.setParameters(params);
            codec.start();

            firstFrame = true;
            this.codec = codec;
        }
        return codec;
    }

    private void configCsd(MediaFormat format, MediaCodec codec, Csd csd) {

        if(format == null || codec == null){
            return;
        }

        if (csd != null) {
            for (int i = 0; i < csd.getCapacity(); i++) {
                if (csd.hasCsd(i)) {
                    format.setByteBuffer("csd-" + i, ByteBuffer.wrap(H264Util.ensureStartsWithNalPrefix(csd.getCsd(i))));
                    if (i == 0) {
                        ppsReceived = true;
                    } else if(i == 1) {
                        byte[] csd0 = csd.getCsd(i);
                        obtainVideoParamsFromSPS(csd0, 0, csd0.length);
                        spsReceived = true;
                    }
                }
            }
        }
    }

    @NonNull
    private MediaCodec ensureMediaCodec() throws IOException {
        return initCodec();
    }

    private void destroyMediaCodec() {
        MediaCodec codec = this.codec;
        if (codec != null) {
            codec.release();
        }
        this.format = null;
        this.codec = null;
        this.waitForKeyFrame = true;
        this.ppsReceived = false;
        this.spsReceived = false;
        this.codecSurface = null;
    }

    private void stopCodecQuietly() {
        try {
            codec.stop();
        } catch (Exception ignored) {
        }
    }

    private void flushCodecQuietly() {
        try {
            waitForKeyFrame = true;
            codec.flush();
        } catch (Exception ignored) {
        }
    }

    private Size getDecoderSafeWidthHeight(MediaCodec decoder) {

        MediaCodecInfo.VideoCapabilities c =
                decoder.getCodecInfo()
                        .getCapabilitiesForType(MIME)
                        .getVideoCapabilities();

        int width = Util.limit(videoWidth, MIN_VIDEO_WIDTH, MAX_VIDEO_WIDTH);
        int height = Util.limit(videoHeight, MIN_VIDEO_HEIGHT, MAX_VIDEO_HEIGHT);

        Size size;
        if (c.isSizeSupported(width, height)) {
            size = new Size(width, height);
        } else {
            int wa = c.getWidthAlignment();
            int ha = c.getHeightAlignment();
            size = new Size(
                    ceilDivide(width, wa) * wa,
                    ceilDivide(height, ha) * ha
            );
        }

        return size;
    }

    /**
     * Divides a {@code numerator} by a {@code denominator}, returning the ceiled result.
     *
     * @param numerator   The numerator to divide.
     * @param denominator The denominator to divide by.
     * @return The ceiled result of the division.
     */
    public static int ceilDivide(int numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }

    private void obtainVideoParamsFromSPS(byte[] sps, int off, int len){
        if (sps == null || len < 4) {
            return;
        }

        try {
            //Wrong width and height is returned in High profile
            SPSParser.VideoParams videoParams =
                    SPSParser.parseSPSStatic(sps, H264Util.startsWithNalPrefix(sps, off, len));

            if (videoParams != null) {
                if (videoParams.width > 0 && videoParams.height > 0
                    && videoParams.width != videoWidth && videoParams.height != videoHeight) {

                    if(videoParams.width > MAX_VIDEO_WIDTH || videoParams.height > MAX_VIDEO_HEIGHT
                            || videoParams.width < MIN_VIDEO_WIDTH || videoParams.height < MIN_VIDEO_HEIGHT){
                        //Invalid width or height
                        videoWidth = Constants.UNKNOWN_VALUE;
                        videoHeight = Constants.UNKNOWN_VALUE;
                    } else {
                        videoWidth = videoParams.width;
                        videoHeight = videoParams.height;

                        MediaFormat format = this.format;
                        if(format != null){
                            format.setInteger(MediaFormat.KEY_WIDTH, videoWidth);
                            format.setInteger(MediaFormat.KEY_HEIGHT, videoHeight);
                        }
                    }

                    track.setWidth(videoWidth);
                    track.setHeight(videoHeight);
                }

                if(videoParams.frameRate > 0 && videoParams.frameRate != videoFrameRate && videoParams.frameRate <= 130){
                    videoFrameRate = videoParams.frameRate;
                    track.setFps(videoFrameRate);
                }

            }

        } catch (Exception e) {
            //ignore
            //Log.println(Log.ASSERT, "H264Decoder", "obtainVideoParamsFromPPS: " + e);
        }

    }

}
