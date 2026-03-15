package pl.nickers.match3.ecs

import kotlin.random.Random

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

fun World.createIceCubeEntity(
    row: Int,
    col: Int,
    life: Int = 3,
): Int {
    val entityId = createEntity()
    addComponent(entityId, GridPositionComponent(row = row, col = col))
    addComponent(entityId, JellyTypeComponent(type = -1))
    addComponent(entityId, BodyImageComponent(image = "ice_$life.png"))
    addComponent(entityId, IceCubeComponent(life = life))
    return entityId
}

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

fun World.createRandomBoardEntity(
    row: Int,
    col: Int,
    random: Random,
    catalog: EntityCatalog,
): Int {
    val selection = catalog.selectRandom(random)
    return createEntityFromSelection(row = row, col = col, selection = selection)
}
