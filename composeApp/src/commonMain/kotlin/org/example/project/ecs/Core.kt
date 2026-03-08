package org.example.project.ecs

import kotlin.reflect.KClass

/**
 * Marker interface for all ECS components.
 * Modeled after artemis-odb's [com.artemis.Component].
 */
interface Component

/**
 * Defines a set of component types an entity must possess to be matched.
 * Modeled after artemis-odb's [com.artemis.Aspect].
 */
class Aspect private constructor(
    val allTypes: Set<KClass<out Component>>,
) {
    companion object {
        fun all(vararg types: KClass<out Component>) = Aspect(types.toSet())
    }
}

/**
 * Provides fast, type-safe access to components of a given type.
 * Modeled after artemis-odb's [com.artemis.ComponentMapper].
 */
class ComponentMapper<T : Component> internal constructor(
    private val type: KClass<T>,
    private val world: World,
) {
    operator fun get(entityId: Int): T? = world.getComponent(entityId, type)
    fun has(entityId: Int): Boolean = world.hasComponent(entityId, type)
    fun set(entityId: Int, component: T) { world.addComponent(entityId, component) }
    fun remove(entityId: Int) { world.removeComponent(entityId, type) }
}

/**
 * Base class for systems that operate on the [World].
 * Modeled after artemis-odb's [com.artemis.BaseSystem].
 */
abstract class BaseSystem {
    lateinit var world: World
        internal set
    var isEnabled: Boolean = true

    open fun initialize() {}
    abstract fun processSystem()
}

/**
 * System that automatically iterates over all entities matching an [Aspect].
 * Modeled after artemis-odb's [com.artemis.systems.IteratingSystem].
 */
abstract class IteratingSystem(val aspect: Aspect) : BaseSystem() {
    override fun processSystem() {
        world.getEntitiesForAspect(aspect).forEach { process(it) }
    }

    protected abstract fun process(entityId: Int)
}

/**
 * Builder for [World] configuration. Register systems before the world is created.
 * Modeled after artemis-odb's [com.artemis.WorldConfiguration].
 */
class WorldConfiguration {
    internal val systems = mutableListOf<BaseSystem>()

    fun with(system: BaseSystem): WorldConfiguration {
        systems.add(system)
        return this
    }
}

/**
 * Main container for entities, components, and systems.
 * Modeled after artemis-odb's [com.artemis.World].
 *
 * Entities are lightweight integer IDs. Components are stored in per-type maps.
 * Systems are registered at construction time and invoked via [process].
 */
class World(configure: WorldConfiguration.() -> Unit = {}) {
    private var nextEntityId = 0
    private val activeEntities = mutableSetOf<Int>()
    private val componentStores = mutableMapOf<KClass<out Component>, MutableMap<Int, Component>>()
    private val systems = mutableListOf<BaseSystem>()
    private val mappers = mutableMapOf<KClass<out Component>, ComponentMapper<*>>()

    init {
        val config = WorldConfiguration().apply(configure)
        config.systems.forEach { registerSystem(it) }
    }

    fun createEntity(): Int {
        val id = nextEntityId++
        activeEntities.add(id)
        return id
    }

    fun deleteEntity(id: Int) {
        activeEntities.remove(id)
        componentStores.values.forEach { it.remove(id) }
    }

    fun entityExists(id: Int): Boolean = id in activeEntities

    fun getAllEntityIds(): Set<Int> = activeEntities.toSet()

    fun <T : Component> addComponent(entityId: Int, component: T) {
        val type = component::class
        componentStores.getOrPut(type) { mutableMapOf() }[entityId] = component
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponent(entityId: Int, type: KClass<T>): T? {
        return componentStores[type]?.get(entityId) as? T
    }

    fun hasComponent(entityId: Int, type: KClass<out Component>): Boolean {
        return componentStores[type]?.containsKey(entityId) == true
    }

    fun removeComponent(entityId: Int, type: KClass<out Component>) {
        componentStores[type]?.remove(entityId)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getMapper(type: KClass<T>): ComponentMapper<T> {
        return mappers.getOrPut(type) { ComponentMapper(type, this) } as ComponentMapper<T>
    }

    inline fun <reified T : Component> mapper(): ComponentMapper<T> = getMapper(T::class)

    fun getEntitiesForAspect(aspect: Aspect): List<Int> {
        return activeEntities.filter { entityId ->
            aspect.allTypes.all { type -> hasComponent(entityId, type) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : BaseSystem> getSystem(type: KClass<T>): T {
        return systems.first { type.isInstance(it) } as T
    }

    inline fun <reified T : BaseSystem> getSystem(): T = getSystem(T::class)

    fun process() {
        systems.filter { it.isEnabled }.forEach { it.processSystem() }
    }

    private fun registerSystem(system: BaseSystem) {
        system.world = this
        systems.add(system)
        system.initialize()
    }
}
