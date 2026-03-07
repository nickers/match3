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

    private fun initialState(): GameState {
        var idCounter = 0
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
        state = state.swapped(a, b).copy(swappingA = null, swappingB = null)
        isAnimating = false
    }
}
