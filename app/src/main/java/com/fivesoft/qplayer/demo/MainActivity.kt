package com.fivesoft.qplayer.demo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.fivesoft.qplayer.compose.PlayerEntity
import com.fivesoft.qplayer.compose.PlayerSize
import com.fivesoft.qplayer.compose.QPlayerGridCompose
import com.fivesoft.qplayer.compose.QPlayerGridState
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
    val gridState = remember(streamUri) {
        val players = mutableStateListOf<PlayerEntity>()
        PLAYER_SIZES.forEachIndexed { index, size ->
            players += PlayerEntity(
                state = QPlayerState(
                    bufferLatency = mutableIntStateOf(0),
                    mediaSource = mutableStateOf(streamUri),
                    audioEnabled = mutableStateOf(index == 0)
                ),
                aspectRatio = PLAYER_ASPECT_RATIO,
                size = size
            )
        }
        QPlayerGridState(players)
    }

    QPlayerGridCompose(
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}

private val PLAYER_SIZES = listOf(
    PlayerSize.ExtraLarge,
    PlayerSize.Large,
    PlayerSize.Medium,
    PlayerSize.Small
)

private const val PLAYER_ASPECT_RATIO = 16f / 9f
private const val STREAM_URL = "rtsp://192.168.88.48:554/mainstream"
