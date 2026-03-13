package org.example.project.ecs

import org.example.project.FallingCells
import org.example.project.GameState
import org.example.project.GridPos
import org.example.project.JellyCell
import org.example.project.SwapAnimation
import kotlin.math.abs
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

/**
 * Convenience accessor for the singleton [BoardStateComponent] in a [World].
 */
fun World.boardState(): BoardStateComponent? {
    val entities = getEntitiesForAspect(Aspect.all(BoardStateComponent::class))
    return entities.firstOrNull()?.let { getComponent(it, BoardStateComponent::class) }
}

/**
 * Pure function: scans the grid for horizontal and vertical runs of 3+
 * identical gem types and returns the entity IDs involved.
 */
fun findMatchesOnGrid(world: World, gridSize: Int): Set<Int> {
    val posMapper = world.mapper<GridPositionComponent>()
    val typeMapper = world.mapper<JellyTypeComponent>()
    val bombMapper = world.mapper<BombComponent>()

    val grid = Array(gridSize) { IntArray(gridSize) { -1 } }
    val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
    world.getEntitiesForAspect(aspect).forEach { entityId ->
        if (bombMapper.has(entityId)) return@forEach
        val pos = posMapper[entityId]!!
        if (pos.row in 0 until gridSize && pos.col in 0 until gridSize) {
            grid[pos.row][pos.col] = entityId
        }
    }

    val matched = mutableSetOf<Int>()

    for (r in 0 until gridSize) {
        var c = 0
        while (c < gridSize) {
            val eid = grid[r][c]
            if (eid == -1) { c++; continue }
            val type = typeMapper[eid]!!.type
            var len = 1
            while (c + len < gridSize) {
                val nextId = grid[r][c + len]
                if (nextId == -1 || typeMapper[nextId]!!.type != type) break
                len++
            }
            if (len >= 3) {
                for (i in 0 until len) {
                    val id = grid[r][c + i]
                    if (id != -1) matched.add(id)
                }
            }
            c += len
        }
    }

    for (col in 0 until gridSize) {
        var r = 0
        while (r < gridSize) {
            val eid = grid[r][col]
            if (eid == -1) { r++; continue }
            val type = typeMapper[eid]!!.type
            var len = 1
            while (r + len < gridSize) {
                val nextId = grid[r + len][col]
                if (nextId == -1 || typeMapper[nextId]!!.type != type) break
                len++
            }
            if (len >= 3) {
                for (i in 0 until len) {
                    val id = grid[r + i][col]
                    if (id != -1) matched.add(id)
                }
            }
            r += len
        }
    }

    return matched
}

// ---------------------------------------------------------------------------
// Systems – processed in registration order every frame via World.process()
// ---------------------------------------------------------------------------

/**
 * Handles player input: cell selection and swap initiation.
 * Active only during [GamePhase.IDLE].
 *
 * Queries entities with [GridPositionComponent] + [JellyTypeComponent] for
 * hit-testing and [SelectedComponent] for current selection state.
 */
class InputSystem : BaseSystem() {
    private sealed class Event {
        data class Click(val row: Int, val col: Int) : Event()
        data class Drag(val fromRow: Int, val fromCol: Int, val toRow: Int, val toCol: Int) : Event()
    }

    private val pendingEvents = mutableListOf<Event>()

    private lateinit var posMapper: ComponentMapper<GridPositionComponent>
    private lateinit var selectedMapper: ComponentMapper<SelectedComponent>
    private lateinit var swappingMapper: ComponentMapper<SwappingComponent>

    override fun initialize() {
        posMapper = world.mapper()
        selectedMapper = world.mapper()
        swappingMapper = world.mapper()
    }

    fun enqueueClick(row: Int, col: Int) {
        pendingEvents.add(Event.Click(row, col))
    }

    fun enqueueDragSwap(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int) {
        pendingEvents.add(Event.Drag(fromRow, fromCol, toRow, toCol))
    }

    override fun processSystem() {
        val board = world.boardState() ?: return
        if (board.phase != GamePhase.IDLE) {
            pendingEvents.clear()
            return
        }

        val events = pendingEvents.toList()
        pendingEvents.clear()

        for (event in events) {
            when (event) {
                is Event.Click -> handleCellClick(event.row, event.col, board)
                is Event.Drag -> handleDragSwap(event, board)
            }
        }
    }

    private fun handleCellClick(row: Int, col: Int, board: BoardStateComponent) {
        val clickedEntity = findEntityAt(row, col, board.gridSize) ?: return
        val selectedEntity = findSelectedEntity()

        when {
            selectedEntity == null ->
                selectedMapper.set(clickedEntity, SelectedComponent())

            selectedEntity == clickedEntity ->
                selectedMapper.remove(clickedEntity)

            isAdjacentEntities(selectedEntity, clickedEntity) -> {
                selectedMapper.remove(selectedEntity)
                triggerSwap(selectedEntity, clickedEntity, board)
            }

            else -> {
                selectedMapper.remove(selectedEntity)
                selectedMapper.set(clickedEntity, SelectedComponent())
            }
        }
    }

    private fun handleDragSwap(event: Event.Drag, board: BoardStateComponent) {
        val entityA = findEntityAt(event.fromRow, event.fromCol, board.gridSize) ?: return
        val entityB = findEntityAt(event.toRow, event.toCol, board.gridSize) ?: return
        if (!isAdjacentEntities(entityA, entityB)) return
        findSelectedEntity()?.let { selectedMapper.remove(it) }
        triggerSwap(entityA, entityB, board)
    }

    private fun triggerSwap(entityA: Int, entityB: Int, board: BoardStateComponent) {
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
        board.phase = GamePhase.ANIMATING_SWAP
    }

    fun findEntityAt(row: Int, col: Int, gridSize: Int): Int? {
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
}

/**
 * Resolves the outcome of a completed swap animation.
 * Active during [GamePhase.RESOLVE_SWAP].
 *
 * Queries entities with [SwappingComponent] to apply final positions, then
 * uses [findMatchesOnGrid] to decide: proceed to match processing, or
 * initiate a return swap.
 */
class SwapResolveSystem : BaseSystem() {
    private lateinit var posMapper: ComponentMapper<GridPositionComponent>
    private lateinit var swappingMapper: ComponentMapper<SwappingComponent>
    private lateinit var bombMapper: ComponentMapper<BombComponent>
    private lateinit var explodingMapper: ComponentMapper<ExplodingComponent>

    override fun initialize() {
        posMapper = world.mapper()
        swappingMapper = world.mapper()
        bombMapper = world.mapper()
        explodingMapper = world.mapper()
    }

    override fun processSystem() {
        val board = world.boardState() ?: return
        if (board.phase != GamePhase.RESOLVE_SWAP) return

        val swapping = world.getEntitiesForAspect(Aspect.all(SwappingComponent::class))
        if (swapping.size != 2) return

        val eA = swapping[0]
        val eB = swapping[1]
        val swapA = swappingMapper[eA]!!
        val swapB = swappingMapper[eB]!!

        posMapper[eA]!!.setTo(swapA.targetPosition)
        posMapper[eB]!!.setTo(swapB.targetPosition)

        if (swapA.isReturning && swapB.isReturning) {
            swappingMapper.remove(eA)
            swappingMapper.remove(eB)
            board.phase = GamePhase.IDLE
            return
        }

        val hasBombA = bombMapper.has(eA)
        val hasBombB = bombMapper.has(eB)

        if (hasBombA || hasBombB) {
            swappingMapper.remove(eA)
            swappingMapper.remove(eB)
            if (hasBombA) explodingMapper.set(eA, ExplodingComponent())
            if (hasBombB) explodingMapper.set(eB, ExplodingComponent())
            board.phase = GamePhase.ANIMATING_EXPLOSION
            return
        }

        val matches = findMatchesOnGrid(world, board.gridSize)

        if (matches.isEmpty()) {
            swappingMapper.set(eA, SwappingComponent(
                sourceRow = swapA.targetRow, sourceCol = swapA.targetCol,
                targetRow = swapA.sourceRow, targetCol = swapA.sourceCol,
                isReturning = true,
            ))
            swappingMapper.set(eB, SwappingComponent(
                sourceRow = swapB.targetRow, sourceCol = swapB.targetCol,
                targetRow = swapB.sourceRow, targetCol = swapB.sourceCol,
                isReturning = true,
            ))
            board.phase = GamePhase.ANIMATING_SWAP
        } else {
            swappingMapper.remove(eA)
            swappingMapper.remove(eB)
            board.phase = GamePhase.PROCESSING_MATCHES
        }
    }
}

/**
 * Resolves a bomb explosion after its animation finishes.
 * Active during [GamePhase.RESOLVE_EXPLOSION].
 *
 * Removes all entities in the 3×3 area centred on each exploding bomb,
 * awards score, applies gravity (compacting columns and spawning new gems),
 * then transitions to [GamePhase.ANIMATING_FALL] or checks for matches.
 */
class BombExplodeSystem(
    private val random: Random = Random.Default,
) : BaseSystem() {
    private lateinit var posMapper: ComponentMapper<GridPositionComponent>
    private lateinit var typeMapper: ComponentMapper<JellyTypeComponent>
    private lateinit var explodingMapper: ComponentMapper<ExplodingComponent>
    private lateinit var fallingMapper: ComponentMapper<FallingComponent>
    private lateinit var bombMapper: ComponentMapper<BombComponent>

    override fun initialize() {
        posMapper = world.mapper()
        typeMapper = world.mapper()
        explodingMapper = world.mapper()
        fallingMapper = world.mapper()
        bombMapper = world.mapper()
    }

    override fun processSystem() {
        val board = world.boardState() ?: return
        if (board.phase != GamePhase.RESOLVE_EXPLOSION) return

        val exploding = world.getEntitiesForAspect(Aspect.all(ExplodingComponent::class))
        if (exploding.isEmpty()) {
            board.phase = GamePhase.IDLE
            return
        }

        val entitiesToRemove = mutableSetOf<Int>()
        for (bombEntity in exploding) {
            val pos = posMapper[bombEntity] ?: continue
            explodingMapper.remove(bombEntity)
            for (dr in -1..1) {
                for (dc in -1..1) {
                    val r = pos.row + dr
                    val c = pos.col + dc
                    if (r in 0 until board.gridSize && c in 0 until board.gridSize) {
                        val entity = findEntityAt(r, c, board.gridSize)
                        if (entity != null) entitiesToRemove.add(entity)
                    }
                }
            }
        }

        if (entitiesToRemove.isEmpty()) {
            board.phase = GamePhase.IDLE
            return
        }

        entitiesToRemove.forEach { world.deleteEntity(it) }
        board.score += entitiesToRemove.size * 10

        val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
        var hasFalling = false

        for (col in 0 until board.gridSize) {
            val surviving = world.getEntitiesForAspect(aspect)
                .filter { posMapper[it]!!.col == col }
                .sortedBy { posMapper[it]!!.row }

            val numNew = board.gridSize - surviving.size

            surviving.forEachIndexed { idx, entityId ->
                val pos = posMapper[entityId]!!
                val oldRow = pos.row
                val newRow = numNew + idx
                pos.row = newRow
                pos.col = col
                if (oldRow != newRow) {
                    fallingMapper.set(entityId, FallingComponent(fromRow = oldRow, toRow = newRow))
                    hasFalling = true
                }
            }

            for (i in 0 until numNew) {
                val entityId = world.createEntity()
                world.addComponent(entityId, GridPositionComponent(row = i, col = col))
                if (random.nextInt(30) == 0) {
                    world.addComponent(entityId, JellyTypeComponent(type = 0))
                    world.addComponent(entityId, BombComponent())
                } else {
                    world.addComponent(entityId, JellyTypeComponent(type = random.nextInt(1, 7)))
                }
                fallingMapper.set(entityId, FallingComponent(fromRow = -(numNew - i), toRow = i))
                hasFalling = true
            }
        }

        if (hasFalling) {
            board.phase = GamePhase.ANIMATING_FALL
        } else {
            val matches = findMatchesOnGrid(world, board.gridSize)
            board.phase = if (matches.isNotEmpty()) GamePhase.PROCESSING_MATCHES else GamePhase.IDLE
        }
    }

    private fun findEntityAt(row: Int, col: Int, gridSize: Int): Int? {
        val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
        return world.getEntitiesForAspect(aspect).firstOrNull {
            val pos = posMapper[it]!!
            pos.row == row && pos.col == col
        }
    }
}

/**
 * Detects matches and applies gravity in a cascade loop.
 * Active during [GamePhase.PROCESSING_MATCHES].
 *
 * Queries entities with [GridPositionComponent] + [JellyTypeComponent] for
 * match detection and gravity fill. Loops internally until either falling
 * cells need animating or no further matches exist.
 */
class MatchGravitySystem(
    private val random: Random = Random.Default,
) : BaseSystem() {
    private lateinit var posMapper: ComponentMapper<GridPositionComponent>
    private lateinit var typeMapper: ComponentMapper<JellyTypeComponent>
    private lateinit var fallingMapper: ComponentMapper<FallingComponent>
    private lateinit var bombMapper: ComponentMapper<BombComponent>

    override fun initialize() {
        posMapper = world.mapper()
        typeMapper = world.mapper()
        fallingMapper = world.mapper()
        bombMapper = world.mapper()
    }

    override fun processSystem() {
        val board = world.boardState() ?: return
        if (board.phase != GamePhase.PROCESSING_MATCHES) return

        var matches = findMatchesOnGrid(world, board.gridSize)
        while (matches.isNotEmpty()) {
            val result = applyGravity(matches, board.gridSize)
            board.score += result.scoreGained

            if (result.hasFallingCells) {
                board.phase = GamePhase.ANIMATING_FALL
                return
            }
            matches = findMatchesOnGrid(world, board.gridSize)
        }

        board.phase = GamePhase.IDLE
    }

    private fun applyGravity(matches: Set<Int>, gridSize: Int): GravityResult {
        matches.forEach { world.deleteEntity(it) }

        val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
        var hasFalling = false

        for (col in 0 until gridSize) {
            val surviving = world.getEntitiesForAspect(aspect)
                .filter { posMapper[it]!!.col == col }
                .sortedBy { posMapper[it]!!.row }

            val numNew = gridSize - surviving.size

            surviving.forEachIndexed { idx, entityId ->
                val pos = posMapper[entityId]!!
                val oldRow = pos.row
                val newRow = numNew + idx
                pos.row = newRow
                pos.col = col
                if (oldRow != newRow) {
                    fallingMapper.set(entityId, FallingComponent(fromRow = oldRow, toRow = newRow))
                    hasFalling = true
                }
            }

            for (i in 0 until numNew) {
                val entityId = world.createEntity()
                world.addComponent(entityId, GridPositionComponent(row = i, col = col))
                if (random.nextInt(30) == 0) {
                    world.addComponent(entityId, JellyTypeComponent(type = 0))
                    world.addComponent(entityId, BombComponent())
                } else {
                    world.addComponent(entityId, JellyTypeComponent(type = random.nextInt(1, 7)))
                }
                fallingMapper.set(entityId, FallingComponent(fromRow = -(numNew - i), toRow = i))
                hasFalling = true
            }
        }

        return GravityResult(
            scoreGained = matches.size * 10,
            hasFallingCells = hasFalling,
        )
    }

    data class GravityResult(
        val scoreGained: Int,
        val hasFallingCells: Boolean,
    )
}

/**
 * Clears [FallingComponent] after a fall animation and checks for cascading
 * matches. Active during [GamePhase.RESOLVE_FALL].
 *
 * Queries entities with [FallingComponent] and removes the component from
 * each, then delegates to [findMatchesOnGrid] to decide the next phase.
 */
class FallResolveSystem : BaseSystem() {
    private lateinit var fallingMapper: ComponentMapper<FallingComponent>

    override fun initialize() {
        fallingMapper = world.mapper()
    }

    override fun processSystem() {
        val board = world.boardState() ?: return
        if (board.phase != GamePhase.RESOLVE_FALL) return

        val fallingEntities = world.getEntitiesForAspect(Aspect.all(FallingComponent::class))
        if (fallingEntities.isEmpty()) {
            board.phase = GamePhase.IDLE
            return
        }

        fallingEntities.forEach { fallingMapper.remove(it) }

        val matches = findMatchesOnGrid(world, board.gridSize)
        board.phase = if (matches.isNotEmpty()) GamePhase.PROCESSING_MATCHES else GamePhase.IDLE
    }
}

/**
 * Rendering system: projects the current ECS world into an immutable
 * [GameState] snapshot that the Compose UI layer can render.
 * Runs every frame regardless of phase.
 *
 * Queries entities with [GridPositionComponent] + [JellyTypeComponent] for
 * the grid, [SelectedComponent] for selection highlight, [SwappingComponent]
 * for swap animations, and [FallingComponent] for fall animations.
 */
class RenderSystem : BaseSystem() {
    private lateinit var posMapper: ComponentMapper<GridPositionComponent>
    private lateinit var typeMapper: ComponentMapper<JellyTypeComponent>
    private lateinit var swappingMapper: ComponentMapper<SwappingComponent>
    private lateinit var fallingMapper: ComponentMapper<FallingComponent>
    private lateinit var bombMapper: ComponentMapper<BombComponent>
    private lateinit var explodingMapper: ComponentMapper<ExplodingComponent>

    var gameState: GameState = GameState(grid = emptyList())
        private set

    override fun initialize() {
        posMapper = world.mapper()
        typeMapper = world.mapper()
        swappingMapper = world.mapper()
        fallingMapper = world.mapper()
        bombMapper = world.mapper()
        explodingMapper = world.mapper()
    }

    override fun processSystem() {
        val board = world.boardState() ?: return
        val gridSize = board.gridSize

        val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
        val entities = world.getEntitiesForAspect(aspect)

        val grid = Array(gridSize) { arrayOfNulls<JellyCell>(gridSize) }
        entities.forEach { entityId ->
            val pos = posMapper[entityId]!!
            val type = typeMapper[entityId]!!.type
            val isBomb = bombMapper.has(entityId)
            if (pos.row in 0 until gridSize && pos.col in 0 until gridSize) {
                grid[pos.row][pos.col] = JellyCell(type = type, id = entityId, isBomb = isBomb)
            }
        }

        val gridList = grid.map { row ->
            row.map { it ?: JellyCell(type = 1, id = -1) }
        }

        val selectedEntity = world.getEntitiesForAspect(
            Aspect.all(SelectedComponent::class),
        ).firstOrNull()
        val selected = selectedEntity
            ?.let { posMapper[it] }
            ?.let { GridPos(it.row, it.col) }

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
        val fallingCells: FallingCells = buildMap {
            fallingEntities.forEach { entityId ->
                val falling = fallingMapper[entityId]!!
                put(entityId, falling.fromRow to falling.toRow)
            }
        }

        val explodingEntities = world.getEntitiesForAspect(Aspect.all(ExplodingComponent::class))
        val explodingBombs = explodingEntities.mapNotNull { entityId ->
            posMapper[entityId]?.let { GridPos(it.row, it.col) }
        }

        gameState = GameState(
            grid = gridList,
            selected = selected,
            swappingCells = swappingCells,
            score = board.score,
            fallingCells = fallingCells,
            explodingBombs = explodingBombs,
        )
    }
}
