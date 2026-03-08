package org.example.project

data class GridPos(val row: Int, val col: Int)

data class SwapAnimation(
    val from: GridPos,
    val to: GridPos,
    val isReturning: Boolean = false,
)

data class JellyCell(
    val type: Int,   // 1..6, maps to jelly_1..jelly_6
    val id: Int,     // stable entity identity for animation keys
)

/** For each entity id, (fromRow, toRow) describing the fall animation. */
typealias FallingCells = Map<Int, Pair<Int, Int>>

/**
 * Immutable UI snapshot of the ECS world, consumed by Compose for rendering.
 * All game logic lives in the ECS systems; this is purely a view-model DTO.
 */
data class GameState(
    val grid: List<List<JellyCell>>,
    val selected: GridPos? = null,
    val swappingCells: Map<Int, SwapAnimation> = emptyMap(),
    val score: Int = 0,
    val fallingCells: FallingCells = emptyMap(),
)
