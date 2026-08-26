package com.fivesoft.qplayer.base.track;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.common.Util;

import java.util.Objects;

public class AudioTrack extends Track {

    public static final String FIELD_CHANNELS = "channels";
    public static final String FIELD_MODE = "mode";

    public static final String FIELD_VOLUME = "volume";

    private int channels = Track.UNKNOWN;
    private String mode;

    private float volume = 0.5f;

    public AudioTrack(@NonNull String id, int payloadType) {
        super(id, payloadType);
    }

    /**
     * Creates an audio track with the specified parameters.<br>
     * @param id The id of the audio track.
     * @param payloadType The payload type of the audio track.
     * @param channels The number of channels of the audio track. Negative values will be treated as unknown.
     * @param mode The mode of the audio track.
     */

    public AudioTrack(@NonNull String id, int payloadType, int channels, String mode) {
        super(id, payloadType);
        setChannels(channels);
        setMode(mode);
    }

    /**
     * Returns the number of channels of the audio track or {@link Track#UNKNOWN} if the number of channels is unknown.<br>
     * @return The number of channels of the audio track or {@link Track#UNKNOWN} if the number of channels is unknown.
     */

    public int getChannels() {
        return channels;
    }

    /**
     * Returns the mode of the audio track or null if the mode is unknown.<br>
     * @return The mode of the audio track or null if the mode is unknown.
     */

    @Nullable
    public String getMode() {
        return mode;
    }

    /**
     * Returns the volume of the audio track.<br>
     * @return The volume of the audio track.
     */

    public float getVolume() {
        return volume;
    }

    /**
     * Sets the number of channels of the audio track.<br>
     * Marks channels as unknown if the number of channels is less than 0.<br>
     * @param channels The number of channels of the audio track.
     */

    public void setChannels(int channels) {
        int val = Math.max(Track.UNKNOWN, channels);
        if(val != this.channels) {
            this.channels = val;
            notifyUpdated(FIELD_CHANNELS, val);
        }
    }

    /**
     * Sets the mode of the audio track.<br>
     * @param mode The mode of the audio track.
     */

    public void setMode(@Nullable String mode) {
        if(!Objects.equals(mode, this.mode)) {
            this.mode = mode;
            notifyUpdated(FIELD_MODE, mode);
        }
    }

    /**
     * Sets the volume of the audio track.<br>
     * @param volume The volume of the audio track.
     */

    public void setVolume(float volume) {
        float val = Util.limit(volume, 0, 1);
        if (val != this.volume) {
            this.volume = val;
            notifyUpdated(FIELD_VOLUME, val);
        }
    }

}
