package com.fivesoft.qplayer.core.player;

import android.media.AudioTrack;
import android.view.Surface;

import com.fivesoft.qplayer.base.QPlayer;
import com.fivesoft.qplayer.common.SubtitleReceiver;

public class QPlayerFactory {

    public static QPlayer<Surface, AudioTrack, SubtitleReceiver> createDefault() {
        return new QPlayerImpl();
    }

}
