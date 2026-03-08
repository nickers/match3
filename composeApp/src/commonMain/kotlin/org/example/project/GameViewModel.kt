package org.example.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.example.project.ecs.*
import kotlin.math.abs
import kotlin.random.Random

const val GRID_SIZE = 7
const val SWAP_DURATION_MS = 900

class GameViewModel(
    private val initialGrid: List<List<Int>>? = null,
) {

    private val world = World {
        with(MatchSystem())
        with(GravitySystem())
    }

    private val posMapper = world.mapper<GridPositionComponent>()
    private val typeMapper = world.mapper<JellyTypeComponent>()
    private val selectedMapper = world.mapper<SelectedComponent>()
    private val swappingMapper = world.mapper<SwappingComponent>()
    private val fallingMapper = world.mapper<FallingComponent>()

    private val matchSystem = world.getSystem<MatchSystem>()
    private val gravitySystem = world.getSystem<GravitySystem>()

    var state by mutableStateOf(GameState(grid = emptyList()))
        private set

    var isAnimating by mutableStateOf(false)
        private set

    private var score = 0

    init {
        initializeGrid()
        state = buildSnapshot()
    }

    private fun initializeGrid() {
        val seededGrid = initialGrid
        require(seededGrid == null || seededGrid.size == GRID_SIZE) {
            "Initial grid must be $GRID_SIZE x $GRID_SIZE"
        }

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
    }

    fun onCellClick(pos: GridPos) {
        if (isAnimating) return

        val clickedEntity = findEntityAt(pos.row, pos.col) ?: return
        val selectedEntity = findSelectedEntity()

        when {
            selectedEntity == null -> {
                selectedMapper.set(clickedEntity, SelectedComponent())
            }
            selectedEntity == clickedEntity -> {
                selectedMapper.remove(clickedEntity)
            }
            isAdjacentEntities(selectedEntity, clickedEntity) -> {
                selectedMapper.remove(selectedEntity)
                triggerSwap(selectedEntity, clickedEntity)
            }
            else -> {
                selectedMapper.remove(selectedEntity)
                selectedMapper.set(clickedEntity, SelectedComponent())
            }
        }

        state = buildSnapshot()
    }

    private fun triggerSwap(entityA: Int, entityB: Int) {
        isAnimating = true
        val posA = posMapper[entityA]!!
        val posB = posMapper[entityB]!!

        swappingMapper.set(entityA, SwappingComponent(
            sourceRow = posA.row, sourceCol = posA.col,
            targetRow = posB.row, targetCol = posB.col,
        ))
        swappingMapper.set(entityB, SwappingComponent(
            sourceRow = posB.row, sourceCol = posB.col,
            targetRow = posA.row, targetCol = posA.col,
        ))

        state = buildSnapshot()
    }

    fun onSwapAnimationFinished() {
        val swappingEntities = world.getEntitiesForAspect(Aspect.all(SwappingComponent::class))
        if (swappingEntities.size != 2) return

        val eA = swappingEntities[0]
        val eB = swappingEntities[1]
        val swapA = swappingMapper[eA]!!
        val swapB = swappingMapper[eB]!!

        posMapper[eA]!!.setTo(swapA.targetPosition)
        posMapper[eB]!!.setTo(swapB.targetPosition)

        if (swapA.isReturning && swapB.isReturning) {
            swappingMapper.remove(eA)
            swappingMapper.remove(eB)
            state = buildSnapshot()
            isAnimating = false
            return
        }

        val matches = matchSystem.findMatches(GRID_SIZE)
        if (matches.isEmpty()) {
            swappingMapper.set(eA, SwappingComponent(
                sourceRow = swapA.targetRow,
                sourceCol = swapA.targetCol,
                targetRow = swapA.sourceRow,
                targetCol = swapA.sourceCol,
                isReturning = true,
            ))
            swappingMapper.set(eB, SwappingComponent(
                sourceRow = swapB.targetRow,
                sourceCol = swapB.targetCol,
                targetRow = swapB.sourceRow,
                targetCol = swapB.sourceCol,
                isReturning = true,
            ))

            state = buildSnapshot()
            return
        }

        swappingMapper.remove(eA)
        swappingMapper.remove(eB)
        processMatches()
    }

    private fun processMatches() {
        var matches = matchSystem.findMatches(GRID_SIZE)
        while (matches.isNotEmpty()) {
            val result = gravitySystem.applyGravity(matches, GRID_SIZE)
            score += result.scoreGained

            if (result.hasFallingCells) {
                state = buildSnapshot()
                return
            }
            matches = matchSystem.findMatches(GRID_SIZE)
        }

        state = buildSnapshot()
        isAnimating = false
    }

    fun onFallAnimationFinished() {
        val aspect = Aspect.all(FallingComponent::class)
        if (world.getEntitiesForAspect(aspect).isEmpty()) {
            isAnimating = false
            return
        }

        gravitySystem.clearFallingComponents()

        val matches = matchSystem.findMatches(GRID_SIZE)
        if (matches.isNotEmpty()) {
            processMatches()
        } else {
            state = buildSnapshot()
            isAnimating = false
        }
    }

    // ---- ECS queries ----

    private fun findEntityAt(row: Int, col: Int): Int? {
        val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
        return world.getEntitiesForAspect(aspect).firstOrNull {
            val pos = posMapper[it]!!
            pos.row == row && pos.col == col
        }
    }

    private fun findSelectedEntity(): Int? {
        return world.getEntitiesForAspect(Aspect.all(SelectedComponent::class)).firstOrNull()
    }

    private fun isAdjacentEntities(entityA: Int, entityB: Int): Boolean {
        val posA = posMapper[entityA]!!
        val posB = posMapper[entityB]!!
        val dr = abs(posA.row - posB.row)
        val dc = abs(posA.col - posB.col)
        return (dr == 1 && dc == 0) || (dr == 0 && dc == 1)
    }

    /**
     * Projects the current ECS world into an immutable [GameState] snapshot
     * that the Compose UI layer can render.
     */
    private fun buildSnapshot(): GameState {
        val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
        val entities = world.getEntitiesForAspect(aspect)

        val grid = Array(GRID_SIZE) { arrayOfNulls<JellyCell>(GRID_SIZE) }
        entities.forEach { entityId ->
            val pos = posMapper[entityId]!!
            val type = typeMapper[entityId]!!.type
            if (pos.row in 0 until GRID_SIZE && pos.col in 0 until GRID_SIZE) {
                grid[pos.row][pos.col] = JellyCell(type = type, id = entityId)
            }
        }

        val gridList = grid.map { row ->
            row.map { it ?: JellyCell(type = 1, id = -1) }
        }

        val selectedEntity = findSelectedEntity()
        val selected = selectedEntity?.let { posMapper[it] }?.let { GridPos(it.row, it.col) }

        val swappingCells = buildMap {
            world.getEntitiesForAspect(Aspect.all(SwappingComponent::class)).forEach { entityId ->
                val swapping = swappingMapper[entityId]!!
                put(
                    entityId,
                    SwapAnimation(
                        from = GridPos(swapping.sourceRow, swapping.sourceCol),
                        to = GridPos(swapping.targetRow, swapping.targetCol),
                        isReturning = swapping.isReturning,
                    ),
                )
            }
        }

        val fallingEntities = world.getEntitiesForAspect(Aspect.all(FallingComponent::class))
        val fallingCells = mutableMapOf<Int, Pair<Int, Int>>()
        fallingEntities.forEach { entityId ->
            val falling = fallingMapper[entityId]!!
            fallingCells[entityId] = falling.fromRow to falling.toRow
        }

        return GameState(
            grid = gridList,
            selected = selected,
            swappingCells = swappingCells,
            score = score,
            fallingCells = fallingCells,
        )
    }
}
