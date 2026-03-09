package org.example.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private val GRID_PADDING = 24.dp
private const val GAP_FRACTION = 0.06f

/** Fraction of cell size the pointer must travel before a drag is recognised. */
private const val DRAG_THRESHOLD_FRACTION = 0.25f

private enum class DragDirection { UP, DOWN, LEFT, RIGHT }

private data class DragInfo(
    val sourcePos: GridPos,
    val direction: DragDirection,
    val targetPos: GridPos,
)

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
                onDragSwap = vm::onDragSwap,
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
    onDragSwap: (from: GridPos, to: GridPos) -> Unit,
    onSwapFinished: () -> Unit,
    onFallFinished: () -> Unit,
) {
    val grid = state.grid
    val swappingCells = state.swappingCells
    val fallingCells = state.fallingCells

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
        val swapCompletionDispatched = remember(swappingCells) { mutableStateOf(false) }
        val completedSwapIds = remember(swappingCells) { mutableSetOf<Int>() }

        var dragState by remember { mutableStateOf<DragInfo?>(null) }

        LaunchedEffect(fallingCells) {
            if (fallingCells.isNotEmpty()) {
                delay(SWAP_DURATION_MS.toLong())
                onFallFinished()
            }
        }

        Box(
            modifier = Modifier
                .size(gridDp)
                .pointerInput(Unit) {
                    val localCellPx = size.width.toFloat() / (n + GAP_FRACTION * (n - 1))
                    val localStride = localCellPx + localCellPx * GAP_FRACTION
                    val threshold = localCellPx * DRAG_THRESHOLD_FRACTION

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val startX = down.position.x
                        val startY = down.position.y
                        val col = (startX / localStride).toInt().coerceIn(0, n - 1)
                        val row = (startY / localStride).toInt().coerceIn(0, n - 1)
                        val startPos = GridPos(row, col)
                        val pointerId = down.id

                        var isDragging = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.find { it.id == pointerId } ?: break

                            if (!change.pressed) {
                                val currentDrag = dragState
                                if (isDragging && currentDrag != null) {
                                    onDragSwap(currentDrag.sourcePos, currentDrag.targetPos)
                                } else if (!isDragging) {
                                    onCellClick(startPos)
                                }
                                dragState = null
                                change.consume()
                                break
                            }

                            if (!isDragging) {
                                val dx = change.position.x - startX
                                val dy = change.position.y - startY
                                if (abs(dx) > threshold || abs(dy) > threshold) {
                                    isDragging = true
                                    val direction = if (abs(dx) > abs(dy)) {
                                        if (dx > 0) DragDirection.RIGHT else DragDirection.LEFT
                                    } else {
                                        if (dy > 0) DragDirection.DOWN else DragDirection.UP
                                    }
                                    val target = when (direction) {
                                        DragDirection.UP -> GridPos(row - 1, col)
                                        DragDirection.DOWN -> GridPos(row + 1, col)
                                        DragDirection.LEFT -> GridPos(row, col - 1)
                                        DragDirection.RIGHT -> GridPos(row, col + 1)
                                    }
                                    if (target.row in 0 until n && target.col in 0 until n) {
                                        dragState = DragInfo(startPos, direction, target)
                                    }
                                }
                            }
                            change.consume()
                        }
                    }
                }
        ) {
            Layout(
                content = {
                    grid.forEachIndexed { row, rowList ->
                        rowList.forEachIndexed { col, cell ->
                            key(cell.id) {
                                val pos = GridPos(row, col)
                                val isSelected = state.selected == pos
                                val swapInfo = swappingCells[cell.id]
                                val isSwapping = swapInfo != null
                                val fallInfo = fallingCells[cell.id]

                                val initialOffsetY = if (fallInfo != null && !isSwapping) {
                                    (fallInfo.first - fallInfo.second) * stride
                                } else {
                                    0f
                                }
                                val targetOffsetX = remember(swapInfo) { Animatable(0f) }
                                val targetOffsetY = remember(swapInfo, fallInfo) { Animatable(initialOffsetY) }

                                if (swapInfo != null) {
                                    val dx = (swapInfo.to.col - swapInfo.from.col) * stride
                                    val dy = (swapInfo.to.row - swapInfo.from.row) * stride
                                    LaunchedEffect(swapInfo) {
                                        targetOffsetX.snapTo(0f)
                                        targetOffsetY.snapTo(0f)
                                        val anim = tween<Float>(durationMillis = SWAP_DURATION_MS)
                                        coroutineScope {
                                            launch { targetOffsetX.animateTo(dx, anim) }
                                            launch { targetOffsetY.animateTo(dy, anim) }
                                        }

                                        if (completedSwapIds.add(cell.id) &&
                                            !swapCompletionDispatched.value &&
                                            completedSwapIds.containsAll(swappingCells.keys)
                                        ) {
                                            swapCompletionDispatched.value = true
                                            onSwapFinished()
                                        }
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
                                )
                            }
                        }
                    }
                },
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

            val currentDrag = dragState
            if (currentDrag != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val srcX = currentDrag.sourcePos.col * stride + cellPx / 2f
                    val srcY = currentDrag.sourcePos.row * stride + cellPx / 2f
                    val tgtX = currentDrag.targetPos.col * stride + cellPx / 2f
                    val tgtY = currentDrag.targetPos.row * stride + cellPx / 2f

                    val arrowColor = Color.White.copy(alpha = 0.85f)
                    val lineWidth = cellPx * 0.07f
                    val headSize = cellPx * 0.25f

                    drawLine(
                        color = arrowColor,
                        start = Offset(srcX, srcY),
                        end = Offset(tgtX, tgtY),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round,
                    )

                    val headPath = Path().apply {
                        when (currentDrag.direction) {
                            DragDirection.RIGHT -> {
                                moveTo(tgtX + headSize * 0.5f, tgtY)
                                lineTo(tgtX - headSize * 0.5f, tgtY - headSize * 0.5f)
                                lineTo(tgtX - headSize * 0.5f, tgtY + headSize * 0.5f)
                            }
                            DragDirection.LEFT -> {
                                moveTo(tgtX - headSize * 0.5f, tgtY)
                                lineTo(tgtX + headSize * 0.5f, tgtY - headSize * 0.5f)
                                lineTo(tgtX + headSize * 0.5f, tgtY + headSize * 0.5f)
                            }
                            DragDirection.DOWN -> {
                                moveTo(tgtX, tgtY + headSize * 0.5f)
                                lineTo(tgtX - headSize * 0.5f, tgtY - headSize * 0.5f)
                                lineTo(tgtX + headSize * 0.5f, tgtY - headSize * 0.5f)
                            }
                            DragDirection.UP -> {
                                moveTo(tgtX, tgtY - headSize * 0.5f)
                                lineTo(tgtX - headSize * 0.5f, tgtY + headSize * 0.5f)
                                lineTo(tgtX + headSize * 0.5f, tgtY + headSize * 0.5f)
                            }
                        }
                        close()
                    }
                    drawPath(headPath, arrowColor)
                }
            }
        }
    }
}

@Composable
private fun JellyItem(
    cell: JellyCell,
    cellSize: Dp,
    isSelected: Boolean,
    offsetX: Float,
    offsetY: Float,
) {
    Box(
        modifier = Modifier
            .size(cellSize)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
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
