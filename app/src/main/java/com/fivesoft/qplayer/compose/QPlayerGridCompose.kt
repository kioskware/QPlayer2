package com.fivesoft.qplayer.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class PlayerSize(val weight: Int) {
    VerySmall(-2),
    Small(-1),
    Medium(0),
    Large(1),
    ExtraLarge(2)
}

data class PlayerEntity(
    val state: QPlayerComposeState,
    val name: String? = null,
    val aspectRatio: Float = 16f / 9f,
    val size: PlayerSize = PlayerSize.Medium
)

class QPlayerGridState(
    val players: SnapshotStateList<PlayerEntity> = mutableStateListOf()
)

@Composable
fun rememberQPlayerGridState(state: QPlayerGridState = QPlayerGridState()) = remember { state }

@Composable
fun QPlayerGridCompose(
    modifier: Modifier = Modifier,
    state: QPlayerGridState = rememberQPlayerGridState()
) {
    val players = state.players.toList()

    Layout(
        modifier = modifier,
        content = {
            players.forEach { player ->
                QPlayerCompose(state = player.state)
            }
        }
    ) { measurables, constraints ->
        val width = constraints.resolvedWidth
        val height = constraints.resolvedHeight

        if (measurables.isEmpty() || width == 0 || height == 0) {
            measurables.forEach { measurable ->
                measurable.measure(Constraints.fixed(0, 0))
            }
            layout(width, height) {}
        } else {
            val bounds = calculatePlayerBounds(players, width, height)
            val placeables = measurables.mapIndexed { index, measurable ->
                val playerBounds = bounds[index]
                measurable.measure(
                    Constraints.fixed(playerBounds.width, playerBounds.height)
                )
            }

            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    val playerBounds = bounds[index]
                    placeable.place(playerBounds.x, playerBounds.y)
                }
            }
        }
    }
}

private val Constraints.resolvedWidth: Int
    get() = if (hasBoundedWidth) maxWidth else minWidth

private val Constraints.resolvedHeight: Int
    get() = if (hasBoundedHeight) maxHeight else minHeight

private fun calculatePlayerBounds(
    players: List<PlayerEntity>,
    containerWidth: Int,
    containerHeight: Int
): List<PlayerBounds> {
    if (players.size == 1) {
        return listOf(
            PlayerBounds(
                x = 0,
                y = 0,
                width = containerWidth,
                height = containerHeight
            )
        )
    }

    val items = players
        .mapIndexed { index, player -> player.toLayoutItem(index) }
        .sortedWith(
            compareByDescending<LayoutItem> { it.targetArea }
                .thenByDescending { it.baseHeight }
                .thenBy { it.index }
        )

    var bestCandidate: LayoutCandidate? = null
    partitions(items).forEach { rows ->
        val candidate = evaluateLayout(rows, containerWidth, containerHeight)
        val currentBest = bestCandidate
        if (currentBest == null || candidate.score > currentBest.score) {
            bestCandidate = candidate
        }
    }

    return requireNotNull(bestCandidate).rectangles
        .map { rectangle -> rectangle.toPlayerBounds(containerWidth, containerHeight) }
}

private fun PlayerEntity.toLayoutItem(index: Int): LayoutItem {
    val safeAspectRatio = aspectRatio
        .takeIf { it.isFinite() && it > MIN_ASPECT_RATIO }
        ?.toDouble()
        ?: DEFAULT_ASPECT_RATIO
    val targetArea = SIZE_STEP.pow(size.weight.toDouble())

    return LayoutItem(
        index = index,
        targetArea = targetArea,
        baseWidth = sqrt(targetArea * safeAspectRatio),
        baseHeight = sqrt(targetArea / safeAspectRatio)
    )
}

private fun partitions(items: List<LayoutItem>): Sequence<List<List<LayoutItem>>> = sequence {
    if (items.size <= MAX_EXHAUSTIVE_PLAYERS) {
        val partitionCount = 1 shl (items.size - 1)
        repeat(partitionCount) { mask ->
            val rows = mutableListOf<MutableList<LayoutItem>>()
            var currentRow = mutableListOf<LayoutItem>()
            rows += currentRow

            items.forEachIndexed { index, item ->
                currentRow += item
                if (index < items.lastIndex && mask and (1 shl index) != 0) {
                    currentRow = mutableListOf()
                    rows += currentRow
                }
            }
            yield(rows)
        }
    } else {
        for (rowCount in 1..min(items.size, MAX_FALLBACK_ROWS)) {
            yield(items.partitionEvenly(rowCount))
        }
    }
}

private fun List<LayoutItem>.partitionEvenly(rowCount: Int): List<List<LayoutItem>> {
    val rows = mutableListOf<List<LayoutItem>>()
    var nextIndex = 0

    repeat(rowCount) { rowIndex ->
        val remainingItems = size - nextIndex
        val remainingRows = rowCount - rowIndex
        val itemsInRow = ceil(remainingItems.toDouble() / remainingRows).toInt()
        rows += subList(nextIndex, nextIndex + itemsInRow)
        nextIndex += itemsInRow
    }

    return rows
}

private fun evaluateLayout(
    rows: List<List<LayoutItem>>,
    containerWidth: Int,
    containerHeight: Int
): LayoutCandidate {
    val rowLayouts = rows.map { items ->
        RowLayout(
            items = items,
            baseWidth = items.sumOf(LayoutItem::baseWidth),
            baseHeight = items.maxOf(LayoutItem::baseHeight)
        )
    }
    val widthCaps = rowLayouts.map { row -> containerWidth / row.baseWidth }
    val commonScale = min(
        widthCaps.min(),
        containerHeight / rowLayouts.sumOf(RowLayout::baseHeight)
    )
    val rowScales = DoubleArray(rowLayouts.size) { commonScale }

    distributeRemainingHeight(
        rows = rowLayouts,
        scales = rowScales,
        widthCaps = widthCaps,
        containerHeight = containerHeight.toDouble()
    )

    val totalHeight = rowLayouts.indices.sumOf { index ->
        rowLayouts[index].baseHeight * rowScales[index]
    }
    var y = (containerHeight - totalHeight) / 2.0
    val rectangles = MutableList(rowLayouts.sumOf { it.items.size }) { LayoutRectangle.Zero }

    rowLayouts.forEachIndexed { rowIndex, row ->
        val scale = rowScales[rowIndex]
        val rowWidth = row.baseWidth * scale
        val rowHeight = row.baseHeight * scale
        var x = (containerWidth - rowWidth) / 2.0

        row.items.forEach { item ->
            val itemWidth = item.baseWidth * scale
            val itemHeight = item.baseHeight * scale
            rectangles[item.index] = LayoutRectangle(
                x = x,
                y = y + (rowHeight - itemHeight) / 2.0,
                width = itemWidth,
                height = itemHeight
            )
            x += itemWidth
        }
        y += rowHeight
    }

    val usedArea = rowLayouts.indices.sumOf { rowIndex ->
        rowLayouts[rowIndex].items.sumOf(LayoutItem::targetArea) *
            rowScales[rowIndex].pow(2)
    }
    val utilization = usedArea / (containerWidth.toDouble() * containerHeight)
    val scaleDistortion = calculateScaleDistortion(rowLayouts, rowScales)

    return LayoutCandidate(
        rectangles = rectangles,
        score = utilization - scaleDistortion * SIZE_DISTORTION_PENALTY
    )
}

private fun distributeRemainingHeight(
    rows: List<RowLayout>,
    scales: DoubleArray,
    widthCaps: List<Double>,
    containerHeight: Double
) {
    var remainingHeight = containerHeight - rows.indices.sumOf { index ->
        rows[index].baseHeight * scales[index]
    }
    val scalableRows = rows.indices
        .filterTo(mutableSetOf()) { index -> scales[index] < widthCaps[index] - EPSILON }

    while (remainingHeight > EPSILON && scalableRows.isNotEmpty()) {
        val scaleStepToNextCap = scalableRows.minOf { index ->
            widthCaps[index] - scales[index]
        }
        val scalableHeight = scalableRows.sumOf { index -> rows[index].baseHeight }
        val scaleStep = min(scaleStepToNextCap, remainingHeight / scalableHeight)

        scalableRows.forEach { index -> scales[index] += scaleStep }
        remainingHeight -= scaleStep * scalableHeight
        scalableRows.removeAll { index ->
            widthCaps[index] - scales[index] <= EPSILON
        }
    }
}

private fun calculateScaleDistortion(
    rows: List<RowLayout>,
    scales: DoubleArray
): Double {
    val itemCount = rows.sumOf { it.items.size }
    val meanLogScale = rows.indices.sumOf { index ->
        rows[index].items.size * ln(scales[index])
    } / itemCount
    val variance = rows.indices.sumOf { index ->
        rows[index].items.size * (ln(scales[index]) - meanLogScale).pow(2)
    } / itemCount

    return sqrt(variance)
}

private fun LayoutRectangle.toPlayerBounds(
    containerWidth: Int,
    containerHeight: Int
): PlayerBounds {
    val left = x.roundToInt().coerceIn(0, containerWidth)
    val top = y.roundToInt().coerceIn(0, containerHeight)
    val right = (x + width).roundToInt().coerceIn(left, containerWidth)
    val bottom = (y + height).roundToInt().coerceIn(top, containerHeight)

    return PlayerBounds(
        x = left,
        y = top,
        width = right - left,
        height = bottom - top
    )
}

private data class LayoutItem(
    val index: Int,
    val targetArea: Double,
    val baseWidth: Double,
    val baseHeight: Double
)

private data class RowLayout(
    val items: List<LayoutItem>,
    val baseWidth: Double,
    val baseHeight: Double
)

private data class LayoutCandidate(
    val rectangles: List<LayoutRectangle>,
    val score: Double
)

private data class LayoutRectangle(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
) {
    companion object {
        val Zero = LayoutRectangle(0.0, 0.0, 0.0, 0.0)
    }
}

private data class PlayerBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

private const val DEFAULT_ASPECT_RATIO = 16.0 / 9.0
private const val MIN_ASPECT_RATIO = 0.01f
private const val SIZE_STEP = 1.6
private const val SIZE_DISTORTION_PENALTY = 0.08
private const val EPSILON = 0.000_001
private const val MAX_EXHAUSTIVE_PLAYERS = 12
private const val MAX_FALLBACK_ROWS = 12
