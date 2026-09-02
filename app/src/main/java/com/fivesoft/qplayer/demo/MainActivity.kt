package com.fivesoft.qplayer.demo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fivesoft.qplayer.compose.PlayerEntity
import com.fivesoft.qplayer.compose.PlayerSize
import com.fivesoft.qplayer.compose.QPlayerGridCompose
import com.fivesoft.qplayer.compose.QPlayerGridState
import com.fivesoft.qplayer.compose.QPlayerState
import java.net.URI
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences(PLAYER_PREFERENCES, MODE_PRIVATE)
        val savedStreamUrl = preferences.getString(PREF_STREAM_URL, STREAM_URL) ?: STREAM_URL
        val savedLatencyMs = preferences.getInt(PREF_LATENCY_MS, DEFAULT_LATENCY_MS)
        val savedDrivingMode = preferences.getBoolean(PREF_DRIVING_MODE, DEFAULT_DRIVING_MODE)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContent {
            PlayerGrid(
                initialStreamUrl = savedStreamUrl,
                initialLatencyMs = savedLatencyMs,
                initialDrivingMode = savedDrivingMode,
                onSettingsChanged = { streamUrl, latencyMs, drivingMode ->
                    preferences.edit()
                        .putString(PREF_STREAM_URL, streamUrl)
                        .putInt(PREF_LATENCY_MS, latencyMs)
                        .putBoolean(PREF_DRIVING_MODE, drivingMode)
                        .apply()
                }
            )
        }
    }
}

@Composable
private fun PlayerGrid(
    initialStreamUrl: String,
    initialLatencyMs: Int,
    initialDrivingMode: Boolean,
    onSettingsChanged: (String, Int, Boolean) -> Unit
) {
    var streamUrl by rememberSaveable { mutableStateOf(initialStreamUrl) }
    var latencyMs by rememberSaveable { mutableIntStateOf(initialLatencyMs) }
    var drivingMode by rememberSaveable { mutableStateOf(initialDrivingMode) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    val playerState = remember {
        QPlayerState(
            bufferLatency = mutableIntStateOf(latencyMs),
            mediaSource = mutableStateOf(URI.create(streamUrl)),
            audioEnabled = mutableStateOf(true)
        )
    }
    val gridState = remember(playerState) {
        val players = mutableStateListOf(
            PlayerEntity(
                state = playerState,
                aspectRatio = PLAYER_ASPECT_RATIO
            )
        )
        QPlayerGridState(players)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        QPlayerGridCompose(
            state = gridState,
            modifier = Modifier.fillMaxSize()
        )

        if (drivingMode) {
            DrivingControlsOverlay(
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        IconButton(
            onClick = { settingsVisible = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.End
                    )
                )
                .padding(8.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                .semantics { contentDescription = "Ustawienia odtwarzacza" }
        ) {
            Text(
                text = "\u2699",
                color = Color.White,
                fontSize = 22.sp
            )
        }
    }

    if (settingsVisible) {
        StreamSettingsDialog(
            initialStreamUrl = streamUrl,
            initialLatencyMs = latencyMs,
            initialDrivingMode = drivingMode,
            onDismiss = { settingsVisible = false },
            onConfirm = { streamUri, newLatencyMs, newDrivingMode ->
                val newStreamUrl = streamUri.toString()
                streamUrl = newStreamUrl
                latencyMs = newLatencyMs
                drivingMode = newDrivingMode
                playerState.mediaSource.value = streamUri
                playerState.bufferLatency.intValue = newLatencyMs
                onSettingsChanged(newStreamUrl, newLatencyMs, newDrivingMode)
                settingsVisible = false
            }
        )
    }
}

@Composable
private fun StreamSettingsDialog(
    initialStreamUrl: String,
    initialLatencyMs: Int,
    initialDrivingMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (URI, Int, Boolean) -> Unit
) {
    var streamUrl by remember(initialStreamUrl) { mutableStateOf(initialStreamUrl) }
    var latencyMs by remember(initialLatencyMs) { mutableIntStateOf(initialLatencyMs) }
    var drivingMode by remember(initialDrivingMode) { mutableStateOf(initialDrivingMode) }
    val streamUri = remember(streamUrl) { streamUrl.toStreamUriOrNull() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ustawienia odtwarzacza") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Adres streamu") },
                    singleLine = true,
                    isError = streamUri == null,
                    supportingText = if (streamUri == null) {
                        { Text("Podaj pełny, poprawny adres streamu.") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Latency")
                        Text("$latencyMs ms")
                    }
                    Slider(
                        value = latencyMs.toFloat(),
                        onValueChange = { latencyMs = it.roundToInt() },
                        valueRange = 0f..MAX_LATENCY_MS.toFloat()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tryb jazdy")
                    Switch(
                        checked = drivingMode,
                        onCheckedChange = { drivingMode = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = streamUri != null,
                onClick = {
                    onConfirm(requireNotNull(streamUri), latencyMs, drivingMode)
                }
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}

private fun String.toStreamUriOrNull(): URI? = trim()
    .takeIf(String::isNotEmpty)
    ?.let { value -> runCatching { URI.create(value) }.getOrNull() }
    ?.takeIf { uri -> uri.isAbsolute && uri.rawSchemeSpecificPart.isNotEmpty() }

private val PLAYER_SIZES = listOf(
    PlayerSize.ExtraLarge,
    PlayerSize.Large,
    PlayerSize.Medium,
    PlayerSize.Small
)

private const val PLAYER_ASPECT_RATIO = 16f / 9f
private const val MAX_LATENCY_MS = 2_000
private const val DEFAULT_LATENCY_MS = 0
private const val DEFAULT_DRIVING_MODE = true
private const val STREAM_URL = ""
private const val PLAYER_PREFERENCES = "player_preferences"
private const val PREF_STREAM_URL = "stream_url"
private const val PREF_LATENCY_MS = "latency_ms"
private const val PREF_DRIVING_MODE = "driving_mode"
