package com.game.engine.ecs

import kotlin.reflect.KClass

class Family(private val components: Set<KClass<out Component>>) {
    val entities = ArrayList<Entity>()

    fun matches(entity: Entity): Boolean {
        return components.all { entity.has(it) }
    }
}

// 让 Family 用起来像 List
inline val Family.size: Int get() = entities.size
inline val Family.isEmpty: Boolean get() = entities.isEmpty()
inline val Family.isNotEmpty: Boolean get() = entities.isNotEmpty()

// 随机获取一个（比如随机攻击一个敌人）
fun Family.randomOrNull(): Entity? = if (isEmpty) null else entities.random()

// --- 1 个组件的遍历 ---
// 用法: family.forEach<Transform> { entity, t -> ... }
inline fun <reified A : Component> Family.forEach(
    action: (Entity, A) -> Unit
) {
    // 使用索引遍历，性能最高，无 Iterator 分配
    for (i in entities.indices) {
        val entity = entities[i]
        // 这里假定 Family 筛选正确，直接 get，速度极快
        action(entity, entity.get())
    }
}

// --- 2 个组件的遍历 (最常用) ---
// 用法: family.forEach<Transform, Velocity> { entity, t, v -> ... }
inline fun <reified A : Component, reified B : Component> Family.forEach(
    action: (Entity, A, B) -> Unit
) {
    for (i in entities.indices) {
        val entity = entities[i]
        action(entity, entity.get(), entity.get())
    }
}

// --- 3 个组件的遍历 ---
// 用法: family.forEach<Transform, Velocity, Sprite> { entity, t, v, s -> ... }
inline fun <reified A : Component, reified B : Component, reified C : Component> Family.forEach(
    action: (Entity, A, B, C) -> Unit
) {
    for (i in entities.indices) {
        val entity = entities[i]
        action(entity, entity.get(), entity.get(), entity.get())
    }
}

// --- 获取实体 ---

// 获取第一个实体 (比如拿到 Player)
fun Family.entity(): Entity = entities.first()
fun Family.entityOrNull(): Entity? = entities.firstOrNull()

inline fun <reified A : Component> Family.component(): A {
    return entity().get()
}

inline fun <reified A : Component> Family.componentOrNull(): A? {
    return entityOrNull()?.get()
}

// 拿到第一个实体的一对组件
inline fun <reified A : Component, reified B : Component> Family.components(): Pair<A, B> {
    return entity().get<A>() to entity().get<B>()
}