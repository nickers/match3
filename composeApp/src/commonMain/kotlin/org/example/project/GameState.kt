package org.example.project

import kotlin.random.Random

data class GridPos(val row: Int, val col: Int)

data class JellyCell(
    val type: Int,         // 1..6, maps to jelly_1..jelly_6
    val id: Int,           // stable identity for animation keys
)

/** For each cell id, (fromRow, toRow) - the row it fell from and its new row. */
typealias FallingCells = Map<Int, Pair<Int, Int>>

data class GameState(
    val grid: List<List<JellyCell>>,
    val selected: GridPos? = null,
    val swappingA: GridPos? = null,
    val swappingB: GridPos? = null,
    val score: Int = 0,
    val fallingCells: FallingCells = emptyMap(),
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

/** Returns all positions that are part of a horizontal or vertical match of 3+. */
fun GameState.findMatches(): Set<GridPos> {
    val matches = mutableSetOf<GridPos>()
    val g = grid
    val rows = g.size
    val cols = g[0].size

    // Horizontal runs
    for (r in 0 until rows) {
        var c = 0
        while (c < cols) {
            val type = g[r][c].type
            var len = 1
            while (c + len < cols && g[r][c + len].type == type) len++
            if (len >= 3) {
                for (i in 0 until len) matches.add(GridPos(r, c + i))
            }
            c += len
        }
    }

    // Vertical runs
    for (col in 0 until cols) {
        var r = 0
        while (r < rows) {
            val type = g[r][col].type
            var len = 1
            while (r + len < rows && g[r + len][col].type == type) len++
            if (len >= 3) {
                for (i in 0 until len) matches.add(GridPos(r + i, col))
            }
            r += len
        }
    }
    return matches
}

data class GravityResult(
    val grid: List<List<JellyCell>>,
    val newScore: Int,
    val fallingCells: FallingCells,
    val nextIdCounter: Int,
)

/**
 * Removes matched positions, applies gravity (gems fall down), fills with new random gems.
 */
fun GameState.removeMatchesAndApplyGravity(
    matches: Set<GridPos>,
    idCounter: Int,
    random: Random = Random.Default,
): GravityResult {
    val matchSet = matches
    var nextId = idCounter
    val falling = mutableMapOf<Int, Pair<Int, Int>>()
    val cols = grid[0].size
    val rows = grid.size

    val newGrid = (0 until cols).map { col ->
        // Non-matched cells in this column, top to bottom (preserve order for gravity)
        val remaining = (0 until rows)
            .map { row -> GridPos(row, col) }
            .filter { it !in matchSet }
            .map { grid[it.row][it.col] }

        // Build new column: remaining cells settle at bottom, new cells fill top
        val newColumn = mutableListOf<JellyCell>()
        val numNew = rows - remaining.size
        for (i in 0 until numNew) {
            val cell = JellyCell(type = random.nextInt(1, 7), id = nextId++)
            newColumn.add(cell)
            falling[cell.id] = (-(numNew - i)) to i  // fell from above
        }
        remaining.forEachIndexed { idx, cell ->
            val newRow = numNew + idx
            val oldRow = (0 until rows).first { grid[it][col].id == cell.id }
            newColumn.add(cell)
            if (oldRow != newRow) {
                falling[cell.id] = oldRow to newRow
            }
        }
        newColumn
    }.let { columns ->
        // columns are by col; we need rows
        (0 until rows).map { r -> (0 until cols).map { c -> columns[c][r] } }
    }

    val pointsPerMatch = 10
    val matchCount = matches.size
    val addedScore = matchCount * pointsPerMatch

    return GravityResult(
        grid = newGrid,
        newScore = score + addedScore,
        fallingCells = falling,
        nextIdCounter = nextId,
    )
}
