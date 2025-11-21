package com.game.engine.ecs

import kotlin.jvm.JvmName

class Family(private val mask: Long) {
    @PublishedApi
    internal val entities = ArrayList<Entity>()

    private val entitiesMap = HashMap<Int, Entity>()

    fun matches(entity: Entity): Boolean {
        return (entity.componentBits and mask) == mask
    }

    fun find(id: Int): Entity? {
        return entitiesMap[id]
    }

    internal fun add(entity: Entity): Boolean {
        if (!matches(entity)) return false
        val result = entities.add(entity)
        entitiesMap[entity.id] = entity
        return result
    }

    internal fun remove(entity: Entity): Boolean {
        val result = entities.remove(entity)
        entitiesMap.remove(entity.id)
        return result
    }
}

inline val Family.size: Int get() = entities.size
inline val Family.isEmpty: Boolean get() = entities.isEmpty()
inline val Family.isNotEmpty: Boolean get() = entities.isNotEmpty()
fun Family.randomOrNull(): Entity? = if (isEmpty) null else entities.random()

@JvmName("each0")
inline fun Family.each(
    action: (Entity) -> Unit
) {
    for (i in entities.indices) {
        val entity = entities[i]
        action(entity)
    }
}

@JvmName("each1")
inline fun <reified A : Component> Family.each(
    action: (Entity, A) -> Unit
) {
    for (i in entities.indices) {
        val entity = entities[i]
        action(entity, entity.get())
    }
}

@JvmName("each2")
inline fun <reified A : Component, reified B : Component> Family.each(
    action: (Entity, A, B) -> Unit
) {
    for (i in entities.indices) {
        val entity = entities[i]
        action(entity, entity.get(), entity.get())
    }
}

@JvmName("each3")
inline fun <reified A : Component, reified B : Component, reified C : Component> Family.each(
    action: (Entity, A, B, C) -> Unit
) {
    for (i in entities.indices) {
        val entity = entities[i]
        action(entity, entity.get(), entity.get(), entity.get())
    }
}

// --- 获取实体 ---
inline fun Family.find(predicate: (Entity) -> Boolean): Entity? {
    var entity: Entity? = null
    for (i in entities.indices) {
        entity = entities[i]
        if (predicate(entity)) break
    }
    return entity
}

fun Family.first(): Entity = entities.first()
fun Family.firstOrNull(): Entity? = entities.firstOrNull()

fun Family.last(): Entity = entities.last()
fun Family.lastOrNull(): Entity? = entities.lastOrNull()

inline fun <reified A : Component> Family.get(): A {
    return first().get()
}

inline fun <reified A : Component> Family.getOrNull(): A? {
    return firstOrNull()?.get()
}

@JvmName("components1")
inline fun <reified A : Component, reified B : Component> Family.components(): Pair<A, B> {
    return first().run { Pair(get(), get()) }
}

@JvmName("components2")
inline fun <reified A : Component, reified B : Component, reified C: Component> Family.components(): Triple<A, B, C> {
    return first().run { Triple(get(), get(), get()) }
}