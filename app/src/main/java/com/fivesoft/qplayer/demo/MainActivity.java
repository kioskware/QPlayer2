package com.fivesoft.qplayer.demo;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.fivesoft.qplayer.R;
import com.fivesoft.qplayer.core.KeepScreenOnMode;
import com.fivesoft.qplayer.core.QPlayerView;

import org.jetbrains.annotations.Nullable;

public final class MainActivity extends AppCompatActivity {

    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);
        QPlayerView playerView = findViewById(R.id.playerView1);
        QPlayerView playerView2 = findViewById(R.id.playerView2);
        QPlayerView playerView3 = findViewById(R.id.playerView3);
        QPlayerView playerView4 = findViewById(R.id.playerView4);

        super.onCreate(savedInstanceState);

        String streamUrl = "rtsp://192.168.88.48:554/mainstream";

        playerView.setBufferLatency(0)
                .setAudioEnabled(true)
                .setMediaSource(streamUrl)
                .setPlayback(true);

        playerView2.setBufferLatency(0)
                .setAudioEnabled(false)
                .setMediaSource(streamUrl)
                .setPlayback(true);

        playerView3.setBufferLatency(0)
                .setAudioEnabled(false)
                .setMediaSource(streamUrl)
                .setPlayback(true);

        playerView4.setBufferLatency(0)
                .setAudioEnabled(false)
                .setMediaSource(streamUrl)
                .setPlayback(true);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

    }

}
