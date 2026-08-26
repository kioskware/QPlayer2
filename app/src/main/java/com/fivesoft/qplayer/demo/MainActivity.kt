package com.fivesoft.qplayer.demo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.fivesoft.qplayer.compose.QPlayerCompose
import com.fivesoft.qplayer.compose.QPlayerState
import java.net.URI

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContent {
            PlayerGrid()
        }
    }
}

@Composable
private fun PlayerGrid() {
    val streamUri = remember { URI.create(STREAM_URL) }
    val playerStates = remember(streamUri) {
        List(PLAYER_COUNT) { index ->
            QPlayerState(
                bufferLatency = mutableIntStateOf(0),
                mediaSource = mutableStateOf(streamUri),
                audioEnabled = mutableStateOf(index == 0)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        QPlayerCompose(
            state = playerStates[0],
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.5f)
                .align(Alignment.TopStart)
        )
        QPlayerCompose(
            state = playerStates[1],
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.5f)
                .align(Alignment.BottomStart)
        )
        QPlayerCompose(
            state = playerStates[2],
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.5f)
                .align(Alignment.TopEnd)
        )
        QPlayerCompose(
            state = playerStates[3],
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.5f)
                .align(Alignment.BottomEnd)
        )
    }
}

private const val PLAYER_COUNT = 4
private const val STREAM_URL = "rtsp://192.168.88.48:554/mainstream"
