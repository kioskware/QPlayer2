package com.fivesoft.qplayer.common;

import androidx.annotation.NonNull;

import com.fivesoft.qplayer.base.track.SubtitleTrack;

public interface SubtitleReceiver {

    void onSubtitleReceived(@NonNull SubtitleTrack track, @NonNull String text, long duration);

}
