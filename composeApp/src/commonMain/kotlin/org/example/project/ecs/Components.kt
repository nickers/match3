package org.example.project.ecs

/**
 * Grid position of a gem entity on the game board.
 * Mutable so systems can update position in-place (e.g. gravity).
 */
data class GridPositionComponent(var row: Int, var col: Int) : Component

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
) : Component

/**
 * Attached to entities that need a fall animation after a match-and-remove cycle.
 * [fromRow] can be negative for new gems entering from above the board.
 */
data class FallingComponent(
    val fromRow: Int,
    val toRow: Int,
) : Component
