package org.example.project

import org.example.project.ecs.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// ECS Core Tests
// ---------------------------------------------------------------------------

class WorldTest {

    @Test
    fun createEntity_returnsIncrementingIds() {
        val world = World()
        assertEquals(0, world.createEntity())
        assertEquals(1, world.createEntity())
        assertEquals(2, world.createEntity())
    }

    @Test
    fun deleteEntity_removesEntityAndComponents() {
        val world = World()
        val id = world.createEntity()
        world.addComponent(id, JellyTypeComponent(3))
        assertTrue(world.entityExists(id))

        world.deleteEntity(id)
        assertFalse(world.entityExists(id))
        assertNull(world.getComponent(id, JellyTypeComponent::class))
    }

    @Test
    fun addAndGetComponent() {
        val world = World()
        val id = world.createEntity()
        world.addComponent(id, GridPositionComponent(2, 3))

        val pos = world.getComponent(id, GridPositionComponent::class)
        assertNotNull(pos)
        assertEquals(2, pos.row)
        assertEquals(3, pos.col)
    }

    @Test
    fun removeComponent() {
        val world = World()
        val id = world.createEntity()
        world.addComponent(id, GridPositionComponent(0, 0))
        assertTrue(world.hasComponent(id, GridPositionComponent::class))

        world.removeComponent(id, GridPositionComponent::class)
        assertFalse(world.hasComponent(id, GridPositionComponent::class))
    }

    @Test
    fun componentMapper_setGetHasRemove() {
        val world = World()
        val mapper = world.mapper<JellyTypeComponent>()
        val id = world.createEntity()

        assertFalse(mapper.has(id))
        mapper.set(id, JellyTypeComponent(5))
        assertTrue(mapper.has(id))
        assertEquals(5, mapper[id]!!.type)

        mapper.remove(id)
        assertFalse(mapper.has(id))
    }

    @Test
    fun getEntitiesForAspect_filtersCorrectly() {
        val world = World()
        val a = world.createEntity()
        world.addComponent(a, GridPositionComponent(0, 0))
        world.addComponent(a, JellyTypeComponent(1))

        val b = world.createEntity()
        world.addComponent(b, GridPositionComponent(1, 1))

        val c = world.createEntity()
        world.addComponent(c, JellyTypeComponent(2))

        val both = world.getEntitiesForAspect(
            Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
        )
        assertEquals(listOf(a), both)

        val posOnly = world.getEntitiesForAspect(Aspect.all(GridPositionComponent::class))
        assertTrue(posOnly.containsAll(listOf(a, b)))
        assertEquals(2, posOnly.size)
    }

    @Test
    fun systemRegistration_and_process() {
        var called = false
        val sys = object : BaseSystem() {
            override fun processSystem() { called = true }
        }
        val world = World { with(sys) }
        assertFalse(called)
        world.process()
        assertTrue(called)
    }

    @Test
    fun iteratingSystem_processesMatchingEntities() {
        val processed = mutableListOf<Int>()
        val sys = object : IteratingSystem(Aspect.all(GridPositionComponent::class)) {
            override fun process(entityId: Int) { processed.add(entityId) }
        }
        val world = World { with(sys) }

        val a = world.createEntity()
        world.addComponent(a, GridPositionComponent(0, 0))
        val b = world.createEntity()
        world.addComponent(b, JellyTypeComponent(1))
        val c = world.createEntity()
        world.addComponent(c, GridPositionComponent(2, 2))

        world.process()
        assertEquals(listOf(a, c), processed.sorted())
    }

    @Test
    fun systemsProcessInRegistrationOrder() {
        val order = mutableListOf<String>()
        val sysA = object : BaseSystem() {
            override fun processSystem() { order.add("A") }
        }
        val sysB = object : BaseSystem() {
            override fun processSystem() { order.add("B") }
        }
        val sysC = object : BaseSystem() {
            override fun processSystem() { order.add("C") }
        }
        val world = World {
            with(sysA)
            with(sysB)
            with(sysC)
        }
        world.process()
        assertEquals(listOf("A", "B", "C"), order)
    }

    @Test
    fun getAllEntityIds_returnsCorrectSet() {
        val world = World()
        val a = world.createEntity()
        val b = world.createEntity()
        assertEquals(setOf(a, b), world.getAllEntityIds())

        world.deleteEntity(a)
        assertEquals(setOf(b), world.getAllEntityIds())
    }
}

// ---------------------------------------------------------------------------
// Match Detection Tests
// ---------------------------------------------------------------------------

class MatchDetectionTest {

    private fun worldWithGrid(types: List<List<Int>>): World {
        val world = World()
        val gridSize = types.size
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val e = world.createEntity()
                world.addComponent(e, GridPositionComponent(r, c))
                world.addComponent(e, JellyTypeComponent(types[r][c]))
            }
        }
        return world
    }

    @Test
    fun detectsHorizontalMatch() {
        val types = listOf(
            listOf(1, 1, 1, 2, 3),
            listOf(2, 3, 4, 5, 6),
            listOf(3, 4, 5, 6, 1),
            listOf(4, 5, 6, 1, 2),
            listOf(5, 6, 1, 2, 3),
        )
        val world = worldWithGrid(types)
        val matches = findMatchesOnGrid(world, 5)
        assertEquals(3, matches.size)
    }

    @Test
    fun detectsVerticalMatch() {
        val types = listOf(
            listOf(1, 2, 3, 4, 5),
            listOf(1, 3, 4, 5, 6),
            listOf(1, 4, 5, 6, 1),
            listOf(2, 5, 6, 1, 2),
            listOf(3, 6, 1, 2, 3),
        )
        val world = worldWithGrid(types)
        val matches = findMatchesOnGrid(world, 5)
        assertEquals(3, matches.size)
    }

    @Test
    fun detectsBothDirections() {
        val types = listOf(
            listOf(1, 1, 1),
            listOf(2, 1, 3),
            listOf(3, 1, 2),
        )
        val world = worldWithGrid(types)
        val matches = findMatchesOnGrid(world, 3)
        assertEquals(5, matches.size)
    }

    @Test
    fun noMatchesOnUniqueGrid() {
        val types = listOf(
            listOf(1, 2, 1),
            listOf(2, 1, 2),
            listOf(1, 2, 1),
        )
        val world = worldWithGrid(types)
        val matches = findMatchesOnGrid(world, 3)
        assertTrue(matches.isEmpty())
    }
}

// ---------------------------------------------------------------------------
// System Tests
// ---------------------------------------------------------------------------

class MatchGravitySystemTest {

    @Test
    fun removesMatchedEntitiesAndFillsGrid() {
        val matchGravitySystem = MatchGravitySystem()
        val world = World { with(matchGravitySystem) }

        val boardEntity = world.createEntity()
        world.addComponent(boardEntity, BoardStateComponent(
            phase = GamePhase.PROCESSING_MATCHES,
            gridSize = 3,
        ))

        val gridSize = 3
        val types = listOf(
            listOf(1, 2, 3),
            listOf(1, 3, 2),
            listOf(1, 2, 3),
        )
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val e = world.createEntity()
                world.addComponent(e, GridPositionComponent(r, c))
                world.addComponent(e, JellyTypeComponent(types[r][c]))
            }
        }

        world.process()

        val board = world.getComponent(boardEntity, BoardStateComponent::class)!!
        assertEquals(30, board.score)
        assertEquals(GamePhase.ANIMATING_FALL, board.phase)

        val posMapper = world.mapper<GridPositionComponent>()
        val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
        val remaining = world.getEntitiesForAspect(aspect)
        assertEquals(gridSize * gridSize, remaining.size)

        val occupied = remaining.map { posMapper[it]!!.let { p -> p.row to p.col } }.toSet()
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                assertTrue((r to c) in occupied, "Expected entity at ($r, $c)")
            }
        }
    }

    @Test
    fun skipsWhenPhaseIsNotProcessingMatches() {
        val matchGravitySystem = MatchGravitySystem()
        val world = World { with(matchGravitySystem) }

        val boardEntity = world.createEntity()
        world.addComponent(boardEntity, BoardStateComponent(
            phase = GamePhase.IDLE,
            gridSize = 3,
        ))

        val types = listOf(
            listOf(1, 1, 1),
            listOf(2, 3, 4),
            listOf(3, 4, 5),
        )
        for (r in 0 until 3) {
            for (c in 0 until 3) {
                val e = world.createEntity()
                world.addComponent(e, GridPositionComponent(r, c))
                world.addComponent(e, JellyTypeComponent(types[r][c]))
            }
        }

        world.process()

        val board = world.getComponent(boardEntity, BoardStateComponent::class)!!
        assertEquals(GamePhase.IDLE, board.phase)
        assertEquals(0, board.score)
    }
}

class FallResolveSystemTest {

    @Test
    fun clearsFallingComponentsAndTransitionsToIdle() {
        val fallResolveSystem = FallResolveSystem()
        val world = World { with(fallResolveSystem) }

        val boardEntity = world.createEntity()
        world.addComponent(boardEntity, BoardStateComponent(
            phase = GamePhase.RESOLVE_FALL,
            gridSize = 3,
        ))

        val fallingMapper = world.mapper<FallingComponent>()
        val e1 = world.createEntity()
        fallingMapper.set(e1, FallingComponent(-1, 0))
        val e2 = world.createEntity()
        fallingMapper.set(e2, FallingComponent(0, 1))

        assertEquals(2, world.getEntitiesForAspect(Aspect.all(FallingComponent::class)).size)

        world.process()

        assertEquals(0, world.getEntitiesForAspect(Aspect.all(FallingComponent::class)).size)
        val board = world.getComponent(boardEntity, BoardStateComponent::class)!!
        assertEquals(GamePhase.IDLE, board.phase)
    }

    @Test
    fun detectsCascadingMatches() {
        val fallResolveSystem = FallResolveSystem()
        val world = World { with(fallResolveSystem) }

        val boardEntity = world.createEntity()
        world.addComponent(boardEntity, BoardStateComponent(
            phase = GamePhase.RESOLVE_FALL,
            gridSize = 3,
        ))

        val fallingMapper = world.mapper<FallingComponent>()
        val types = listOf(
            listOf(1, 2, 3),
            listOf(1, 3, 2),
            listOf(1, 2, 3),
        )
        for (r in 0 until 3) {
            for (c in 0 until 3) {
                val e = world.createEntity()
                world.addComponent(e, GridPositionComponent(r, c))
                world.addComponent(e, JellyTypeComponent(types[r][c]))
                if (c == 0) fallingMapper.set(e, FallingComponent(fromRow = r - 1, toRow = r))
            }
        }

        world.process()

        val board = world.getComponent(boardEntity, BoardStateComponent::class)!!
        assertEquals(GamePhase.PROCESSING_MATCHES, board.phase)
    }
}

class SwapResolveSystemTest {

    @Test
    fun resolveReturnSwap_goesToIdle() {
        val swapResolveSystem = SwapResolveSystem()
        val world = World { with(swapResolveSystem) }

        val boardEntity = world.createEntity()
        world.addComponent(boardEntity, BoardStateComponent(
            phase = GamePhase.RESOLVE_SWAP,
            gridSize = 3,
        ))

        val posMapper = world.mapper<GridPositionComponent>()
        val swappingMapper = world.mapper<SwappingComponent>()

        val eA = world.createEntity()
        world.addComponent(eA, GridPositionComponent(0, 0))
        world.addComponent(eA, JellyTypeComponent(1))
        swappingMapper.set(eA, SwappingComponent(
            sourceRow = 0, sourceCol = 1,
            targetRow = 0, targetCol = 0,
            isReturning = true,
        ))

        val eB = world.createEntity()
        world.addComponent(eB, GridPositionComponent(0, 1))
        world.addComponent(eB, JellyTypeComponent(2))
        swappingMapper.set(eB, SwappingComponent(
            sourceRow = 0, sourceCol = 0,
            targetRow = 0, targetCol = 1,
            isReturning = true,
        ))

        world.process()

        val board = world.getComponent(boardEntity, BoardStateComponent::class)!!
        assertEquals(GamePhase.IDLE, board.phase)
        assertFalse(swappingMapper.has(eA))
        assertFalse(swappingMapper.has(eB))
        assertEquals(0, posMapper[eA]!!.row)
        assertEquals(0, posMapper[eA]!!.col)
        assertEquals(0, posMapper[eB]!!.row)
        assertEquals(1, posMapper[eB]!!.col)
    }
}

// ---------------------------------------------------------------------------
// Game ViewModel Tests
// ---------------------------------------------------------------------------

class GameViewModelTest {

    @Test
    fun initialBoard_hasNoMatchesAndHasValidMove() {
        repeat(50) {
            val vm = GameViewModel()
            val grid = vm.state.grid
            for (r in grid.indices) {
                var c = 0
                while (c < grid[r].size) {
                    val t = grid[r][c].type
                    var len = 1
                    while (c + len < grid[r].size && grid[r][c + len].type == t) len++
                    assertTrue(len < 3, "Horizontal match at row=$r col=$c len=$len type=$t")
                    c += len
                }
            }
            for (c in grid[0].indices) {
                var r = 0
                while (r < grid.size) {
                    val t = grid[r][c].type
                    var len = 1
                    while (r + len < grid.size && grid[r + len][c].type == t) len++
                    assertTrue(len < 3, "Vertical match at row=$r col=$c len=$len type=$t")
                    r += len
                }
            }
            assertTrue(vm.hasValidMove(), "Board should have at least one valid move")
        }
    }

    @Test
    fun hasValidMove_trueWhenMoveExists() {
        val grid = listOf(
            listOf(1, 2, 1, 2, 1, 2, 1),
            listOf(2, 1, 1, 1, 2, 1, 2),
            listOf(1, 2, 1, 2, 1, 2, 1),
            listOf(2, 1, 2, 1, 2, 1, 2),
            listOf(1, 2, 1, 2, 1, 2, 1),
            listOf(2, 1, 2, 1, 2, 1, 2),
            listOf(1, 2, 1, 2, 1, 2, 1),
        )
        val vm = GameViewModel(initialGrid = grid)
        assertTrue(vm.hasValidMove())
    }

    @Test
    fun hasValidMove_falseWhenNoMoveExists() {
        val grid = listOf(
            listOf(1, 2, 3, 4, 5, 6, 1),
            listOf(2, 3, 4, 5, 6, 1, 2),
            listOf(3, 4, 5, 6, 1, 2, 3),
            listOf(4, 5, 6, 1, 2, 3, 4),
            listOf(5, 6, 1, 2, 3, 4, 5),
            listOf(6, 1, 2, 3, 4, 5, 6),
            listOf(1, 2, 3, 4, 5, 6, 1),
        )
        val vm = GameViewModel(initialGrid = grid)
        assertFalse(vm.hasValidMove())
    }

    private val invalidSwapGrid = listOf(
        listOf(1, 2, 3, 4, 5, 6, 1),
        listOf(2, 3, 4, 5, 6, 1, 2),
        listOf(3, 4, 5, 6, 1, 2, 3),
        listOf(4, 5, 6, 1, 2, 3, 4),
        listOf(5, 6, 1, 2, 3, 4, 5),
        listOf(6, 1, 2, 3, 4, 5, 6),
        listOf(1, 2, 3, 4, 5, 6, 1),
    )

    @Test
    fun invalidSwap_startsReturnAnimationBeforeRestoringGrid() {
        val viewModel = GameViewModel(initialGrid = invalidSwapGrid)

        viewModel.onCellClick(GridPos(0, 0))
        viewModel.onCellClick(GridPos(0, 1))

        assertTrue(viewModel.isAnimating)
        assertEquals(2, viewModel.state.swappingCells.size)
        assertTrue(viewModel.state.swappingCells.values.none { it.isReturning })

        viewModel.onSwapAnimationFinished()

        val returnAnimations = viewModel.state.swappingCells.values
        assertEquals(2, returnAnimations.size)
        assertTrue(returnAnimations.all { it.isReturning })
        assertEquals(setOf(GridPos(0, 1), GridPos(0, 0)), returnAnimations.map { it.from }.toSet())
        assertEquals(2, viewModel.state.grid[0][0].type)
        assertEquals(1, viewModel.state.grid[0][1].type)

        viewModel.onSwapAnimationFinished()

        assertFalse(viewModel.isAnimating)
        assertTrue(viewModel.state.swappingCells.isEmpty())
        assertEquals(1, viewModel.state.grid[0][0].type)
        assertEquals(2, viewModel.state.grid[0][1].type)
        assertEquals(0, viewModel.state.score)
    }

    @Test
    fun selectionToggle_doesNotAnimateOrScore() {
        val viewModel = GameViewModel(initialGrid = invalidSwapGrid)

        viewModel.onCellClick(GridPos(0, 0))
        assertFalse(viewModel.isAnimating)
        assertEquals(GridPos(0, 0), viewModel.state.selected)

        viewModel.onCellClick(GridPos(0, 0))
        assertFalse(viewModel.isAnimating)
        assertNull(viewModel.state.selected)
        assertEquals(0, viewModel.state.score)
    }
}
