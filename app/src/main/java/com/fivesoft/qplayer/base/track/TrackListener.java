package com.fivesoft.qplayer.base.track;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.common.Constants;

/**
 * An interface for handling track changes and events.
 */

public interface TrackListener {

    /**
     * Called when a field of track is changed.<br>
     * @param track The track.
     * @param field The field that is changed.
     * @param value The new value of the field.
     */

    default void onTrackFieldChanged(@NonNull Track track, @NonNull String field, @Nullable Object value) {}

    /**
     * Called when track sample is received.<br>
     * @param track The track attached to the sample.
     * @param sample The sample that is received.
     */

    default void onSampleReceived(@NonNull Track track, @NonNull Sample sample) {}

    /**
     * Called when track frame rendered.<br>
     * @param track The track attached to the frame.
     * @param timestamp The timestamp of the frame or {@link Constants#UNKNOWN_VALUE}
     *                  if decoder does not provide such information.
     */

    default void onFrameRendered(@NonNull Track track, long timestamp) {}

    /**
     * Called when track is added to the player.<br>
     * @param track The track that is added.
     */

    default void onTrackAdded(@NonNull Track track) {}

    /**
     * Called when track is removed from the player.<br>
     * @param track The track that is removed.
     */

    default void onTrackRemoved(@NonNull Track track) {}

}
