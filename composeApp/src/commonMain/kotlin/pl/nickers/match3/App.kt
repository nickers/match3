package pl.nickers.match3

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.bomb
import kotlinproject.composeapp.generated.resources.bomb_face_1
import kotlinproject.composeapp.generated.resources.bomb_face_2
import kotlinproject.composeapp.generated.resources.face_1
import kotlinproject.composeapp.generated.resources.face_2
import kotlinproject.composeapp.generated.resources.face_3
import kotlinproject.composeapp.generated.resources.face_4
import kotlinproject.composeapp.generated.resources.face_5
import kotlinproject.composeapp.generated.resources.face_6
import kotlinproject.composeapp.generated.resources.jelly_1
import kotlinproject.composeapp.generated.resources.jelly_2
import kotlinproject.composeapp.generated.resources.jelly_3
import kotlinproject.composeapp.generated.resources.jelly_4
import kotlinproject.composeapp.generated.resources.jelly_5
import kotlinproject.composeapp.generated.resources.jelly_6
import pl.nickers.match3.ecs.EntityCatalog
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

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
    var catalog by remember { mutableStateOf<EntityCatalog?>(null) }
    LaunchedEffect(Unit) {
        catalog = EntityCatalog.load()
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val loadedCatalog = catalog
            if (loadedCatalog != null) {
                val vm = remember { GameViewModel(catalog = loadedCatalog) }
                val state = vm.state

                GameGrid(
                    state = state,
                    padding = GRID_PADDING,
                    onCellClick = vm::onCellClick,
                    onDragSwap = vm::onDragSwap,
                    onSwapFinished = vm::onSwapAnimationFinished,
                    onFallFinished = vm::onFallAnimationFinished,
                    onEffectsFinished = vm::onEffectsAnimationFinished,
                )

                ScoreDisplay(
                    score = state.score,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
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
    onEffectsFinished: () -> Unit,
) {
    val grid = state.grid
    val swappingCells = state.swappingCells
    val fallingCells = state.fallingCells
    val explodingBombs = state.explodingBombs

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

                        var everExceededThreshold = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.find { it.id == pointerId } ?: break

                            if (!change.pressed) {
                                val currentDrag = dragState
                                if (currentDrag != null) {
                                    onDragSwap(currentDrag.sourcePos, currentDrag.targetPos)
                                } else if (!everExceededThreshold) {
                                    onCellClick(startPos)
                                }
                                dragState = null
                                change.consume()
                                break
                            }

                            val dx = change.position.x - startX
                            val dy = change.position.y - startY

                            if (abs(dx) > threshold || abs(dy) > threshold) {
                                everExceededThreshold = true
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
                                } else {
                                    dragState = null
                                }
                            } else {
                                dragState = null
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

            if (explodingBombs.isNotEmpty()) {
                val isFullBoard = state.fullBoardExplosion
                val explosionProgress = remember(explodingBombs) { Animatable(0f) }

                LaunchedEffect(explodingBombs) {
                    explosionProgress.snapTo(0f)
                    explosionProgress.animateTo(
                        1f,
                        tween(durationMillis = if (isFullBoard) EFFECTS_DURATION_MS * 2 else EFFECTS_DURATION_MS),
                    )
                    onEffectsFinished()
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val p = explosionProgress.value
                    if (isFullBoard) {
                        val gridFullSize = n * cellPx + (n - 1) * gapPx
                        val centerX = gridFullSize / 2f
                        val centerY = gridFullSize / 2f
                        val currentSize = p * gridFullSize
                        val alpha = 0.6f * (1f - p * 0.3f)
                        drawRect(
                            color = Color(0xFFFF2200).copy(alpha = alpha),
                            topLeft = Offset(
                                centerX - currentSize / 2,
                                centerY - currentSize / 2,
                            ),
                            size = Size(currentSize, currentSize),
                        )
                        drawRect(
                            color = Color.White.copy(alpha = alpha * 0.7f),
                            topLeft = Offset(
                                centerX - currentSize / 2,
                                centerY - currentSize / 2,
                            ),
                            size = Size(currentSize, currentSize),
                            style = Stroke(width = cellPx * 0.1f),
                        )
                    } else {
                        val singleSize = 3 * stride - gapPx
                        for (bombPos in explodingBombs) {
                            val centerX = bombPos.col * stride + cellPx / 2f
                            val centerY = bombPos.row * stride + cellPx / 2f
                            val currentSize = p * singleSize
                            val alpha = 0.55f * (1f - p * 0.4f)
                            drawRect(
                                color = Color(0xFFFF4400).copy(alpha = alpha),
                                topLeft = Offset(
                                    centerX - currentSize / 2,
                                    centerY - currentSize / 2,
                                ),
                                size = Size(currentSize, currentSize),
                            )
                            drawRect(
                                color = Color.White.copy(alpha = alpha * 0.6f),
                                topLeft = Offset(
                                    centerX - currentSize / 2,
                                    centerY - currentSize / 2,
                                ),
                                size = Size(currentSize, currentSize),
                                style = Stroke(width = cellPx * 0.06f),
                            )
                        }
                    }
                }
            }

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
private fun ScoreDisplay(score: Int, modifier: Modifier = Modifier) {
    Text(
        text = "$score",
        modifier = modifier
            .padding(16.dp)
            .background(
                Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
        maxLines = 1,
        style = TextStyle(
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.5f),
                offset = Offset(1f, 1f),
                blurRadius = 3f,
            ),
        ),
    )
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
        if (cell.isBomb) {
            BombItem(cellSize = cellSize, bodyImage = cell.bodyImage, faceImage = cell.faceImage)
        } else {
            Image(
                painter = painterResource(cell.bodyImage.toBodyDrawableRes()),
                contentDescription = "Jelly body",
                modifier = Modifier.size(cellSize),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(cell.faceImage.toFaceDrawableRes()),
                contentDescription = "Jelly face",
                modifier = Modifier.size(cellSize),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun BombItem(cellSize: Dp, bodyImage: String, faceImage: String) {
    Image(
        painter = painterResource(bodyImage.toBodyDrawableRes()),
        contentDescription = "Bomb body",
        modifier = Modifier.size(cellSize),
        contentScale = ContentScale.Fit,
    )
    Image(
        painter = painterResource(faceImage.toFaceDrawableRes()),
        contentDescription = "Bomb face",
        modifier = Modifier.size(cellSize),
        contentScale = ContentScale.Fit,
    )
}

private val bodyDrawables = mapOf(
    "jelly_1.png" to Res.drawable.jelly_1,
    "jelly_2.png" to Res.drawable.jelly_2,
    "jelly_3.png" to Res.drawable.jelly_3,
    "jelly_4.png" to Res.drawable.jelly_4,
    "jelly_5.png" to Res.drawable.jelly_5,
    "jelly_6.png" to Res.drawable.jelly_6,
    "bomb.png" to Res.drawable.bomb,
)

private val faceDrawables = mapOf(
    "face_1.png" to Res.drawable.face_1,
    "face_2.png" to Res.drawable.face_2,
    "face_3.png" to Res.drawable.face_3,
    "face_4.png" to Res.drawable.face_4,
    "face_5.png" to Res.drawable.face_5,
    "face_6.png" to Res.drawable.face_6,
    "bomb_face_1.png" to Res.drawable.bomb_face_1,
    "bomb_face_2.png" to Res.drawable.bomb_face_2,
)

private fun String.toBodyDrawableRes(): DrawableResource =
    bodyDrawables[this] ?: Res.drawable.jelly_1

private fun String.toFaceDrawableRes(): DrawableResource =
    faceDrawables[this] ?: Res.drawable.face_1
