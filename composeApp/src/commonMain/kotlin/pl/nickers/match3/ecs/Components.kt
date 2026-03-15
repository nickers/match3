package pl.nickers.match3.ecs

data class GridPositionComponent(var row: Int, var col: Int) : Component {

    fun setTo(other: GridPositionComponent) {
        row = other.row
        col = other.col
    }
}

data class JellyTypeComponent(val type: Int) : Component

data class BodyImageComponent(val image: String) : Component

data class JellyFaceComponent(val image: String) : Component

data class BombFaceComponent(val image: String) : Component

class SelectedComponent : Component

data class SwappingComponent(
    val sourceRow: Int,
    val sourceCol: Int,
    val targetRow: Int,
    val targetCol: Int,
    val isReturning: Boolean = false,
) : Component {

    val targetPosition get() = GridPositionComponent(targetRow, targetCol)
    val sourcePosition get() = GridPositionComponent(sourceRow, sourceCol)
}

data class FallingComponent(
    val fromRow: Int,
    val toRow: Int,
) : Component

class BombComponent : Component

data class IceCubeComponent(var life: Int = 3) : Component

class MatchNeighbourComponent : Component

class ExplodingComponent : Component

class FullBoardExplosionComponent : Component

data class PendingSwapValidationComponent(
    val otherEntity: Int,
) : Component

enum class GamePhase {
    IDLE,
    ANIMATING_SWAP,
    RESOLVE_SWAP,
    ANIMATING_FALL,
    RESOLVE_FALL,
    ANIMATING_EFFECTS,
    RESOLVE_EFFECTS,
    PROCESSING;

    val isAnimating: Boolean get() =
        this == ANIMATING_SWAP || this == ANIMATING_FALL || this == ANIMATING_EFFECTS
}

data class BoardStateComponent(
    var phase: GamePhase = GamePhase.IDLE,
    var score: Int = 0,
    val gridSize: Int = 7,
) : Component
