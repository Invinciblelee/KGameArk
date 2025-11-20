package com.game.engine.ecs

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.engine.core.GameScope
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

class Family(val components: Set<KClass<out Component>>) {
    // 缓存符合这个组合的实体列表
    val entities = ArrayList<Entity>()

    // 检查一个实体是否属于这个家族
    fun matches(entity: Entity): Boolean {
        return components.all { entity.components.containsKey(it) }
    }
}

class World(private val scope: GameScope): GameScope by scope {
    // 所有实体的总池子
    private val entities = ArrayList<Entity>()
    private val pendingRemoval = ArrayList<Entity>()

    private val systems = ArrayList<System>()

    // 核心优化：Family 缓存
    // Key: 组件类型的集合, Value: 符合这个组合的实体列表
    private val families = HashMap<Set<KClass<out Component>>, Family>()

    private var nextId = 0

    // --- 创建实体 ---
    fun entity(block: Entity.() -> Unit): Entity {
        val e = Entity(nextId++).apply(block)
        entities.add(e)

        // 核心：新实体诞生时，检查它属于哪些已有的 Family
        families.values.forEach { family ->
            if (family.matches(e)) {
                family.entities.add(e)
            }
        }
        return e
    }

    // --- 移除实体 (关键补充) ---
    fun remove(entity: Entity) {
        if (!pendingRemoval.contains(entity)) {
            pendingRemoval.add(entity)
        }
    }

    // --- 核心查询逻辑 ---
    fun getEntitiesFor(vararg componentTypes: KClass<out Component>): List<Entity> {
        val key = componentTypes.toSet()
        val family = families.getOrPut(key) {
            Family(key).also { fam ->
                entities.filter { fam.matches(it) }.forEach { fam.entities.add(it) }
            }
        }
        return family.entities
    }

    fun install(system: System) {
        system.attach(this)
        systems.add(system)
    }

    fun update(dt: Float) {
        systems.forEach { it.update(dt) }

        flushEntities()
    }

    fun draw(drawScope: DrawScope) {
        systems.forEach { it.draw(drawScope) }
    }

    fun clear() {
        entities.clear()
        systems.clear()
        families.clear()
        nextId = 0
    }

    private fun flushEntities() {
        if (pendingRemoval.isEmpty()) return

        // 真正执行删除
        pendingRemoval.forEach { entity ->
            // 1. 从总池移除
            entities.remove(entity)
            // 2. 从索引/Family移除
            families.values.forEach { it.entities.remove(entity) } // 假设你用了 Family 优化
        }
        pendingRemoval.clear()
    }

}

@JvmName("query1")
inline fun <reified A : Component> World.query(): List<Entity> {
    return getEntitiesFor(A::class)
}

@JvmName("query2")
inline fun <reified A : Component, reified B : Component> World.query(): List<Entity> {
    return getEntitiesFor(A::class, B::class)
}

@JvmName("query3")
inline fun <reified A : Component, reified B : Component, reified C : Component> World.query(): List<Entity> {
    return getEntitiesFor(A::class, B::class, C::class)
}

@JvmName("findEntity1")
inline fun <reified A : Component> World.findEntity(): Entity {
    return getEntitiesFor(A::class).first()
}

@JvmName("findEntity2")
inline fun <reified A : Component, reified B : Component> World.findEntity(): Entity {
    return getEntitiesFor(A::class, B::class).first()
}

@JvmName("findEntity3")
inline fun <reified A : Component, reified B : Component, reified C : Component> World.findEntity(): Entity {
    return getEntitiesFor(A::class, B::class, C::class).first()
}

@JvmName("findEntityOrNull1")
inline fun <reified A : Component> World.findEntityOrNull(): Entity? {
    return getEntitiesFor(A::class).firstOrNull()
}

@JvmName("findEntityOrNull12")
inline fun <reified A : Component, reified B : Component> World.findEntityOrNull(): Entity? {
    return getEntitiesFor(A::class, B::class).firstOrNull()
}

@JvmName("findEntityOrNull3")
inline fun <reified A : Component, reified B : Component, reified C : Component> World.findEntityOrNull(): Entity? {
    return getEntitiesFor(A::class, B::class, C::class).firstOrNull()
}

inline fun <reified A : Component> World.get(): A {
    return query<A>().first().get<A>()
}

inline fun <reified A : Component, reified B : Component> World.getPair(): Pair<A, B> {
    val entity = query<A, B>().first()
    return entity.get<A>() to entity.get<B>()
}

inline fun <reified A : Component> World.getOrNull(): A? {
    return query<A>().firstOrNull()?.get<A>()
}

// --- 场景 B：遍历组件 (自动解包) ---

@JvmName("each1")
inline fun <reified A : Component> World.each(action: (Entity, A) -> Unit) {
    val entities = getEntitiesFor(A::class)
    for (i in entities.indices) {
        val entity = entities[i]
        val a = entity.get<A>()
        action(entity, a)
    }
}

@JvmName("each2")
inline fun <reified A : Component, reified B : Component> World.each(action: (Entity, A, B) -> Unit) {
    val entities = getEntitiesFor(A::class, B::class)
    for (i in entities.indices) {
        val entity = entities[i]
        val a = entity.get<A>()
        val b = entity.get<B>()
        action(entity, a, b)
    }
}

@JvmName("each3")
inline fun <reified A : Component, reified B : Component, reified C : Component> World.each(action: (Entity, A, B, C) -> Unit) {
    val entities = getEntitiesFor(A::class, B::class, C::class)
    for (i in entities.indices) {
        val entity = entities[i]
        val a = entity.get<A>()
        val b = entity.get<B>()
        val c = entity.get<C>()
        action(entity, a, b, c)
    }
}
