package com.fivesoft.qplayer.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fivesoft.qplayer.steering.SteeringInputState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun DrivingControlsOverlay(
    steeringState: StateFlow<SteeringInputState>,
    modifier: Modifier = Modifier
) {
    val state by steeringState.collectAsState()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < 480.dp
        val wheelSize = if (compact) 104.dp else 144.dp
        val brakeWidth = if (compact) 50.dp else 60.dp
        val brakeHeight = if (compact) 78.dp else 92.dp
        val gasWidth = if (compact) 62.dp else 74.dp
        val gasHeight = if (compact) 102.dp else 120.dp
        val edgePadding = if (compact) 16.dp else 32.dp
        val pedalSpacing = if (compact) 10.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .padding(horizontal = edgePadding, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (state.connected) {
                    state.statusMessage ?: state.deviceName ?: "Logitech G920"
                } else {
                    "Logitech G920 — nie podłączono"
                },
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.34f), STATUS_SHAPE)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .alpha(if (state.inputAvailable) 1f else 0.48f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                SteeringWheel(
                    size = wheelSize,
                    value = state.steering
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(pedalSpacing),
                    verticalAlignment = Alignment.Bottom
                ) {
                    DrivingPedal(
                        label = "HAMULEC",
                        value = state.brake,
                        accent = Color(0xFFAAAEB4),
                        width = brakeWidth,
                        height = brakeHeight
                    )
                    DrivingPedal(
                        label = "GAZ",
                        value = state.accelerator,
                        accent = Color(0xFFD0D2D5),
                        width = gasWidth,
                        height = gasHeight
                    )
                }
            }
        }
    }
}

@Composable
private fun SteeringWheel(size: Dp, value: Float) {
    val safeValue = value.coerceIn(-1f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.semantics {
            contentDescription = "Kierownica ${(safeValue * 100).roundToInt()} procent"
        }
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .graphicsLayer(rotationZ = safeValue * MAX_STEERING_INDICATOR_ROTATION)
        ) {
            val centerPoint = center
            val strokeWidth = this.size.minDimension * 0.075f
            val outerRadius = this.size.minDimension / 2f - strokeWidth
            val hubRadius = outerRadius * 0.25f

            drawCircle(
                color = Color.Black.copy(alpha = 0.30f),
                radius = this.size.minDimension / 2f
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.62f),
                radius = outerRadius,
                style = Stroke(width = strokeWidth)
            )

            listOf(-90f, 30f, 150f).forEach { angle ->
                val radians = angle / 180f * PI
                val directionX = cos(radians).toFloat()
                val directionY = sin(radians).toFloat()
                drawLine(
                    color = Color.White.copy(alpha = 0.56f),
                    start = Offset(
                        centerPoint.x + directionX * hubRadius,
                        centerPoint.y + directionY * hubRadius
                    ),
                    end = Offset(
                        centerPoint.x + directionX * outerRadius * 0.82f,
                        centerPoint.y + directionY * outerRadius * 0.82f
                    ),
                    strokeWidth = strokeWidth * 0.72f,
                    cap = StrokeCap.Round
                )
            }

            drawCircle(
                color = Color.Black.copy(alpha = 0.42f),
                radius = hubRadius
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.58f),
                radius = hubRadius,
                style = Stroke(width = strokeWidth * 0.55f)
            )
        }
        Text(
            text = "KIEROWNICA ${formatSignedPercent(safeValue)}",
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun DrivingPedal(
    label: String,
    value: Float,
    accent: Color,
    width: Dp,
    height: Dp
) {
    val safeValue = value.coerceIn(0f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.semantics {
            contentDescription = "$label ${(safeValue * 100).roundToInt()} procent"
        }
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .background(Color.Black.copy(alpha = 0.34f), PEDAL_SHAPE)
                .border(2.dp, accent.copy(alpha = 0.68f), PEDAL_SHAPE)
                .padding(horizontal = 12.dp, vertical = 15.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pressedHeight = size.height * safeValue
                drawRoundRect(
                    color = accent.copy(alpha = 0.18f + safeValue * 0.52f),
                    topLeft = Offset(0f, size.height - pressedHeight),
                    size = Size(size.width, pressedHeight),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
                repeat(4) { index ->
                    val y = size.height * (index + 1) / 5f
                    drawLine(
                        color = Color.White.copy(alpha = 0.45f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        Text(
            text = "$label ${(safeValue * 100).roundToInt()}%",
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 10.sp
        )
    }
}

private fun formatSignedPercent(value: Float): String {
    val percent = (value * 100).roundToInt()
    return if (percent > 0) "+$percent%" else "$percent%"
}

private val PEDAL_SHAPE = RoundedCornerShape(16.dp)
private val STATUS_SHAPE = RoundedCornerShape(50)
private const val MAX_STEERING_INDICATOR_ROTATION = 225f
