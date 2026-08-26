package com.fivesoft.qplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.fivesoft.qplayer.base.Authentication
import com.fivesoft.qplayer.base.QPlayer
import com.fivesoft.qplayer.base.components.DataSource
import com.fivesoft.qplayer.core.KeepScreenOnMode
import com.fivesoft.qplayer.core.QPlayerView
import java.net.URI

data class QPlayerState(
    val playback: MutableState<Boolean> = mutableStateOf(true),
    val bufferLatency: MutableIntState = mutableIntStateOf(QPlayer.DEFAULT_BUFFER_LATENCY),
    val mediaSource: MutableState<URI> = mutableStateOf(URI.create("")),
    val authentication: MutableState<Authentication?> = mutableStateOf(null),
    val videoEnabled: MutableState<Boolean> = mutableStateOf(true),
    val audioEnabled: MutableState<Boolean> = mutableStateOf(true),
    val subtitlesEnabled: MutableState<Boolean> = mutableStateOf(false),
    val keepScreenOnMode: MutableState<KeepScreenOnMode> = mutableStateOf(KeepScreenOnMode.WHEN_STARTED),
    val firstFramesCount: MutableIntState = mutableIntStateOf(5),
    val readTimeout: MutableIntState = mutableIntStateOf(DataSource.DEFAULT_IO_TIMEOUT),
    val connectTimeout: MutableIntState = mutableIntStateOf(DataSource.DEFAULT_CONNECT_TIMEOUT),
    val volume: MutableFloatState = mutableFloatStateOf(QPlayer.DEFAULT_VOLUME)
)

@Composable
fun rememberQPlayerState(state: QPlayerState = QPlayerState()) = remember { state }

@Composable
fun QPlayerCompose(
    modifier: Modifier = Modifier,
    state: QPlayerState = rememberQPlayerState(),
) {
    AndroidView(
        factory = { context ->
            QPlayerView(context).apply {

            }
        },
        update = { view ->
            view.setPlayback(state.playback.value)
            view.setBufferLatency(state.bufferLatency.intValue.toLong())
            view.setMediaSource(state.mediaSource.value)
            view.setAuthentication(state.authentication.value)
            view.setVideoEnabled(state.videoEnabled.value)
            view.setAudioEnabled(state.audioEnabled.value)
            view.setSubtitlesEnabled(state.subtitlesEnabled.value)
            view.setKeepScreenOnMode(state.keepScreenOnMode.value)
            view.setFirstFramesCount(state.firstFramesCount.intValue)
            view.setConnectTimeout(state.connectTimeout.intValue)
            view.setReadTimeout(state.readTimeout.intValue)
            view.setVolume(state.volume.floatValue)
        },
        onRelease = { view -> view.release() },
        modifier = modifier.fillMaxSize()
    )
}
