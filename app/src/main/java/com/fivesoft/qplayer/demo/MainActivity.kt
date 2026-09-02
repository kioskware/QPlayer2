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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
    var streamUrl by rememberSaveable { mutableStateOf(STREAM_URL) }
    var latencyMs by rememberSaveable { mutableIntStateOf(0) }
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

        IconButton(
            onClick = { settingsVisible = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
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
            onDismiss = { settingsVisible = false },
            onConfirm = { streamUri, newLatencyMs ->
                streamUrl = streamUri.toString()
                latencyMs = newLatencyMs
                playerState.mediaSource.value = streamUri
                playerState.bufferLatency.intValue = newLatencyMs
                settingsVisible = false
            }
        )
    }
}

@Composable
private fun StreamSettingsDialog(
    initialStreamUrl: String,
    initialLatencyMs: Int,
    onDismiss: () -> Unit,
    onConfirm: (URI, Int) -> Unit
) {
    var streamUrl by remember(initialStreamUrl) { mutableStateOf(initialStreamUrl) }
    var latencyMs by remember(initialLatencyMs) { mutableIntStateOf(initialLatencyMs) }
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = streamUri != null,
                onClick = { onConfirm(requireNotNull(streamUri), latencyMs) }
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
private const val STREAM_URL = "rtsp://170.93.143.139/rtplive/470011e600ef003a004ee33696235daa"
