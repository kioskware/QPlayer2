package com.fivesoft.qplayer.impl.decoder.audio.aac;

import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

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
import com.fivesoft.qplayer.common.X;
import com.fivesoft.qplayer.common.resolver.Creator;
import com.fivesoft.qplayer.core.FrameBuilder;
import com.fivesoft.qplayer.core.decoders.AudioDecoder;
import com.fivesoft.qplayer.impl.decoder.video.h264.H264Util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AACDecoder extends AudioDecoder {

    public static final String MIME = "audio/mp4a-latm";

    public static final List<Integer> SUPPORTED_SAMPLE_FORMATS =
            new ArrayList<>(Arrays.asList(FORMAT_RTP, FORMAT_RAW));

    public static final Creator<Descriptor, AudioDecoder> CREATOR =
            new Creator<Descriptor, AudioDecoder>() {

                @Override
                public int accept(Descriptor t) {
                    //Log.println(Log.ASSERT, "AACDecoder", "checking if accept: " + t.track.getFormat());

                    if (t == null)
                        return 0;

                    if (!(t.track instanceof com.fivesoft.qplayer.base.track.AudioTrack))
                        return 0;

                    String format = t.track.getFormat();
                    if (format == null)
                        return 0;

                    format = format.toLowerCase()
                            .replace(" ", "");

                    if (format.equals("aac") || format.equals("audio/aac") || format.equals("audio/mp4a-latm") || format.equals("mpeg4-generic")
                            || format.equals("mp4a-latm") || format.equals("audio/mp4alatm") || format.equals("mp4alatm")
                            || format.equals("advancedaudiocoding") || format.equals("mpeg4aac") || format.equals("aac-lc")
                            || format.equals("aac-hbr") || format.equals("aac-low-complexity") || format.equals("aac-high-efficiency")
                            || format.equals("aac-low-delay") || format.equals("aac-eld") || format.equals("mpeg2-audio")
                            || format.equals("mpeg2aac") || format.equals("mpeg-2aac") || format.equals("mpeg-2-audio") || format.equals("mpeg-2-aac"))
                        return 1;

                    if (format.contains("/aac") || format.contains("/mp4a-latm") || format.contains("/mpeg4aac"))
                        return 1;

                    if (!SUPPORTED_SAMPLE_FORMATS.contains(t.sampleFormat))
                        return 0;

                    return 1;
                }

                @Nullable
                @Override
                public AACDecoder create(Descriptor t) {
                    if (accept(t) > 0)
                        return new AACDecoder((com.fivesoft.qplayer.base.track.AudioTrack) t.track,
                                t.sampleFormat, t.maxEncodedFrameSize);

                    return null;
                }
            };

    private volatile MediaDecoderOutput<AudioTrack> output;
    private AudioTrack trackOutput;
    private volatile MediaCodec codec;
    private volatile MediaFormat format;
    private volatile Csd csd;
    private volatile boolean lowLatencyMode = QPlayer.DEFAULT_LOW_LATENCY_MODE;
    private volatile boolean released = false;
    private final FrameBuilder frameBuilder;
    private volatile MediaCodec.BufferInfo bufferInfo;

    private Bundle codecParams = new Bundle();

    private final MediaCodec.OnFrameRenderedListener onFrameRenderedListener =
            (codec, presentationTimeUs, nanoTime) -> {
                track.notifyFrameRendered(Constants.UNKNOWN_VALUE);
            };
    private volatile int frameIndex;
    private volatile boolean configReceived = false;

    public AACDecoder(@NonNull com.fivesoft.qplayer.base.track.AudioTrack track,
                      int sampleFormat, int maxEncodedFrameSize) throws UnsupportedSampleFormatException {
        super(track, sampleFormat, maxEncodedFrameSize);
        if (sampleFormat == FORMAT_RAW) {
            frameBuilder = FrameBuilder.RAW_FRAME_BUILDER;
        } else if (sampleFormat == FORMAT_RTP) {
            String mode = track.getMode();
            if (mode == null) {
                mode = "AAC-lbr";
            }
            frameBuilder = new RtpAACFrameBuilder(maxEncodedFrameSize, mode);
        } else {
            throw new UnsupportedSampleFormatException("Unsupported sample format: " + sampleFormat);
        }
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
    public void setOutput(@Nullable MediaDecoderOutput<AudioTrack> output) throws IllegalStateException, IllegalArgumentException {
        checkReleased();
        this.output = output;
    }

    @Nullable
    @Override
    public Frame[] feed(@NonNull Sample sample) throws IllegalStateException, UnsupportedSampleException, NullPointerException {
        checkReleased();
        return frameBuilder.pull(sample);
    }

    @Override
    public int decode(@NonNull Frame sample) throws IllegalStateException, UnsupportedSampleException, MediaDecoderException, NullPointerException {
        checkReleased();

        try {
            AudioTrack track = ensureAudioTrack();

            if (track == null)
                return ACTION_INVALID_OUTPUT;

            MediaCodec codec = ensureMediaCodec();
            int inIndex, outIndex;
            inIndex = codec.dequeueInputBuffer(0);
            if (inIndex >= 0) {
                ByteBuffer byteBuffer;
                try {
                    byteBuffer = codec.getInputBuffer(inIndex);
                } catch (Exception e) {
                    return ACTION_DROP_FRAME;
                }

                if (byteBuffer != null) {
                    byteBuffer.rewind();
                }

                if (byteBuffer != null) {
                    byteBuffer.put(sample.getArray(), sample.getOffset(), sample.getLength());
                }
                codec.queueInputBuffer(inIndex, sample.getOffset(), sample.getLength(), 0, 0);

            }

            outIndex = codec.dequeueOutputBuffer(bufferInfo, 0);
            switch (outIndex) {
                default:
                    if (outIndex >= 0) {
                        ByteBuffer byteBuffer = codec.getOutputBuffer(outIndex);
                        byte[] chunk = new byte[bufferInfo.size];
                        if (byteBuffer != null) {
                            byteBuffer.get(chunk);
                        }

                        if (byteBuffer != null) {
                            byteBuffer.clear();
                        }

                        if (chunk.length != 0) {
                            track.write(chunk, 0, chunk.length);
                        }

                        codec.releaseOutputBuffer(outIndex, false);
                        return ACTION_RENDER_FRAME;
                    }
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    break;
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
        return ACTION_DROP_FRAME;
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
        released = true;
        MediaCodec codec = this.codec;
        if (codec != null) {
            codec.release();
        }
    }

    @Override
    public boolean isReleased() {
        return released;
    }

    @NonNull
    private MediaCodec ensureMediaCodec() throws IOException {
        MediaCodec codec = this.codec;
        Csd csd = track.getCsd();
        trackOutput = output.getRenderer();
        if(trackOutput != null){
            trackOutput.play();
        }

        if (codec == null) {
            Bundle params = new Bundle();
            codec = MediaCodec.createDecoderByType(MIME);
            int sampleRate = track.getClockRate();
            int channelCount = track.getChannels();
            format = MediaFormat.createAudioFormat(MIME, sampleRate, channelCount);

            byte[] codecConfig = csd.hasCsd(0) ? csd.getCsd(0) : null;
            if (codecConfig == null) {
                codecConfig = getAacDecoderConfigData(2, sampleRate, channelCount);
            }

            format.setByteBuffer("csd-0", ByteBuffer.wrap(codecConfig));
            format.setInteger("aac-profile", 2);

            bufferInfo = new MediaCodec.BufferInfo();
            frameIndex = 0;

            codec.setOnFrameRenderedListener(onFrameRenderedListener, null);

            //Set low latency mode if available
            if (Build.VERSION.SDK_INT >= 30) {
                params.putInt(MediaFormat.KEY_LOW_LATENCY, lowLatencyMode ? 1 : 0);
            }

            configCsd(format, codec, csd);
            codec.configure(format, null, null, 0);

            codecParams = params;
            codec.setParameters(params);
            codec.start();

            this.codec = codec;
        }
        return codec;
    }

    @Nullable
    private AudioTrack ensureAudioTrack() {
        if (trackOutput == null) {
            trackOutput = output.getRenderer();
            if(trackOutput != null){
                trackOutput.play();
            }
        }

        return trackOutput;
    }

    private void configCsd(MediaFormat format, MediaCodec codec, Csd csd) {
        if (format == null || codec == null) {
            return;
        }
        if (csd != null) {
            for (int i = 0; i < csd.getCapacity(); i++) {
                if (csd.hasCsd(i)) {
                    format.setByteBuffer("csd-" + i, ByteBuffer.wrap(csd.getCsd(i)));
                    if (i == 0) {
                        configReceived = true;
                    }
                }
            }
        }
    }

    private void destroyMediaCodec() {
        MediaCodec codec = this.codec;
        if (codec != null) {
            codec.release();
        }
        this.codec = null;
        format = null;
        configReceived = false;
    }

    private void flushCodecQuietly() {
        try {
            codec.flush();
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private static byte[] getAacDecoderConfigData(int audioProfile, int sampleRate, int channels) {
        int extraDataAac = audioProfile << 11;
        switch (sampleRate) {
            case 7350:
                extraDataAac |= 1536;
                break;
            case 8000:
                extraDataAac |= 1408;
                break;
            case 11025:
                extraDataAac |= 1280;
                break;
            case 12000:
                extraDataAac |= 1152;
                break;
            case 16000:
                extraDataAac |= 1024;
                break;
            case 22050:
                extraDataAac |= 896;
                break;
            case 24000:
                extraDataAac |= 768;
                break;
            case 32000:
                extraDataAac |= 640;
                break;
            case 44100:
                extraDataAac |= 512;
                break;
            case 48000:
                extraDataAac |= 384;
                break;
            case 64000:
                extraDataAac |= 256;
                break;
            case 88200:
                extraDataAac |= 128;
                break;
            case 96000:
                extraDataAac |= 0;
        }

        extraDataAac |= channels << 3;

        return new byte[]{(byte) ((extraDataAac & '\uff00') >> 8), (byte) (extraDataAac & 255)};
    }

}
