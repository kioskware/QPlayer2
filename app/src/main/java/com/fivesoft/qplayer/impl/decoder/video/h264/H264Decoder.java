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
import com.fivesoft.qplayer.base.QPlayer;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.UnsupportedSampleException;
import com.fivesoft.qplayer.base.components.MediaDecoderOutput;
import com.fivesoft.qplayer.base.track.VideoTrack;
import com.fivesoft.qplayer.common.Constants;
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

    private static final int DEFAULT_VIDEO_WIDTH = 640;
    private static final int DEFAULT_VIDEO_HEIGHT = 360;
    private static final int MAX_REASONABLE_VIDEO_DIMENSION = 32768;
    private static final float MAX_REASONABLE_FRAME_RATE = 1000f;
    private static final String KEY_SAR_WIDTH = "sar-width";
    private static final String KEY_SAR_HEIGHT = "sar-height";

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

    private volatile boolean lowLatencyMode = QPlayer.DEFAULT_LOW_LATENCY_MODE;

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
        if (track.getWidth() > 0 && track.getHeight() > 0) {
            videoWidth = track.getWidth();
            videoHeight = track.getHeight();
        }
        if (sampleFormat == FORMAT_RAW) {
            frameBuilder = FrameBuilder.RAW_FRAME_BUILDER;
        } else if (sampleFormat == FORMAT_RTP) {
            frameBuilder = new RtpH264FrameBuilder(maxEncodedFrameSize);
        } else {
            throw new UnsupportedSampleFormatException("Unsupported sample format: " + sampleFormat);
        }
        setCsd(track.getCsd());
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
    }

    @Override
    public void setCsd(@Nullable Csd csd) throws IllegalStateException {
        this.csd = csd;
        updateVideoParamsFromCsd(csd);
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
            if (frame.frameType == Frame.CONFIG_FRAME) {
                byte nalUnitType = H264Util.getNalUnitType(frame.getArray(), frame.getOffset(), frame.getLength());
                if (nalUnitType == H264Util.NAL_UNIT_TYPE_SPS) {
                    spsReceived = true;
                    obtainVideoParamsFromSPS(frame.getArray(), frame.getOffset(), frame.getLength());
                } else if (nalUnitType == H264Util.NAL_UNIT_TYPE_PPS) {
                    ppsReceived = true;
                }
            }

            //Ensure that output surface matches set output surface
            updateCodecSurface();
            //Ensure that codec is configured
            MediaCodec codec = ensureMediaCodec();

            if (frame.frameType != Frame.CONFIG_FRAME) {
                if (!spsReceived || !ppsReceived) {
                    //Codec not configured yet
                    waitForKeyFrame = true;
                    return ACTION_WAITING_FOR_CONFIG;
                }
                if (waitForKeyFrame) {
                    if (frame.frameType == Frame.SYNC_FRAME) {
                        Log.println(Log.ASSERT, "sdfsdf", "Received key frame, flushing codec");
                        flush();
                        waitForKeyFrame = false;
                    } else {
                        return ACTION_WAITING_FOR_KEY_FRAME;
                    }
                }
            }

            int inIndex, outIndex;

            inIndex = codec.dequeueInputBuffer(DEQUEUE_INPUT_TIMEOUT_US);
            if (inIndex >= 0) {
                ByteBuffer bb = codec.getInputBuffer(inIndex);
                if (bb != null) {
                    bb.rewind();

                    //Write nal prefix if needed
                    boolean prependNalPrefix = H264Util.startsWithNalPrefix(
                            frame.getArray(), frame.getOffset(), frame.getLength()
                    ) == 0;
                    if (prependNalPrefix) {
                        bb.put(Constants.H264_NAL_PREFIX);
                    }

                    //Write all buffered samples
                    bb.put(frame.getArray(), frame.getOffset(), frame.getLength());

                    int inputSize = frame.getLength() +
                            (prependNalPrefix ? Constants.H264_NAL_PREFIX.length : 0);
                    codec.queueInputBuffer(inIndex, 0, inputSize, frame.getTimestamp(), 0);
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
                    updateVideoParamsFromOutputFormat(codec.getOutputFormat());
                    return ACTION_CONFIGURED;
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    return ACTION_CONFIGURED;
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

            int[] safeSize = getDecoderSafeWidthHeight(codec);
            format = MediaFormat.createVideoFormat(
                    MIME,
                    safeSize[0],
                    safeSize[1]
            );
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
                    byte[] data = csd.getCsd(i);
                    format.setByteBuffer("csd-" + i, ByteBuffer.wrap(H264Util.ensureStartsWithNalPrefix(data)));
                    try {
                        byte nalUnitType = H264Util.getNalUnitType(data, 0, data.length);
                        if (nalUnitType == H264Util.NAL_UNIT_TYPE_SPS) {
                            obtainVideoParamsFromSPS(data, 0, data.length);
                            spsReceived = true;
                        } else if (nalUnitType == H264Util.NAL_UNIT_TYPE_PPS) {
                            ppsReceived = true;
                        }
                    } catch (IllegalArgumentException ignored) {
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

    private int[] getDecoderSafeWidthHeight(MediaCodec decoder) {

        MediaCodecInfo.VideoCapabilities c =
                decoder.getCodecInfo()
                        .getCapabilitiesForType(MIME)
                        .getVideoCapabilities();

        int width = videoWidth > 0 ? videoWidth : track.getWidth();
        int height = videoHeight > 0 ? videoHeight : track.getHeight();
        if (width <= 0 || height <= 0) {
            width = DEFAULT_VIDEO_WIDTH;
            height = DEFAULT_VIDEO_HEIGHT;
        }

        int widthAlignment = c.getWidthAlignment();
        int heightAlignment = c.getHeightAlignment();
        width = ceilDivide(width, widthAlignment) * widthAlignment;
        height = ceilDivide(height, heightAlignment) * heightAlignment;

        int[] size;
        if (c.isSizeSupported(width, height)) {
            size = new int[]{width, height};
        } else {
            width = alignToRange(
                    width,
                    c.getSupportedWidths().getLower(),
                    c.getSupportedWidths().getUpper(),
                    widthAlignment
            );
            height = alignToRange(
                    height,
                    c.getSupportedHeights().getLower(),
                    c.getSupportedHeights().getUpper(),
                    heightAlignment
            );
            size = new int[]{width, height};
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

    private static int alignToRange(int value, int min, int max, int alignment) {
        int clamped = Math.max(min, Math.min(max, value));
        int aligned = ceilDivide(clamped, alignment) * alignment;
        if (aligned > max) {
            aligned = (max / alignment) * alignment;
        }
        return Math.max(min, aligned);
    }

    private void obtainVideoParamsFromSPS(byte[] sps, int off, int len){
        if (sps == null || len < 4) {
            return;
        }

        try {
            SPSParser.VideoParams videoParams =
                    SPSParser.parseSPSStatic(sps, off, len);

            if (videoParams != null) {
                updateVideoDimensions(videoParams.width, videoParams.height);
                track.setPixelAspectRatio(videoParams.getPixelAspectRatio());

                if(videoParams.frameRate > 0 && videoParams.frameRate != videoFrameRate &&
                        videoParams.frameRate <= MAX_REASONABLE_FRAME_RATE){
                    videoFrameRate = videoParams.frameRate;
                    track.setFps(videoFrameRate);
                }

            }

        } catch (Exception e) {
            //ignore
            //Log.println(Log.ASSERT, "H264Decoder", "obtainVideoParamsFromPPS: " + e);
        }

    }

    private void updateVideoParamsFromCsd(@Nullable Csd csd) {
        if (csd == null) {
            return;
        }
        for (int i = 0; i < csd.getCapacity(); i++) {
            if (!csd.hasCsd(i)) {
                continue;
            }
            byte[] data = csd.getCsd(i);
            try {
                byte nalUnitType = H264Util.getNalUnitType(data, 0, data.length);
                if (nalUnitType == H264Util.NAL_UNIT_TYPE_SPS) {
                    spsReceived = true;
                    obtainVideoParamsFromSPS(data, 0, data.length);
                } else if (nalUnitType == H264Util.NAL_UNIT_TYPE_PPS) {
                    ppsReceived = true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void updateVideoParamsFromOutputFormat(@NonNull MediaFormat outputFormat) {
        try {
            int width = outputFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = outputFormat.getInteger(MediaFormat.KEY_HEIGHT);

            if (outputFormat.containsKey(MediaFormat.KEY_CROP_LEFT) &&
                    outputFormat.containsKey(MediaFormat.KEY_CROP_RIGHT)) {
                width = outputFormat.getInteger(MediaFormat.KEY_CROP_RIGHT) + 1
                        - outputFormat.getInteger(MediaFormat.KEY_CROP_LEFT);
            }
            if (outputFormat.containsKey(MediaFormat.KEY_CROP_TOP) &&
                    outputFormat.containsKey(MediaFormat.KEY_CROP_BOTTOM)) {
                height = outputFormat.getInteger(MediaFormat.KEY_CROP_BOTTOM) + 1
                        - outputFormat.getInteger(MediaFormat.KEY_CROP_TOP);
            }
            updateVideoDimensions(width, height);

            if (outputFormat.containsKey(KEY_SAR_WIDTH) && outputFormat.containsKey(KEY_SAR_HEIGHT)) {
                int sarWidth = outputFormat.getInteger(KEY_SAR_WIDTH);
                int sarHeight = outputFormat.getInteger(KEY_SAR_HEIGHT);
                if (sarWidth > 0 && sarHeight > 0) {
                    track.setPixelAspectRatio((float) sarWidth / sarHeight);
                }
            }
        } catch (RuntimeException ignored) {
            // Some vendor codecs expose an incomplete output format. SPS/SDP remains the fallback.
        }
    }

    private void updateVideoDimensions(int width, int height) {
        if (width <= 0 || height <= 0 ||
                width > MAX_REASONABLE_VIDEO_DIMENSION || height > MAX_REASONABLE_VIDEO_DIMENSION) {
            return;
        }
        if (width == videoWidth && height == videoHeight) {
            return;
        }

        videoWidth = width;
        videoHeight = height;
        MediaFormat currentFormat = format;
        if (currentFormat != null) {
            currentFormat.setInteger(MediaFormat.KEY_WIDTH, width);
            currentFormat.setInteger(MediaFormat.KEY_HEIGHT, height);
        }
        track.setWidth(width);
        track.setHeight(height);
    }

}
