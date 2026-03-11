# ECS Architecture Migration Notes

## Summary

Refactored the ECS implementation from a "systems-as-services" pattern (where
`processSystem()` was empty and systems were called directly by the ViewModel)
to a proper frame-based architecture where every system's `processSystem()` is
invoked each frame via `World.process()`, in strict registration order.

Rendering is now a dedicated `RenderSystem` that projects ECS state into an
immutable `GameState` snapshot for Compose.

---

## Previous Architecture

| Aspect | Implementation |
|--------|----------------|
| `MatchSystem.processSystem()` | Empty. Called via `findMatches()` directly. |
| `GravitySystem.processSystem()` | Empty. Called via `applyGravity()` / `clearFallingComponents()`. |
| `World.process()` | Never called in game code. |
| Game orchestration | Entirely in `GameViewModel` (selection, swap, match loop, gravity, snapshot). |
| Rendering | `GameViewModel.buildSnapshot()` — mixed with game logic. |
| System ordering | Irrelevant since systems were invoked manually. |

### Problems

1. **`processSystem()` was dead code** — systems were service objects, not ECS
   systems.
2. **No frame-based processing** — `World.process()` was never called. The
   ViewModel hand-rolled the game loop.
3. **Rendering was coupled to the ViewModel** — `buildSnapshot()` read every
   component type directly, mixing game-state projection with game logic.
4. **System ordering was meaningless** — registration order had no effect on
   runtime behaviour.
5. **`getAllEntityIds()` had a bug** — referenced `activeObjects` instead of
   `activeEntities` (compile error if the method were ever called).

---

## New Architecture

### Systems (in processing order)

| # | System | Active Phase | Responsibility |
|---|--------|-------------|----------------|
| 1 | `InputSystem` | `IDLE` | Reads queued player input (clicks / drags), manages selection, triggers swaps. |
| 2 | `SwapResolveSystem` | `RESOLVE_SWAP` | Applies final positions after swap animation, checks matches, initiates return swap or match processing. |
| 3 | `MatchGravitySystem` | `PROCESSING_MATCHES` | Finds matches, deletes matched entities, applies gravity, spawns new gems. Cascades internally when gravity produces no falling cells. |
| 4 | `FallResolveSystem` | `RESOLVE_FALL` | Clears `FallingComponent` after fall animation, checks for cascading matches. |
| 5 | `RenderSystem` | Always | Projects ECS state → `GameState` snapshot consumed by Compose. |

### Game Phases

```
IDLE ──[user input]──▶ ANIMATING_SWAP
                           │
              ┌────────────┘ (Compose animation finishes)
              ▼
        RESOLVE_SWAP
          │         │
          │ match   │ no match
          ▼         ▼
  PROCESSING_MATCHES  ANIMATING_SWAP (return)
          │                   │
          ▼                   ▼
    ANIMATING_FALL      RESOLVE_SWAP
          │                   │
          ▼                   ▼
     RESOLVE_FALL           IDLE
       │       │
       │cascade│ none
       ▼       ▼
PROCESSING_   IDLE
 MATCHES
```

### Key Design Decisions

1. **Phase-gated systems.** Each system checks the current `GamePhase` on the
   singleton `BoardStateComponent` and exits early when it's not its turn. This
   makes `World.process()` safe to call unconditionally — systems self-gate.

2. **Multi-pass `processWorld()` loop in the ViewModel.** Because systems run
   sequentially in a single `World.process()` pass, a phase set by
   `FallResolveSystem` (late in the order) won't be seen by
   `MatchGravitySystem` (earlier in the order) until the next pass. The
   ViewModel loops `World.process()` until the phase stabilises at `IDLE` or an
   animation phase. This keeps the ECS core simple (single-pass) while
   supporting cascades that cross system-ordering boundaries.

3. **Match detection as a shared utility.** `findMatchesOnGrid()` is a pure
   top-level function used by `SwapResolveSystem` (to decide return vs. proceed)
   and `MatchGravitySystem` (for the cascade loop). Extracting it avoids
   cross-system method calls and keeps each system self-contained.

4. **Rendering as a system.** `RenderSystem` queries entities with the relevant
   component aspects each frame and writes a `GameState` to its `gameState`
   property. The ViewModel reads this after processing — no more
   `buildSnapshot()` scattered through game logic.

5. **Board entity.** A singleton entity carrying `BoardStateComponent` holds
   game-level state (phase, score, grid size). Systems access it via
   `World.boardState()`.

6. **Input via event queue.** `InputSystem` exposes `enqueueClick()` and
   `enqueueDragSwap()`. The ViewModel pushes events; the system drains them
   during `IDLE` and discards them otherwise.

---

## Migration Path Taken

### Step 1 — Introduce `GamePhase` and `BoardStateComponent`

Added to `Components.kt`. No behaviour changes yet — the old systems still
compiled.

### Step 2 — Extract match detection into a shared utility

`findMatchesOnGrid(world, gridSize)` — a pure function taking a `World` and
grid size, returning matched entity IDs. Replaced `MatchSystem.findMatches()`.

### Step 3 — Replace MatchSystem / GravitySystem with frame-based systems

- `SwapResolveSystem`: absorbs position-apply + match-check + return-swap logic
  from `GameViewModel.onSwapAnimationFinished()`.
- `MatchGravitySystem`: absorbs match-gravity cascade loop from
  `GameViewModel.processMatches()`.
- `FallResolveSystem`: absorbs falling cleanup + cascade check from
  `GameViewModel.onFallAnimationFinished()`.

### Step 4 — Add `InputSystem`

Absorbs selection and swap-trigger logic from `GameViewModel.onCellClick()` and
`GameViewModel.onDragSwap()`.

### Step 5 — Add `RenderSystem`

Moves `buildSnapshot()` into `RenderSystem.processSystem()`. Exposes a
`gameState` property read by the ViewModel.

### Step 6 — Slim down `GameViewModel`

The ViewModel becomes:
- An event router (enqueues input, transitions animation phases).
- A `processWorld()` loop driver.
- An initialisation host (grid setup — not per-frame, so kept in the ViewModel).

### Step 7 — Fix `Core.kt` bug

`getAllEntityIds()` referenced non-existent `activeObjects`; fixed to
`activeEntities.toSet()`.

---

## Potential Issues and Caveats

### 1. Cross-system data dependency in a single pass

`SwapResolveSystem` needs match results to decide between return-swap and
match-processing, but `MatchGravitySystem` runs after it. The solution is to
have `SwapResolveSystem` call `findMatchesOnGrid()` directly rather than relying
on a later system's output. This trades strict "systems never peek at shared
state" purity for correct single-pass behaviour.

### 2. Multi-pass loop ceiling

`processWorld()` loops up to 10 iterations. In theory, cascading
gravity → match → gravity chains could exceed this. In practice, each cascade
requires a fall animation (breaking the loop), so consecutive non-animated
cascades only happen when gravity doesn't move any cells — which the current
spawn logic prevents (new gems always have a negative `fromRow`). The ceiling is
a safety net.

### 3. Event-driven vs. fixed-timestep

The game is event-driven (input → animation → callback), not a continuous
fixed-timestep loop. "Frame" here means each `World.process()` invocation, not a
display frame. Systems are called on every processing frame, which happens on
user input and animation-completion callbacks. This is a deliberate match to the
Compose reactive model rather than a traditional game loop.

### 4. Initialization is not ECS-driven

Grid creation, initial-match elimination, and valid-move checking remain in the
ViewModel. These are one-shot setup steps, not per-frame processing, so making
them systems would add complexity without benefit. They manipulate the world
directly before the first `World.process()` call.

### 5. `BoardStateComponent` is mutable shared state

`BoardStateComponent.phase` and `.score` are mutated by multiple systems.
Correctness depends on systems running in the correct order within a single pass
and on the ViewModel's phase transitions being gated (e.g.
`onSwapAnimationFinished` checks phase before transitioning). This is
inherently less safe than an immutable-event architecture but is standard ECS
practice.

### 6. Compose animation timing

Animation completion is signalled by Compose callbacks (`onSwapFinished`,
`onFallFinished`), not by the ECS. The ViewModel translates these into phase
transitions. If Compose were replaced (e.g. for a canvas-based renderer), these
callbacks would need equivalent hooks.

### 7. Test seam changes

The old `MatchSystem.findMatches()` was easy to call in isolation.
`findMatchesOnGrid()` provides the same seam. System tests now create a `World`
with the system under test, set the board phase, and call `world.process()`.

---

## Files Changed

| File | Change |
|------|--------|
| `ecs/Core.kt` | Fixed `getAllEntityIds()` bug, cleaned comments. |
| `ecs/Components.kt` | Added `GamePhase` enum, `BoardStateComponent`. |
| `ecs/Systems.kt` | Complete rewrite: `InputSystem`, `SwapResolveSystem`, `MatchGravitySystem`, `FallResolveSystem`, `RenderSystem`, plus `findMatchesOnGrid()` utility and `World.boardState()` extension. |
| `GameViewModel.kt` | Slimmed to event routing + `processWorld()` loop + grid init. Removed `buildSnapshot()`, direct system calls, and per-entity orchestration logic. |
| `GameState.kt` | No changes. |
| `App.kt` | No changes. |
| `ComposeAppCommonTest.kt` | Updated to test new systems and `findMatchesOnGrid` utility; added system-level and ordering tests. |
