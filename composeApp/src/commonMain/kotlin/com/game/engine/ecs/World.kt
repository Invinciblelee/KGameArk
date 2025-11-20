package com.game.engine.ecs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.game.engine.core.GameScope
import com.game.engine.ecs.components.Camera
import com.game.engine.ecs.components.Transform
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
    private val entities = ArrayList<Entity>()
    private val pendingRemoval = ArrayList<Entity>()

    private val systems = ArrayList<System>()

    // 核心优化：Family 缓存
    private val families = HashMap<Long, Family>()

    private val cameraFamily: Family by lazy {
        getFamily(Camera::class, Transform::class)
    }

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

    fun getFamily(t1: KClass<out Component>): Family {
        val key = 1L shl ComponentKind.get(t1)
        return getFamilyInternal(key) { setOf(t1) }
    }

    fun getFamily(t1: KClass<out Component>, t2: KClass<out Component>): Family {
        val key = (1L shl ComponentKind.get(t1)) or (1L shl ComponentKind.get(t2))
        return getFamilyInternal(key) { setOf(t1, t2) }
    }

    fun getFamily(t1: KClass<out Component>, t2: KClass<out Component>, t3: KClass<out Component>): Family {
        val key = (1L shl ComponentKind.get(t1)) or
                (1L shl ComponentKind.get(t2)) or
                (1L shl ComponentKind.get(t3))
        return getFamilyInternal(key) { setOf(t1, t2, t3) }
    }

    private inline fun getFamilyInternal(key: Long, typesProvider: () -> Set<KClass<out Component>>): Family {
        return families.getOrPut(key) {
            Family(typesProvider()).also { fam ->
                entities.filterTo(fam.entities) { fam.matches(it) }
            }
        }
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
        var hasActiveCamera = false

        val potentialCameras = cameraFamily.entities

        potentialCameras.forEach { entity ->
            val cam = entity.get<Camera>()

            if (cam.isActive) {
                hasActiveCamera = true
                val t = entity.get<Transform>()
                drawScope.withCamera(cam, t) {
                    systems.forEach { it.draw(this) }
                }
            }
        }

        if (!hasActiveCamera) {
            systems.forEach { it.draw(drawScope) }
        }
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

    /**
     * 内部辅助：应用摄像机变换
     */
    private fun DrawScope.withCamera(
        camera: Camera,
        transform: Transform,
        block: DrawScope.() -> Unit
    ) {
        // 1. 计算视口在屏幕上的物理区域 (Pixel Coordinates)
        val viewportWidth = size.width * camera.viewport.width
        val viewportHeight = size.height * camera.viewport.height
        val viewportLeft = size.width * camera.viewport.left
        val viewportTop = size.height * camera.viewport.top

        // 2. 裁剪绘制区域 (Clip)
        // 这一步很重要，防止小地图的内容画到主屏幕外面去
        clipRect(
            left = viewportLeft,
            top = viewportTop,
            right = viewportLeft + viewportWidth,
            bottom = viewportTop + viewportHeight
        ) {
            // 3. 应用变换矩阵
            withTransform({
                // A. 移动到视口中心 (Viewport Center)
                translate(viewportLeft + viewportWidth / 2f, viewportTop + viewportHeight / 2f)

                // B. 缩放 (Zoom)
                scale(camera.zoom, camera.zoom, pivot = Offset.Zero)

                // C. 旋转 (Rotation) - 绕着摄像机中心转
                rotate(-transform.rotation)

                // D. 摄像机位移 (Camera Position) - 世界反向移动
                translate(-transform.position.x, -transform.position.y)
            }) {
                block()
            }
        }
    }


}

fun World.switchCamera(cameraName: String) {
    val cameraFamily = getFamily(Camera::class, Transform::class)
    cameraFamily.forEach<Camera> { _, camera ->
        camera.isActive = camera.name == cameraName
    }
}