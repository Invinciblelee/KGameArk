package com.game.engine.ecs

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.engine.core.GameScope
import com.game.engine.ecs.components.Camera
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

/**
 * world {
 *         // ... 主角 ...
 *         val player = entity { ... }
 *
 *         // --- 摄像机 ---
 *         entity {
 *             with(Camera(isActive = true))
 *             with(Transform(Offset(0f, 0f)))
 *
 *             // 【关键】挂载通用组件！
 *             // 这行代码一加，摄像机自动就有了“弹性跟随主角”的能力
 *             with(SpringFollow(
 *                 targetId = player.id,
 *                 stiffness = 5.0f // 调整这个数值改变跟随的“软度”
 *             ))
 *
 *             // 【可选】如果你想让摄像机有惯性，甚至可以加 Velocity
 *             // with(Velocity())
 *         }
 *
 *         // ... 系统安装顺序 ...
 *         install(SteeringSystem()) // 先算跟随
 *         install(PhysicsSystem())  // 再算物理移动
 *         install(CameraSystem())   // 再修正边界
 *         install(RenderSystem())   // 最后画画
 *     }
 */
class World(private val scope: GameScope): GameScope by scope {
    // 所有实体的总池子
    @PublishedApi
    internal val entities = ArrayList<Entity>()

    private val entitiesMap = HashMap<Int, Entity>()
    private val pendingRemoval = ArrayList<Entity>()

    private val systems = ArrayList<System>()

    // 核心优化：Family 缓存
    private val families = HashMap<Long, Family>()
    private var nextId = 0

    // --- 创建实体 ---
    fun entity(block: Entity.() -> Unit): Entity {
        val e = Entity(nextId++).apply(block)
        entities.add(e)
        entitiesMap[e.id] = e
        families.values.forEach { family ->
            family.add(e)
        }
        return e
    }

    fun entities(count: Int, block: Entity.() -> Unit) {
        repeat(count) { entity(block) }
    }

    fun remove(entity: Entity) {
        if (!pendingRemoval.contains(entity)) {
            pendingRemoval.add(entity)
        }
    }

    fun find(id: Int): Entity? {
        return entitiesMap[id]
    }

    fun getFamily(vararg types: KClass<out Component>): Family {
        var mask = 0L
        for (i in types.indices) {
            mask = mask or (1L shl ComponentKind.get(types[i]))
        }
        return families.getOrPut(mask) {
            val family = Family(mask)
            entities.filterTo(family.entities) { family.matches(it) }
            family
        }
    }

    fun install(system: System) {
        system.attach(this)
        systems.add(system)
    }

    fun update(deltaTime: Float) {
        systems.forEach { it.update(deltaTime) }

        flush()
    }

    fun draw(drawScope: DrawScope) {
        systems.forEach {
            with(it) { drawScope.draw() }
        }
    }

    fun clear() {
        entities.clear()
        systems.clear()
        families.clear()
        nextId = 0
    }

    private fun flush() {
        if (pendingRemoval.isEmpty()) return

        // 真正执行删除
        pendingRemoval.forEach { entity ->
            // 1. 从总池移除
            entities.remove(entity)
            entitiesMap.remove(entity.id)
            // 2. 从索引/Family移除
            families.values.forEach { it.remove(entity) }
        }
        pendingRemoval.clear()
    }

}

@JvmName("family1")
inline fun <reified A : Component> World.family(): Family {
    return getFamily(A::class)
}

@JvmName("family2")
inline fun <reified A : Component, reified B : Component> World.family(): Family {
    return getFamily(A::class, B::class)
}

@JvmName("family3")
inline fun <reified A : Component, reified B : Component, reified C : Component> World.family(): Family {
    return getFamily(A::class, B::class, C::class)
}

inline fun World.find(predicate: (Entity) -> Boolean): Entity? {
    var entity: Entity? = null
    for (i in entities.indices) {
        entity = entities[i]
        if (predicate(entity)) break
    }
    return entity
}

inline fun <reified TargetTag : Component> World.switchCamera() {
    family<Camera>().each<Camera> { entity, camera ->
        camera.isActive = entity.has<TargetTag>()
    }
}