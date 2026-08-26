package com.fivesoft.qplayer.core;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.components.MediaDecoderOutput;
import com.fivesoft.qplayer.base.track.Track;

public class DefaultAudioOutputCreator extends MediaDecoderOutput.Creator<AudioTrack> {

    public static final DefaultAudioOutputCreator INSTANCE = new DefaultAudioOutputCreator();

    @Nullable
    @Override
    public MediaDecoderOutput<AudioTrack> create(Track t) {
        if(!(t instanceof com.fivesoft.qplayer.base.track.AudioTrack))
            return null;

        return new MediaDecoderOutput<>(
                createAudioTrack((com.fivesoft.qplayer.base.track.AudioTrack) t)
        );
    }

    public static AudioTrack createAudioTrack(@NonNull com.fivesoft.qplayer.base.track.AudioTrack track) {
        int outChannel = track.getChannels() > 1 ? 12 : 4;
        int outAudio = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = AudioTrack.getMinBufferSize(track.getClockRate(), outChannel, outAudio);

        return new AudioTrack(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
                new AudioFormat.Builder()
                        .setEncoding(outAudio)
                        .setChannelMask(outChannel)
                        .setSampleRate(track.getClockRate())
                        .build(),
                bufferSize, AudioTrack.MODE_STREAM, 0);
    }

}
