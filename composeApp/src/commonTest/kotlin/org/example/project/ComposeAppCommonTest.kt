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
}

// ---------------------------------------------------------------------------
// Match System Tests
// ---------------------------------------------------------------------------

class MatchSystemTest {

    private fun worldWithGrid(types: List<List<Int>>): Pair<World, MatchSystem> {
        val matchSystem = MatchSystem()
        val world = World { with(matchSystem) }
        val gridSize = types.size
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val e = world.createEntity()
                world.addComponent(e, GridPositionComponent(r, c))
                world.addComponent(e, JellyTypeComponent(types[r][c]))
            }
        }
        return world to matchSystem
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
        val (_, matchSystem) = worldWithGrid(types)
        val matches = matchSystem.findMatches(5)
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
        val (_, matchSystem) = worldWithGrid(types)
        val matches = matchSystem.findMatches(5)
        assertEquals(3, matches.size)
    }

    @Test
    fun detectsBothDirections() {
        val types = listOf(
            listOf(1, 1, 1),
            listOf(2, 1, 3),
            listOf(3, 1, 2),
        )
        val (_, matchSystem) = worldWithGrid(types)
        val matches = matchSystem.findMatches(3)
        assertEquals(5, matches.size)
    }

    @Test
    fun noMatchesOnUniqueGrid() {
        val types = listOf(
            listOf(1, 2, 1),
            listOf(2, 1, 2),
            listOf(1, 2, 1),
        )
        val (_, matchSystem) = worldWithGrid(types)
        val matches = matchSystem.findMatches(3)
        assertTrue(matches.isEmpty())
    }
}

// ---------------------------------------------------------------------------
// Gravity System Tests
// ---------------------------------------------------------------------------

class GravitySystemTest {

    @Test
    fun removesMatchedEntitiesAndFillsGrid() {
        val matchSystem = MatchSystem()
        val gravitySystem = GravitySystem()
        val world = World {
            with(matchSystem)
            with(gravitySystem)
        }
        val gridSize = 3
        val entityIds = mutableListOf<Int>()
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
                entityIds.add(e)
            }
        }

        val matches = matchSystem.findMatches(gridSize)
        assertEquals(3, matches.size)

        val result = gravitySystem.applyGravity(matches, gridSize)
        assertEquals(30, result.scoreGained)
        assertTrue(result.hasFallingCells)

        matches.forEach { assertFalse(world.entityExists(it)) }

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
    fun clearFallingComponents_removesAll() {
        val gravitySystem = GravitySystem()
        val world = World { with(gravitySystem) }
        val fallingMapper = world.mapper<FallingComponent>()

        val e1 = world.createEntity()
        fallingMapper.set(e1, FallingComponent(-1, 0))
        val e2 = world.createEntity()
        fallingMapper.set(e2, FallingComponent(0, 1))

        assertEquals(2, world.getEntitiesForAspect(Aspect.all(FallingComponent::class)).size)

        gravitySystem.clearFallingComponents()
        assertEquals(0, world.getEntitiesForAspect(Aspect.all(FallingComponent::class)).size)
    }
}

// ---------------------------------------------------------------------------
// Game ViewModel Tests
// ---------------------------------------------------------------------------

class GameViewModelTest {

    @Test
    fun initialBoard_hasNoMatches() {
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
        }
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
}
