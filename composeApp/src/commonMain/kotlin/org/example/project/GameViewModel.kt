package org.example.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

const val GRID_SIZE = 7
const val SWAP_DURATION_MS = 300   // configurable swap animation speed

class GameViewModel {

    var state by mutableStateOf(initialState())
        private set

    var isAnimating by mutableStateOf(false)
        private set

    private var idCounter: Int = GRID_SIZE * GRID_SIZE

    private fun initialState(): GameState {
        idCounter = 0
        val grid = List(GRID_SIZE) {
            List(GRID_SIZE) {
                JellyCell(type = Random.nextInt(1, 7), id = idCounter++)
            }
        }
        return GameState(grid = grid)
    }

    fun onCellClick(pos: GridPos) {
        if (isAnimating) return
        val current = state
        val sel = current.selected

        when {
            sel == null -> {
                state = current.copy(selected = pos)
            }
            sel == pos -> {
                state = current.copy(selected = null)
            }
            current.isAdjacent(sel, pos) -> {
                triggerSwap(sel, pos)
            }
            else -> {
                state = current.copy(selected = pos)
            }
        }
    }

    private fun triggerSwap(a: GridPos, b: GridPos) {
        isAnimating = true
        state = state.copy(selected = null, swappingA = a, swappingB = b)
    }

    /** Called by the UI after the swap animation completes. */
    fun onSwapAnimationFinished() {
        val a = state.swappingA ?: return
        val b = state.swappingB ?: return
        val swappedState = state.swapped(a, b).copy(swappingA = null, swappingB = null)
        val matches = swappedState.findMatches()

        if (matches.isEmpty()) {
            // Invalid move: revert swap
            state = state.swapped(b, a).copy(swappingA = null, swappingB = null)
            isAnimating = false
            return
        }

        processMatches(swappedState)
    }

    private fun processMatches(currentState: GameState) {
        var s = currentState
        var matches = s.findMatches()

        while (matches.isNotEmpty()) {
            val result = s.removeMatchesAndApplyGravity(matches, idCounter)
            idCounter = result.nextIdCounter

            s = s.copy(
                grid = result.grid,
                score = result.newScore,
                fallingCells = result.fallingCells,
            )
            if (result.fallingCells.isEmpty()) {
                matches = s.findMatches()
            } else {
                state = s
                return  // UI will call onFallAnimationFinished when done
            }
        }

        state = s.copy(fallingCells = emptyMap())
        isAnimating = false
    }

    /** Called by the UI after all fall animations complete. */
    fun onFallAnimationFinished() {
        val s = state
        if (s.fallingCells.isEmpty()) {
            isAnimating = false
            return
        }
        state = s.copy(fallingCells = emptyMap())
        val matches = state.findMatches()
        if (matches.isNotEmpty()) {
            processMatches(state)
        } else {
            isAnimating = false
        }
    }
}
