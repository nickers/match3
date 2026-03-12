package org.example.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.example.project.ecs.*
import kotlin.random.Random

const val GRID_SIZE = 7
const val SWAP_DURATION_MS = 300

class GameViewModel(
    private val initialGrid: List<List<Int>>? = null,
) {

    // Systems – registered in strict processing order
    private val inputSystem = InputSystem()
    private val swapResolveSystem = SwapResolveSystem()
    private val matchGravitySystem = MatchGravitySystem()
    private val fallResolveSystem = FallResolveSystem()
    private val renderSystem = RenderSystem()

    private val world = World {
        with(inputSystem)
        with(swapResolveSystem)
        with(matchGravitySystem)
        with(fallResolveSystem)
        with(renderSystem)
    }

    private val posMapper = world.mapper<GridPositionComponent>()
    private val typeMapper = world.mapper<JellyTypeComponent>()

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

    /**
     * Returns `true` when at least one swap of adjacent gems produces a match.
     * Only right and down swaps are tested — every pair is covered exactly once.
     */
    fun hasValidMove(): Boolean {
        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE) {
                val eA = findEntityAt(row, col) ?: continue
                if (col + 1 < GRID_SIZE) {
                    val eB = findEntityAt(row, col + 1) ?: continue
                    if (swapProducesMatch(eA, eB)) return true
                }
                if (row + 1 < GRID_SIZE) {
                    val eB = findEntityAt(row + 1, col) ?: continue
                    if (swapProducesMatch(eA, eB)) return true
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
                    val entity = world.createEntity()
                    world.addComponent(entity, GridPositionComponent(row, col))
                    val type = seededGrid?.get(row)?.also {
                        require(it.size == GRID_SIZE) { "Initial grid must be $GRID_SIZE x $GRID_SIZE" }
                    }?.get(col) ?: Random.nextInt(1, 7)
                    world.addComponent(entity, JellyTypeComponent(type))
                }
            }
            if (seededGrid != null) return
            eliminateInitialMatches()
        } while (!hasValidMove())
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
            val neighborTypes = neighborTypes(pos.row, pos.col)
            val candidates = (1..6).filter { it !in neighborTypes }
            val newType = candidates.random()
            typeMapper.set(entityId, JellyTypeComponent(newType))
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

    private fun neighborTypes(row: Int, col: Int): Set<Int> {
        val types = mutableSetOf<Int>()
        for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            val nr = row + dr
            val nc = col + dc
            if (nr in 0 until GRID_SIZE && nc in 0 until GRID_SIZE) {
                findEntityAt(nr, nc)?.let { types.add(typeMapper[it]!!.type) }
            }
        }
        return types
    }

    private fun findEntityAt(row: Int, col: Int): Int? {
        val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
        return world.getEntitiesForAspect(aspect).firstOrNull {
            val pos = posMapper[it]!!
            pos.row == row && pos.col == col
        }
    }
}
