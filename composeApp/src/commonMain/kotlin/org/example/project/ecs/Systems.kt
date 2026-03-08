package org.example.project.ecs

import kotlin.random.Random

/**
 * Detects horizontal and vertical matches of 3+ identical gem types on the board.
 * Not auto-processed; call [findMatches] explicitly when needed.
 */
class MatchSystem : BaseSystem() {
    private lateinit var posMapper: ComponentMapper<GridPositionComponent>
    private lateinit var typeMapper: ComponentMapper<JellyTypeComponent>

    override fun initialize() {
        posMapper = world.mapper()
        typeMapper = world.mapper()
    }

    fun findMatches(gridSize: Int): Set<Int> {
        val grid = buildGrid(gridSize)
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

    private fun buildGrid(gridSize: Int): Array<IntArray> {
        val grid = Array(gridSize) { IntArray(gridSize) { -1 } }
        val aspect = Aspect.all(GridPositionComponent::class, JellyTypeComponent::class)
        world.getEntitiesForAspect(aspect).forEach { entityId ->
            val pos = posMapper[entityId]!!
            if (pos.row in 0 until gridSize && pos.col in 0 until gridSize) {
                grid[pos.row][pos.col] = entityId
            }
        }
        return grid
    }

    override fun processSystem() { /* invoked explicitly via findMatches() */ }
}

/**
 * Removes matched entities, shifts surviving gems downward, and spawns
 * new random gems at the top. Attaches [FallingComponent] to every entity
 * that moved or was newly created.
 */
class GravitySystem : BaseSystem() {
    private lateinit var posMapper: ComponentMapper<GridPositionComponent>
    private lateinit var typeMapper: ComponentMapper<JellyTypeComponent>
    private lateinit var fallingMapper: ComponentMapper<FallingComponent>

    override fun initialize() {
        posMapper = world.mapper()
        typeMapper = world.mapper()
        fallingMapper = world.mapper()
    }

    data class GravityResult(
        val scoreGained: Int,
        val hasFallingCells: Boolean,
    )

    fun applyGravity(
        matches: Set<Int>,
        gridSize: Int,
        random: Random = Random.Default,
    ): GravityResult {
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
                val newRow = i
                world.addComponent(entityId, GridPositionComponent(row = newRow, col = col))
                world.addComponent(entityId, JellyTypeComponent(type = random.nextInt(1, 7)))
                fallingMapper.set(entityId, FallingComponent(fromRow = -(numNew - i), toRow = newRow))
                hasFalling = true
            }
        }

        return GravityResult(
            scoreGained = matches.size * 10,
            hasFallingCells = hasFalling,
        )
    }

    fun clearFallingComponents() {
        val aspect = Aspect.all(FallingComponent::class)
        world.getEntitiesForAspect(aspect).forEach { fallingMapper.remove(it) }
    }

    override fun processSystem() { /* invoked explicitly via applyGravity() */ }
}
