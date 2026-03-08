package org.example.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.jelly_1
import kotlinproject.composeapp.generated.resources.jelly_2
import kotlinproject.composeapp.generated.resources.jelly_3
import kotlinproject.composeapp.generated.resources.jelly_4
import kotlinproject.composeapp.generated.resources.jelly_5
import kotlinproject.composeapp.generated.resources.jelly_6
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.min
import kotlin.math.roundToInt

private val GRID_PADDING = 24.dp
private const val GAP_FRACTION = 0.06f

@Composable
fun App() {
    val vm = remember { GameViewModel() }
    val state = vm.state

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            GameGrid(
                state = state,
                padding = GRID_PADDING,
                onCellClick = vm::onCellClick,
                onSwapFinished = vm::onSwapAnimationFinished,
                onFallFinished = vm::onFallAnimationFinished,
            )
        }
    }
}

@Composable
private fun GameGrid(
    state: GameState,
    padding: Dp,
    onCellClick: (GridPos) -> Unit,
    onSwapFinished: () -> Unit,
    onFallFinished: () -> Unit,
) {
    val swapA = state.swappingA
    val swapB = state.swappingB
    val grid = state.grid
    val fallingCells = state.fallingCells

    // BoxWithConstraints gives us maxWidth/maxHeight in Dp during composition,
    // so stride is available where LaunchedEffect runs.
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val paddingPx = with(density) { padding.toPx() }
        val available = min(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()) - paddingPx * 2
        val n = GRID_SIZE
        val cellPx = available / (n + GAP_FRACTION * (n - 1))
        val gapPx = cellPx * GAP_FRACTION
        val stride = cellPx + gapPx
        val cellDp = with(density) { cellPx.toDp() }
        val gridDp = with(density) { (n * cellPx + (n - 1) * gapPx).toDp() }

        LaunchedEffect(fallingCells) {
            if (fallingCells.isNotEmpty()) {
                delay(SWAP_DURATION_MS.toLong())
                onFallFinished()
            }
        }

        Layout(
            content = {
                grid.forEachIndexed { row, rowList ->
                    rowList.forEachIndexed { col, cell ->
                        val pos = GridPos(row, col)
                        val isSelected = state.selected == pos
                        val isSwapping = pos == swapA || pos == swapB
                        val fallInfo = fallingCells[cell.id]

                        // Initialize the Y Animatable at the correct fall offset
                        // so the very first frame already shows the cell at its
                        // pre-animation position (eliminates the one-frame flicker
                        // that occurred when Animatable started at 0).
                        val initialOffsetY = if (fallInfo != null && !isSwapping) {
                            (fallInfo.first - fallInfo.second) * stride
                        } else {
                            0f
                        }
                        val targetOffsetX = remember(cell.id) { Animatable(0f) }
                        val targetOffsetY = remember(cell.id) { Animatable(initialOffsetY) }

                        if (isSwapping && swapA != null && swapB != null) {
                            val dx = if (pos == swapA) (swapB.col - swapA.col) * stride
                                     else (swapA.col - swapB.col) * stride
                            val dy = if (pos == swapA) (swapB.row - swapA.row) * stride
                                     else (swapA.row - swapB.row) * stride
                            LaunchedEffect(swapA, swapB) {
                                targetOffsetX.snapTo(0f)
                                targetOffsetY.snapTo(0f)
                                val anim = tween<Float>(durationMillis = SWAP_DURATION_MS)
                                coroutineScope {
                                    launch { targetOffsetX.animateTo(dx, anim) }
                                    launch { targetOffsetY.animateTo(dy, anim) }
                                }
                                if (pos == swapA) onSwapFinished()
                            }
                        } else if (fallInfo != null) {
                            val dy = (fallInfo.first - fallInfo.second) * stride
                            LaunchedEffect(fallingCells) {
                                targetOffsetY.snapTo(dy)
                                val anim = tween<Float>(durationMillis = SWAP_DURATION_MS)
                                targetOffsetY.animateTo(0f, anim)
                            }
                        } else {
                            LaunchedEffect(Unit) {
                                targetOffsetX.snapTo(0f)
                                targetOffsetY.snapTo(0f)
                            }
                        }

                        JellyItem(
                            cell = cell,
                            cellSize = cellDp,
                            isSelected = isSelected,
                            offsetX = targetOffsetX.value,
                            offsetY = targetOffsetY.value,
                            onClick = { onCellClick(pos) },
                        )
                    }
                }
            },
            modifier = Modifier.size(gridDp),
            measurePolicy = { measurables, _ ->
                val cellConstraints = Constraints.fixed(cellPx.roundToInt(), cellPx.roundToInt())
                val placeables = measurables.map { it.measure(cellConstraints) }
                val gridSizePx = (n * cellPx + (n - 1) * gapPx).roundToInt()
                layout(gridSizePx, gridSizePx) {
                    placeables.forEachIndexed { index, placeable ->
                        placeable.placeRelative(
                            x = (index % n * stride).roundToInt(),
                            y = (index / n * stride).roundToInt(),
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun JellyItem(
    cell: JellyCell,
    cellSize: Dp,
    isSelected: Boolean,
    offsetX: Float,
    offsetY: Float,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(cellSize)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(3.dp, Color.White)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(cell.type.toDrawableRes()),
            contentDescription = "Jelly ${cell.type}",
            modifier = Modifier.size(cellSize),
            contentScale = ContentScale.Fit,
        )
    }
}

private fun Int.toDrawableRes(): DrawableResource = when (this) {
    1 -> Res.drawable.jelly_1
    2 -> Res.drawable.jelly_2
    3 -> Res.drawable.jelly_3
    4 -> Res.drawable.jelly_4
    5 -> Res.drawable.jelly_5
    else -> Res.drawable.jelly_6
}

