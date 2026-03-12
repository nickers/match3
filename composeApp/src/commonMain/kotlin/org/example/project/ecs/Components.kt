package org.example.project.ecs

/**
 * Grid position of a gem entity on the game board.
 * Mutable so systems can update position in-place (e.g. gravity).
 */
data class GridPositionComponent(var row: Int, var col: Int) : Component {

    fun setTo(other: GridPositionComponent) {
        row = other.row
        col = other.col
    }
}

/**
 * The visual type of a jelly gem (1–6), mapping to drawable resources.
 */
data class JellyTypeComponent(val type: Int) : Component

/**
 * Marker: the entity is currently selected by the player.
 */
class SelectedComponent : Component

/**
 * Attached to entities involved in a swap animation.
 * Positions are grid coordinates.
 */
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

/**
 * Attached to entities that need a fall animation after a match-and-remove cycle.
 * [fromRow] can be negative for new gems entering from above the board.
 */
data class FallingComponent(
    val fromRow: Int,
    val toRow: Int,
) : Component

// ---------------------------------------------------------------------------
// Game-level state
// ---------------------------------------------------------------------------

/**
 * Tracks the current phase of the game loop. Systems gate their processing
 * on the active phase to ensure correct ordering of game events.
 */
enum class GamePhase {
    /** Waiting for player input. */
    IDLE,
    /** Swap animation is playing (forward or return). */
    ANIMATING_SWAP,
    /** Swap animation finished; resolve the outcome. */
    RESOLVE_SWAP,
    /** Match detection + gravity cascade in progress. */
    PROCESSING_MATCHES,
    /** Fall animation is playing. */
    ANIMATING_FALL,
    /** Fall animation finished; check for cascading matches. */
    RESOLVE_FALL;

    val isAnimating: Boolean get() = this == ANIMATING_SWAP || this == ANIMATING_FALL
}

/**
 * Singleton component on the "board" entity. Holds game-level state that
 * systems need to coordinate: current phase, score, and grid dimensions.
 */
data class BoardStateComponent(
    var phase: GamePhase = GamePhase.IDLE,
    var score: Int = 0,
    val gridSize: Int = 7,
) : Component
