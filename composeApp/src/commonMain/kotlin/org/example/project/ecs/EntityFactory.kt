package org.example.project.ecs

import kotlin.random.Random

/**
 * Creates a jelly entity from a specific integer type (used for seeded/test grids).
 * Derives image filenames from the convention "jelly_N.png" / "face_N.png".
 */
fun World.createJellyEntity(
    row: Int,
    col: Int,
    jellyType: Int,
    random: Random,
): Int {
    val entityId = createEntity()
    addComponent(entityId, GridPositionComponent(row = row, col = col))
    addComponent(entityId, JellyTypeComponent(type = jellyType))
    addComponent(entityId, BodyImageComponent(image = "jelly_$jellyType.png"))
    addComponent(entityId, JellyFaceComponent(image = "face_${random.nextInt(1, 7)}.png"))
    return entityId
}

/**
 * Creates a bomb entity (used for seeded/test grids).
 */
fun World.createBombEntity(
    row: Int,
    col: Int,
    random: Random,
): Int {
    val entityId = createEntity()
    addComponent(entityId, GridPositionComponent(row = row, col = col))
    addComponent(entityId, JellyTypeComponent(type = 0))
    addComponent(entityId, BodyImageComponent(image = "bomb.png"))
    addComponent(entityId, BombComponent())
    addComponent(entityId, BombFaceComponent(image = "bomb_face_${random.nextInt(1, 3)}.png"))
    return entityId
}

/**
 * Creates an entity from an [EntitySelection] produced by [EntityCatalog.selectRandom].
 * This is the primary path for all runtime-spawned entities.
 */
fun World.createEntityFromSelection(
    row: Int,
    col: Int,
    selection: EntitySelection,
): Int {
    val entityId = createEntity()
    addComponent(entityId, GridPositionComponent(row = row, col = col))
    addComponent(entityId, JellyTypeComponent(type = selection.typeId))
    addComponent(entityId, BodyImageComponent(image = selection.bodyImage))
    when (selection.type) {
        "bomb" -> {
            addComponent(entityId, BombComponent())
            addComponent(entityId, BombFaceComponent(image = selection.faceImage))
        }
        else -> {
            addComponent(entityId, JellyFaceComponent(image = selection.faceImage))
        }
    }
    return entityId
}

/**
 * Picks a random entity variant from the [catalog] and spawns it at [row],[col].
 */
fun World.createRandomBoardEntity(
    row: Int,
    col: Int,
    random: Random,
    catalog: EntityCatalog,
): Int {
    val selection = catalog.selectRandom(random)
    return createEntityFromSelection(row = row, col = col, selection = selection)
}
