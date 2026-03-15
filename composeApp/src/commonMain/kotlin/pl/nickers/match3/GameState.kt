package pl.nickers.match3

data class GridPos(val row: Int, val col: Int)

data class SwapAnimation(
    val from: GridPos,
    val to: GridPos,
    val isReturning: Boolean = false,
)

data class JellyCell(
    val type: Int,                             // match key: 1..N for jelly variants, 0 for bombs, -1 for ice cubes
    val id: Int,                               // stable entity identity for animation keys
    val isBomb: Boolean = false,
    val isIceCube: Boolean = false,
    val isEmpty: Boolean = false,
    val iceCubeLife: Int = 3,
    val bodyImage: String = "jelly_1.png",     // body drawable filename
    val faceImage: String = "face_1.png",      // face overlay drawable filename
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
    val explodingBombs: List<GridPos> = emptyList(),
    val fullBoardExplosion: Boolean = false,
)
