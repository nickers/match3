package pl.nickers.match3.ecs

import pl.nickers.match3.FallingCells
import pl.nickers.match3.GameState
import pl.nickers.match3.GridPos
import pl.nickers.match3.JellyCell
import pl.nickers.match3.SwapAnimation
import kotlin.math.abs
import kotlin.random.Random

fun World.boardState(): BoardStateComponent? {
    val entities = getEntitiesForAspect(Aspect.all(BoardStateComponent::class))
    return entities.firstOrNull()?.let { getComponent(it, BoardStateComponent::class) }
}

fun World.findEntityAt(row: Int, col: Int): Int? {
    val posMapper = mapper<GridPositionComponent>()
    val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
    return getEntitiesForAspect(aspect).firstOrNull {
        val pos = posMapper[it]!!
        pos.row == row && pos.col == col
    }
}

fun findMatchesOnGrid(world: World, gridSize: Int): Set<Int> {
    val posMapper = world.mapper<GridPositionComponent>()
    val typeMapper = world.mapper<JellyTypeComponent>()
    val bombMapper = world.mapper<BombComponent>()
    val iceCubeMapper = world.mapper<IceCubeComponent>()

    val grid = Array(gridSize) { IntArray(gridSize) { -1 } }
    val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
    world.getEntitiesForAspect(aspect).forEach { entityId ->
        if (bombMapper.has(entityId) || iceCubeMapper.has(entityId)) return@forEach
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

fun applyIceCubeDamage(
    world: World,
    random: Random,
    catalog: EntityCatalog,
) {
    val posMapper = world.mapper<GridPositionComponent>()
    val iceCubeMapper = world.mapper<IceCubeComponent>()
    val matchNeighbourMapper = world.mapper<MatchNeighbourComponent>()
    val aspect = Aspect.all(IceCubeComponent::class, MatchNeighbourComponent::class)
    val toProcess = world.getEntitiesForAspect(aspect).toList()
    for (entityId in toProcess) {
        val ice = iceCubeMapper[entityId] ?: continue
        matchNeighbourMapper.remove(entityId)
        ice.life -= 1
        if (ice.life <= 0) {
            val pos = posMapper[entityId]!!
            world.deleteEntity(entityId)
            world.createRandomBoardEntity(row = pos.row, col = pos.col, random = random, catalog = catalog)
        }
    }
}

fun stampMatchNeighbours(world: World, matches: Set<Int>, gridSize: Int) {
    val posMapper = world.mapper<GridPositionComponent>()
    val matchNeighbourMapper = world.mapper<MatchNeighbourComponent>()
    val aspect = Aspect.all(GridPositionComponent::class)
    for (eid in matches) {
        val pos = posMapper[eid] ?: continue
        val r = pos.row
        val c = pos.col
        for ((dr, dc) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            val nr = r + dr
            val nc = c + dc
            if (nr in 0 until gridSize && nc in 0 until gridSize && (nr to nc) != (r to c)) {
                val neighbour = world.getEntitiesForAspect(aspect).firstOrNull {
                    posMapper[it]!!.row == nr && posMapper[it]!!.col == nc
                } ?: continue
                if (neighbour !in matches) matchNeighbourMapper.set(neighbour, MatchNeighbourComponent())
            }
        }
    }
}

fun applyGravityToGrid(
    world: World,
    entitiesToRemove: Set<Int>,
    gridSize: Int,
    random: Random = Random.Default,
    catalog: EntityCatalog = EntityCatalog.default(),
) {
    entitiesToRemove.forEach { world.deleteEntity(it) }

    val posMapper = world.mapper<GridPositionComponent>()
    val fallingMapper = world.mapper<FallingComponent>()
    val iceCubeMapper = world.mapper<IceCubeComponent>()
    val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)

    for (col in 0 until gridSize) {
        val iceRows = world.getEntitiesForAspect(Aspect.all(IceCubeComponent::class, GridPositionComponent::class))
            .filter { posMapper[it]!!.col == col }
            .map { posMapper[it]!!.row }
            .sorted()

        val segments = if (iceRows.isEmpty()) listOf(0 to gridSize)
        else listOf(0 to iceRows[0]) + iceRows.mapIndexed { i, _ ->
            (iceRows[i] + 1) to (if (i + 1 < iceRows.size) iceRows[i + 1] else gridSize)
        }

        for ((start, end) in segments) {
            if (start >= end) continue
            val inSegment = world.getEntitiesForAspect(aspect)
                .filter { posMapper[it]!!.col == col && posMapper[it]!!.row in start until end && !iceCubeMapper.has(it) }
                .sortedBy { posMapper[it]!!.row }
            val count = inSegment.size
            inSegment.forEachIndexed { idx, entityId ->
                val pos = posMapper[entityId]!!
                val oldRow = pos.row
                val newRow = end - count + idx
                pos.row = newRow
                pos.col = col
                if (oldRow != newRow) {
                    fallingMapper.set(entityId, FallingComponent(fromRow = oldRow, toRow = newRow))
                }
            }
            if (start == 0) {
                val numNew = end - count
                for (i in 0 until numNew) {
                    val entityId = world.createRandomBoardEntity(row = i, col = col, random = random, catalog = catalog)
                    fallingMapper.set(entityId, FallingComponent(fromRow = -(numNew - i), toRow = i))
                }
            }
        }
    }
}

class InputSystem : BaseSystem() {
    private sealed class Event {
        data class Click(val row: Int, val col: Int) : Event()
        data class Drag(val fromRow: Int, val fromCol: Int, val toRow: Int, val toCol: Int) : Event()
    }

    private val pendingEvents = mutableListOf<Event>()
    private lateinit var posMapper: ComponentMapper<GridPositionComponent>
    private lateinit var selectedMapper: ComponentMapper<SelectedComponent>
    private lateinit var swappingMapper: ComponentMapper<SwappingComponent>
    private lateinit var iceCubeMapper: ComponentMapper<IceCubeComponent>

    override fun initialize() {
        posMapper = world.mapper()
        selectedMapper = world.mapper()
        swappingMapper = world.mapper()
        iceCubeMapper = world.mapper()
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
        val clickedEntity = world.findEntityAt(row, col) ?: return
        if (iceCubeMapper.has(clickedEntity)) return
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
        val entityA = world.findEntityAt(event.fromRow, event.fromCol) ?: return
        val entityB = world.findEntityAt(event.toRow, event.toCol) ?: return
        if (iceCubeMapper.has(entityA) || iceCubeMapper.has(entityB)) return
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

    private fun findSelectedEntity(): Int? =
        world.getEntitiesForAspect(Aspect.all(SelectedComponent::class)).firstOrNull()

    private fun isAdjacentEntities(entityA: Int, entityB: Int): Boolean {
        val posA = posMapper[entityA]!!
        val posB = posMapper[entityB]!!
        val dr = abs(posA.row - posB.row)
        val dc = abs(posA.col - posB.col)
        return (dr == 1 && dc == 0) || (dr == 0 && dc == 1)
    }
}

class SwapResolveSystem : BaseSystem() {
    private lateinit var posMapper: ComponentMapper<GridPositionComponent>
    private lateinit var swappingMapper: ComponentMapper<SwappingComponent>
    private lateinit var bombMapper: ComponentMapper<BombComponent>
    private lateinit var explodingMapper: ComponentMapper<ExplodingComponent>
    private lateinit var fullBoardMapper: ComponentMapper<FullBoardExplosionComponent>
    private lateinit var pendingSwapMapper: ComponentMapper<PendingSwapValidationComponent>

    override fun initialize() {
        posMapper = world.mapper()
        swappingMapper = world.mapper()
        bombMapper = world.mapper()
        explodingMapper = world.mapper()
        fullBoardMapper = world.mapper()
        pendingSwapMapper = world.mapper()
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
        swappingMapper.remove(eA)
        swappingMapper.remove(eB)
        if (!swapA.isReturning) {
            val hasBombA = bombMapper.has(eA)
            val hasBombB = bombMapper.has(eB)
            if (hasBombA || hasBombB) {
                if (hasBombA) explodingMapper.set(eA, ExplodingComponent())
                if (hasBombB) explodingMapper.set(eB, ExplodingComponent())
                if (hasBombA && hasBombB) fullBoardMapper.set(eA, FullBoardExplosionComponent())
            } else {
                pendingSwapMapper.set(eA, PendingSwapValidationComponent(otherEntity = eB))
            }
        }
        board.phase = GamePhase.PROCESSING
    }
}

class FallResolveSystem : BaseSystem() {
    private lateinit var fallingMapper: ComponentMapper<FallingComponent>

    override fun initialize() {
        fallingMapper = world.mapper()
    }

    override fun processSystem() {
        val board = world.boardState() ?: return
        if (board.phase != GamePhase.RESOLVE_FALL) return
        world.getEntitiesForAspect(Aspect.all(FallingComponent::class))
            .forEach { fallingMapper.remove(it) }
        board.phase = GamePhase.PROCESSING
    }
}

class EffectResolveSystem(
    private val random: Random = Random.Default,
    private val catalog: EntityCatalog = EntityCatalog.default(),
) : BaseSystem() {
    private lateinit var posMapper: ComponentMapper<GridPositionComponent>
    private lateinit var explodingMapper: ComponentMapper<ExplodingComponent>
    private lateinit var fullBoardMapper: ComponentMapper<FullBoardExplosionComponent>

    override fun initialize() {
        posMapper = world.mapper()
        explodingMapper = world.mapper()
        fullBoardMapper = world.mapper()
    }

    override fun processSystem() {
        val board = world.boardState() ?: return
        if (board.phase != GamePhase.RESOLVE_EFFECTS) return
        val exploding = world.getEntitiesForAspect(Aspect.all(ExplodingComponent::class))
        if (exploding.isEmpty()) {
            board.phase = GamePhase.PROCESSING
            return
        }
        val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
        val isFullBoard = exploding.any { fullBoardMapper.has(it) }
        val entitiesToRemove: Set<Int>
        if (isFullBoard) {
            exploding.forEach {
                explodingMapper.remove(it)
                fullBoardMapper.remove(it)
            }
            entitiesToRemove = world.getEntitiesForAspect(aspect).toSet()
        } else {
            val collected = mutableSetOf<Int>()
            for (bombEntity in exploding) {
                val pos = posMapper[bombEntity] ?: continue
                explodingMapper.remove(bombEntity)
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        val r = pos.row + dr
                        val c = pos.col + dc
                        if (r in 0 until board.gridSize && c in 0 until board.gridSize) {
                            world.findEntityAt(r, c)?.let { collected.add(it) }
                        }
                    }
                }
            }
            entitiesToRemove = collected
        }
        if (entitiesToRemove.isNotEmpty()) {
            board.score += entitiesToRemove.size * 10
            applyGravityToGrid(world, entitiesToRemove, board.gridSize, random, catalog)
        }
        board.phase = GamePhase.PROCESSING
    }
}

class MatchNeighbourCleanupSystem : BaseSystem() {
    private lateinit var matchNeighbourMapper: ComponentMapper<MatchNeighbourComponent>

    override fun initialize() {
        matchNeighbourMapper = world.mapper()
    }

    override fun processSystem() {
        val board = world.boardState() ?: return
        if (board.phase != GamePhase.IDLE) return
        world.getEntitiesForAspect(Aspect.all(MatchNeighbourComponent::class))
            .toList()
            .forEach { matchNeighbourMapper.remove(it) }
    }
}

class GameLoopSystem(
    private val random: Random = Random.Default,
    private val catalog: EntityCatalog = EntityCatalog.default(),
) : BaseSystem() {
    private lateinit var posMapper: ComponentMapper<GridPositionComponent>
    private lateinit var swappingMapper: ComponentMapper<SwappingComponent>
    private lateinit var pendingSwapMapper: ComponentMapper<PendingSwapValidationComponent>

    override fun initialize() {
        posMapper = world.mapper()
        swappingMapper = world.mapper()
        pendingSwapMapper = world.mapper()
    }

    override fun processSystem() {
        val board = world.boardState() ?: return
        if (board.phase != GamePhase.PROCESSING) return
        var iterations = 0
        while (iterations++ < 100) {
            if (world.getEntitiesForAspect(Aspect.all(SwappingComponent::class)).isNotEmpty()) {
                board.phase = GamePhase.ANIMATING_SWAP
                return
            }
            if (world.getEntitiesForAspect(Aspect.all(FallingComponent::class)).isNotEmpty()) {
                board.phase = GamePhase.ANIMATING_FALL
                return
            }
            if (world.getEntitiesForAspect(Aspect.all(ExplodingComponent::class)).isNotEmpty()) {
                board.phase = GamePhase.ANIMATING_EFFECTS
                return
            }
            val matches = findMatchesOnGrid(world, board.gridSize)
            if (matches.isNotEmpty()) {
                board.score += matches.size * 10
                stampMatchNeighbours(world, matches, board.gridSize)
                applyIceCubeDamage(world, random, catalog)
                applyGravityToGrid(world, matches, board.gridSize, random, catalog)
                clearPendingSwapValidations()
                continue
            }
            val pending = world.getEntitiesForAspect(Aspect.all(PendingSwapValidationComponent::class))
            if (pending.isNotEmpty()) {
                val holder = pending.first()
                val comp = pendingSwapMapper[holder]!!
                val eA = holder
                val eB = comp.otherEntity
                pendingSwapMapper.remove(holder)
                if (world.entityExists(eA) && world.entityExists(eB)) {
                    val posA = posMapper[eA]!!
                    val posB = posMapper[eB]!!
                    swappingMapper.set(eA, SwappingComponent(
                        sourceRow = posA.row, sourceCol = posA.col,
                        targetRow = posB.row, targetCol = posB.col,
                        isReturning = true,
                    ))
                    swappingMapper.set(eB, SwappingComponent(
                        sourceRow = posB.row, sourceCol = posB.col,
                        targetRow = posA.row, targetCol = posA.col,
                        isReturning = true,
                    ))
                    continue
                }
            }
            board.phase = GamePhase.IDLE
            return
        }
        board.phase = GamePhase.IDLE
    }

    private fun clearPendingSwapValidations() {
        world.getEntitiesForAspect(Aspect.all(PendingSwapValidationComponent::class))
            .forEach { pendingSwapMapper.remove(it) }
    }
}

class RenderSystem : BaseSystem() {
    private lateinit var posMapper: ComponentMapper<GridPositionComponent>
    private lateinit var typeMapper: ComponentMapper<JellyTypeComponent>
    private lateinit var bodyImageMapper: ComponentMapper<BodyImageComponent>
    private lateinit var jellyFaceMapper: ComponentMapper<JellyFaceComponent>
    private lateinit var bombFaceMapper: ComponentMapper<BombFaceComponent>
    private lateinit var swappingMapper: ComponentMapper<SwappingComponent>
    private lateinit var fallingMapper: ComponentMapper<FallingComponent>
    private lateinit var bombMapper: ComponentMapper<BombComponent>
    private lateinit var iceCubeMapper: ComponentMapper<IceCubeComponent>
    private lateinit var explodingMapper: ComponentMapper<ExplodingComponent>
    private lateinit var fullBoardMapper: ComponentMapper<FullBoardExplosionComponent>

    var gameState: GameState = GameState(grid = emptyList())
        private set

    override fun initialize() {
        posMapper = world.mapper()
        typeMapper = world.mapper()
        bodyImageMapper = world.mapper()
        jellyFaceMapper = world.mapper()
        bombFaceMapper = world.mapper()
        swappingMapper = world.mapper()
        fallingMapper = world.mapper()
        bombMapper = world.mapper()
        iceCubeMapper = world.mapper()
        explodingMapper = world.mapper()
        fullBoardMapper = world.mapper()
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
            val isIceCube = iceCubeMapper.has(entityId)
            val iceCube = iceCubeMapper[entityId]
            val bodyImage = when {
                isIceCube && iceCube != null -> "ice_${iceCube.life}.png"
                isBomb -> bodyImageMapper[entityId]?.image ?: "bomb.png"
                else -> bodyImageMapper[entityId]?.image ?: "jelly_$type.png"
            }
            val faceImage = when {
                isIceCube -> "face_1.png"
                isBomb -> bombFaceMapper[entityId]?.image ?: "bomb_face_1.png"
                else -> jellyFaceMapper[entityId]?.image ?: "face_1.png"
            }
            val iceCubeLife = if (isIceCube && iceCube != null) iceCube.life else 3
            if (pos.row in 0 until gridSize && pos.col in 0 until gridSize) {
                grid[pos.row][pos.col] = JellyCell(
                    type = type,
                    id = entityId,
                    isBomb = isBomb,
                    isIceCube = isIceCube,
                    isEmpty = false,
                    iceCubeLife = iceCubeLife,
                    bodyImage = bodyImage,
                    faceImage = faceImage,
                )
            }
        }
        val gridList = grid.map { row ->
            row.map { it ?: JellyCell(type = 1, id = -1, isEmpty = true) }
        }
        val selectedEntity = world.getEntitiesForAspect(Aspect.all(SelectedComponent::class)).firstOrNull()
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
        val fullBoardExplosion = explodingEntities.any { fullBoardMapper.has(it) }
        gameState = GameState(
            grid = gridList,
            selected = selected,
            swappingCells = swappingCells,
            score = board.score,
            fallingCells = fallingCells,
            explodingBombs = explodingBombs,
            fullBoardExplosion = fullBoardExplosion,
        )
    }
}
