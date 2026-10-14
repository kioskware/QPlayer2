package com.fivesoft.qplayer.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import com.fivesoft.qplayer.base.Authentication
import com.fivesoft.qplayer.base.QPlayer
import com.fivesoft.qplayer.base.Sample
import com.fivesoft.qplayer.base.components.DataSource
import com.fivesoft.qplayer.base.track.Track
import com.fivesoft.qplayer.base.track.TrackListener
import com.fivesoft.qplayer.base.track.Tracks
import com.fivesoft.qplayer.common.measure.BitrateMeasure
import com.fivesoft.qplayer.core.KeepScreenOnMode
import com.fivesoft.qplayer.core.QPlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI

/**
 * Composable state of the [QPlayer].
 * Use with [QPlayerCompose].
 */
data class QPlayerComposeState(
    val playback: MutableState<Boolean> = mutableStateOf(true),
    val bufferLatency: MutableIntState = mutableIntStateOf(QPlayer.DEFAULT_BUFFER_LATENCY),
    val decoderLowLatencyMode: MutableState<Boolean> = mutableStateOf(QPlayer.DEFAULT_LOW_LATENCY_MODE),
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
) {
    internal var playerView: QPlayerView? = null
    internal val _tracks: MutableStateFlow<Tracks> = MutableStateFlow(Tracks())
    val tracks = _tracks.asStateFlow()
}

/**
 * Convenient method to remember state of the QPlayer inside composable.
 */
@Composable
fun rememberQPlayerState(state: QPlayerComposeState = QPlayerComposeState()) = remember { state }

/**
 * Compose wrapper for [QPlayer] view. For more details, see [QPlayer] docs.
 *
 * @param state to control
 */
@Composable
fun QPlayerCompose(
    modifier: Modifier = Modifier,
    state: QPlayerComposeState = rememberQPlayerState(),
) {
    Box(
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                QPlayerView(context).apply {
                    state.playerView = this
                    state._tracks.value = this.tracks
                }
            },
            update = { view ->
                view.apply {
                    setBufferLatency(state.bufferLatency.intValue.toLong())
                    setDecoderLowLatency(state.decoderLowLatencyMode.value)
                    setAuthentication(state.authentication.value)
                    setVideoEnabled(state.videoEnabled.value)
                    setAudioEnabled(state.audioEnabled.value)
                    setSubtitlesEnabled(state.subtitlesEnabled.value)
                    setKeepScreenOnMode(state.keepScreenOnMode.value)
                    setFirstFramesCount(state.firstFramesCount.intValue)
                    setConnectTimeout(state.connectTimeout.intValue)
                    setReadTimeout(state.readTimeout.intValue)
                    setVolume(state.volume.floatValue)
                    setMediaSource(state.mediaSource.value)
                    setPlayback(state.playback.value)
                }
            },
            onRelease = { view -> view.release() },
            modifier = Modifier.fillMaxSize()
        )
    }

}

val QPlayerComposeState.statVideoBitrate: Double
    get() = tracks.value.videoTrack?.statBitrate ?: Track.UNKNOWN.toDouble()

val QPlayerComposeState.statAudioBitrate: Double
    get() = tracks.value.audioTrack?.statBitrate ?: Track.UNKNOWN.toDouble()

val QPlayerComposeState.statFps: Double
    get() = tracks.value.videoTrack?.statFps ?: Track.UNKNOWN.toDouble()

enum class TrackPlaybackState {
    Idle, Connecting, Decoding, Playing
}

interface TrackPlaybackStates {
    val videoTrackState: StateFlow<TrackPlaybackState>
    val audioTrackState: StateFlow<TrackPlaybackState>
    val subtitleTrackState: StateFlow<TrackPlaybackState>
}
