package pl.nickers.match3

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import pl.nickers.match3.ecs.*
import kotlin.random.Random

const val GRID_SIZE = 7
const val SWAP_DURATION_MS = 300
const val EFFECTS_DURATION_MS = 400

class GameViewModel(
    private val catalog: EntityCatalog = EntityCatalog.default(),
    private val initialGrid: List<List<Int>>? = null,
) {
    private val random = Random.Default

    // Systems – registered in strict processing order
    private val inputSystem = InputSystem()
    private val swapResolveSystem = SwapResolveSystem()
    private val fallResolveSystem = FallResolveSystem()
    private val effectResolveSystem = EffectResolveSystem(random = random, catalog = catalog)
    private val gameLoopSystem = GameLoopSystem(random = random, catalog = catalog)
    private val matchNeighbourCleanupSystem = MatchNeighbourCleanupSystem()
    private val renderSystem = RenderSystem()

    private val world = World {
        with(inputSystem)
        with(swapResolveSystem)
        with(fallResolveSystem)
        with(effectResolveSystem)
        with(gameLoopSystem)
        with(matchNeighbourCleanupSystem)
        with(renderSystem)
    }

    private val posMapper = world.mapper<GridPositionComponent>()
    private val typeMapper = world.mapper<JellyTypeComponent>()
    private val bodyImageMapper = world.mapper<BodyImageComponent>()
    private val bombMapper = world.mapper<BombComponent>()
    private val iceCubeMapper = world.mapper<IceCubeComponent>()

    private var boardEntity: Int = -1

    var state by mutableStateOf(GameState(grid = emptyList()))
        private set

    var isAnimating by mutableStateOf(false)
        private set

    init {
        boardEntity = world.createEntity()
        world.addComponent(boardEntity, BoardStateComponent(gridSize = GRID_SIZE))
        initializeGrid()
        processWorld()
    }

    // ---- Public API (consumed by Compose) ----

    fun onCellClick(pos: GridPos) {
        if (isAnimating) return
        inputSystem.enqueueClick(pos.row, pos.col)
        processWorld()
    }

    fun onDragSwap(from: GridPos, to: GridPos) {
        if (isAnimating) return
        inputSystem.enqueueDragSwap(from.row, from.col, to.row, to.col)
        processWorld()
    }

    fun onSwapAnimationFinished() {
        val board = boardState() ?: return
        if (board.phase != GamePhase.ANIMATING_SWAP) return
        board.phase = GamePhase.RESOLVE_SWAP
        processWorld()
    }

    fun onFallAnimationFinished() {
        val board = boardState() ?: return
        if (board.phase != GamePhase.ANIMATING_FALL) return
        board.phase = GamePhase.RESOLVE_FALL
        processWorld()
    }

    fun onEffectsAnimationFinished() {
        val board = boardState() ?: return
        if (board.phase != GamePhase.ANIMATING_EFFECTS) return
        board.phase = GamePhase.RESOLVE_EFFECTS
        processWorld()
    }

    /**
     * Returns `true` when at least one swap of adjacent gems produces a match.
     * Only right and down swaps are tested — every pair is covered exactly once.
     */
    fun hasValidMove(): Boolean {
        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE) {
                val eA = findEntityAt(row, col) ?: continue
                if (iceCubeMapper.has(eA)) continue
                val isBombA = bombMapper.has(eA)
                if (col + 1 < GRID_SIZE) {
                    val eB = findEntityAt(row, col + 1) ?: continue
                    if (iceCubeMapper.has(eB)) continue
                    if (isBombA || bombMapper.has(eB) || swapProducesMatch(eA, eB)) return true
                }
                if (row + 1 < GRID_SIZE) {
                    val eB = findEntityAt(row + 1, col) ?: continue
                    if (iceCubeMapper.has(eB)) continue
                    if (isBombA || bombMapper.has(eB) || swapProducesMatch(eA, eB)) return true
                }
            }
        }
        return false
    }

    // ---- World processing ----

    /**
     * Runs [World.process] in a loop until the phase stabilises at [GamePhase.IDLE]
     * or reaches an animation phase that must be played by Compose before the
     * next resolution step.
     */
    private fun processWorld() {
        val maxIterations = 10
        var iterations = 0
        do {
            val phaseBefore = boardState()?.phase
            world.process()
            val phaseAfter = boardState()?.phase
            iterations++
        } while (
            iterations < maxIterations &&
            phaseAfter != null &&
            phaseAfter != phaseBefore &&
            phaseAfter != GamePhase.IDLE &&
            !phaseAfter.isAnimating
        )
        syncState()
    }

    private fun syncState() {
        state = renderSystem.gameState
        isAnimating = boardState()?.phase != GamePhase.IDLE
    }

    private fun boardState(): BoardStateComponent? =
        world.getComponent(boardEntity, BoardStateComponent::class)

    // ---- Grid initialisation (runs once) ----

    private fun initializeGrid() {
        val seededGrid = initialGrid
        require(seededGrid == null || seededGrid.size == GRID_SIZE) {
            "Initial grid must be $GRID_SIZE x $GRID_SIZE"
        }

        do {
            clearGrid()
            for (row in 0 until GRID_SIZE) {
                for (col in 0 until GRID_SIZE) {
                    if (seededGrid != null) {
                        val type = seededGrid[row].also {
                            require(it.size == GRID_SIZE) { "Initial grid must be $GRID_SIZE x $GRID_SIZE" }
                        }[col]
                        if (type == 0) {
                            world.createBombEntity(row = row, col = col, random = random)
                        } else {
                            world.createJellyEntity(row = row, col = col, jellyType = type, random = random)
                        }
                    } else {
                        world.createRandomBoardEntity(row = row, col = col, random = random, catalog = catalog)
                    }
                }
            }
            if (seededGrid != null) return
            eliminateInitialMatches()
            placeIceCube()
        } while (!hasValidMove())
    }

    private fun placeIceCube() {
        val row = random.nextInt(GRID_SIZE)
        val col = random.nextInt(GRID_SIZE)
        world.findEntityAt(row, col)?.let { world.deleteEntity(it) }
        world.createIceCubeEntity(row = row, col = col)
    }

    private fun clearGrid() {
        val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
        world.getEntitiesForAspect(aspect).forEach { world.deleteEntity(it) }
    }

    private fun eliminateInitialMatches() {
        var matches = findMatchesOnGrid(world, GRID_SIZE)
        while (matches.isNotEmpty()) {
            val entityId = matches.random()
            val pos = posMapper[entityId]!!
            val neighborBodyImages = neighborBodyImages(pos.row, pos.col)
            val candidates = catalog.jellyBodyImages.filter { it !in neighborBodyImages }
            if (candidates.isNotEmpty()) {
                val newBodyImage = candidates.random()
                typeMapper.set(entityId, JellyTypeComponent(catalog.typeIdForBody(newBodyImage)))
                bodyImageMapper.set(entityId, BodyImageComponent(newBodyImage))
            }
            matches = findMatchesOnGrid(world, GRID_SIZE)
        }
    }

    private fun swapProducesMatch(eA: Int, eB: Int): Boolean {
        val typeA = typeMapper[eA]!!
        val typeB = typeMapper[eB]!!
        typeMapper.set(eA, typeB)
        typeMapper.set(eB, typeA)
        val found = findMatchesOnGrid(world, GRID_SIZE).isNotEmpty()
        typeMapper.set(eA, typeA)
        typeMapper.set(eB, typeB)
        return found
    }

    private fun neighborBodyImages(row: Int, col: Int): Set<String> {
        val images = mutableSetOf<String>()
        for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            val nr = row + dr
            val nc = col + dc
            if (nr in 0 until GRID_SIZE && nc in 0 until GRID_SIZE) {
                findEntityAt(nr, nc)?.let { bodyImageMapper[it]?.let { c -> images.add(c.image) } }
            }
        }
        return images
    }

    private fun findEntityAt(row: Int, col: Int): Int? = world.findEntityAt(row, col)
}
