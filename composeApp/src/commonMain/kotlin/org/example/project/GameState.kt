package org.example.project

data class GridPos(val row: Int, val col: Int)

data class JellyCell(
    val type: Int,         // 1..6, maps to jelly_1..jelly_6
    val id: Int,           // stable identity for animation keys
)

data class GameState(
    val grid: List<List<JellyCell>>,
    val selected: GridPos? = null,
    val swappingA: GridPos? = null,
    val swappingB: GridPos? = null,
)

fun GameState.isAdjacent(a: GridPos, b: GridPos): Boolean {
    val dr = kotlin.math.abs(a.row - b.row)
    val dc = kotlin.math.abs(a.col - b.col)
    return (dr == 1 && dc == 0) || (dr == 0 && dc == 1)
}

fun GameState.swapped(a: GridPos, b: GridPos): GameState {
    val newGrid = grid.map { it.toMutableList() }.toMutableList()
    val tmp = newGrid[a.row][a.col]
    newGrid[a.row][a.col] = newGrid[b.row][b.col]
    newGrid[b.row][b.col] = tmp
    return copy(grid = newGrid.map { it.toList() })
}
