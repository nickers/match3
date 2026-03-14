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

/**
 * Marker: the entity is a bomb rather than a regular gem.
 * Bombs do not participate in match detection. Swapping a bomb with any gem
 * causes an explosion that removes all cells in the 3×3 area centred on the bomb.
 */
class BombComponent : Component

/**
 * Marker: attached to a bomb entity that is currently exploding.
 * The bomb's [GridPositionComponent] supplies the explosion centre.
 */
class ExplodingComponent : Component

/**
 * Marker: attached to an exploding bomb entity to indicate that the
 * explosion should cover the entire board (e.g. two bombs swapped).
 */
class FullBoardExplosionComponent : Component

/**
 * Attached to one of the two entities involved in a forward swap that
 * has not yet been validated. [otherEntity] is the id of the second
 * entity. If the processing loop finds no matches / effects, it uses
 * this component to create a return-swap animation.
 */
data class PendingSwapValidationComponent(
    val otherEntity: Int,
) : Component

// ---------------------------------------------------------------------------
// Game-level state
// ---------------------------------------------------------------------------

/**
 * Tracks the current phase of the game loop.
 *
 * The game processes phases in a fixed order:
 *   animate swap → fall down → activate effects → process matches → idle
 *
 * After any phase produces work, the loop restarts from the beginning.
 * Animation phases pause the loop until Compose finishes the animation and
 * fires a callback that transitions to the corresponding RESOLVE phase.
 */
enum class GamePhase {
    /** Waiting for player input. */
    IDLE,
    /** Swap animation is playing (forward or return). */
    ANIMATING_SWAP,
    /** Swap animation finished; resolve positions. */
    RESOLVE_SWAP,
    /** Fall animation is playing. */
    ANIMATING_FALL,
    /** Fall animation finished; clear falling markers. */
    RESOLVE_FALL,
    /** Effect animations are playing (e.g. bomb explosion). */
    ANIMATING_EFFECTS,
    /** Effect animations finished; execute effect logic. */
    RESOLVE_EFFECTS,
    /** Main processing loop: checks each step in order and decides next phase. */
    PROCESSING;

    val isAnimating: Boolean get() =
        this == ANIMATING_SWAP || this == ANIMATING_FALL || this == ANIMATING_EFFECTS
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
