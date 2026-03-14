package pl.nickers.match3.ecs

import kotlinproject.composeapp.generated.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Serializable config entry as read from entity_config.json.
 * Each entry describes a spawnable entity variant with relative [weight],
 * a list of possible body images, and a list of possible face images.
 */
@Serializable
data class EntityEntryConfig(
    val type: String,
    val weight: Double,
    val body: List<String>,
    val face: List<String>,
)

/**
 * A selected entity ready to be turned into ECS components.
 */
data class EntitySelection(
    val type: String,
    val bodyImage: String,
    val faceImage: String,
    val typeId: Int,
)

/**
 * Catalog of entity templates loaded from entity_config.json.
 *
 * Assigns stable integer type IDs to each unique body image so that
 * the match-detection system can compare by Int as before.
 * Bombs always receive typeId 0; jelly variants are numbered from 1.
 *
 * Usage:
 *   val catalog = EntityCatalog.load()   // suspend, loads from resource
 *   val selection = catalog.selectRandom(random)
 */
class EntityCatalog(val entries: List<EntityEntryConfig>) {

    private val totalWeight: Double = entries.sumOf { it.weight }

    /** Maps body image filename → integer type ID (0 = bomb, 1+ = jelly). */
    val bodyTypeMap: Map<String, Int>

    /** All unique body image filenames for jelly-type entries, in catalog order. */
    val jellyBodyImages: List<String>

    init {
        var nextId = 1
        val map = mutableMapOf<String, Int>()
        val jellyList = mutableListOf<String>()

        for (entry in entries) {
            for (body in entry.body) {
                if (body in map) continue
                if (entry.type == "bomb") {
                    map[body] = 0
                } else {
                    map[body] = nextId++
                    jellyList.add(body)
                }
            }
        }

        bodyTypeMap = map
        jellyBodyImages = jellyList
    }

    /** Returns the integer type ID for a given body image filename. */
    fun typeIdForBody(bodyImage: String): Int = bodyTypeMap[bodyImage] ?: 1

    /**
     * Picks a random catalog entry (respecting weights), then picks a random
     * body and face image within that entry.
     */
    fun selectRandom(random: Random): EntitySelection {
        val entry = weightedSelect(random)
        val body = entry.body.random(random)
        val face = entry.face.random(random)
        return EntitySelection(
            type = entry.type,
            bodyImage = body,
            faceImage = face,
            typeId = typeIdForBody(body),
        )
    }

    private fun weightedSelect(random: Random): EntityEntryConfig {
        var remaining = random.nextDouble() * totalWeight
        for (entry in entries) {
            remaining -= entry.weight
            if (remaining <= 0.0) return entry
        }
        return entries.last()
    }

    companion object {

        private val jsonParser = Json { ignoreUnknownKeys = true }

        /** Loads and parses entity_config.json from the compose resource bundle. */
        suspend fun load(): EntityCatalog {
            val bytes = Res.readBytes("files/entity_config.json")
            val entries = jsonParser.decodeFromString<List<EntityEntryConfig>>(bytes.decodeToString())
            return EntityCatalog(entries)
        }

        /**
         * Hard-coded fallback matching the content of entity_config.json.
         * Used by tests and as the initial catalog before async loading completes.
         */
        fun default(): EntityCatalog = EntityCatalog(
            listOf(
                EntityEntryConfig(
                    type = "jelly",
                    weight = 10.0,
                    body = listOf("jelly_1.png", "jelly_2.png", "jelly_3.png", "jelly_4.png", "jelly_5.png", "jelly_6.png"),
                    face = listOf("face_1.png", "face_2.png", "face_3.png", "face_4.png", "face_5.png"),
                ),
                EntityEntryConfig(
                    type = "jelly",
                    weight = 1.0,
                    body = listOf("jelly_6.png"),
                    face = listOf("face_6.png"),
                ),
                EntityEntryConfig(
                    type = "bomb",
                    weight = 0.3,
                    body = listOf("bomb.png"),
                    face = listOf("bomb_face_1.png", "bomb_face_2.png"),
                ),
            )
        )
    }
}
