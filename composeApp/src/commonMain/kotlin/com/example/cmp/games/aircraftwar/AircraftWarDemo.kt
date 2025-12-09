@file:OptIn(ExperimentalTime::class)

package com.example.cmp.games.aircraftwar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontVariation.width
import androidx.compose.ui.unit.dp
import com.game.ecs.Component
import com.game.ecs.ComponentType
import com.game.ecs.Entity
import com.game.ecs.EntityTag
import com.game.ecs.Fixed
import com.game.ecs.IntervalSystem
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.World.Companion.inject
import com.game.engine.asset.AssetsManager
import com.game.engine.audio.AudioManager
import com.game.engine.context.PlatformContext
import com.game.engine.core.KGame
import com.game.engine.core.rememberGameSceneStack
import com.game.engine.geometry.ViewportTransform
import com.game.engine.geometry.clampInBounds
import com.game.engine.geometry.safeBounds
import com.game.engine.graphics.shader.BlueSky
import com.game.engine.input.InputManager
import com.game.engine.math.random
import com.game.engine.math.randomOffset
import com.game.engine.ui.ActiveRectangle
import com.game.plugins.components.Boundary
import com.game.plugins.components.Camera
import com.game.plugins.components.CameraTarget
import com.game.plugins.components.CharacterStats
import com.game.plugins.components.CleanupTag
import com.game.plugins.components.EnemyTag
import com.game.plugins.components.PlayerBulletTag
import com.game.plugins.components.PlayerTag
import com.game.plugins.components.Renderable
import com.game.plugins.components.RigidBody
import com.game.plugins.components.Smooth
import com.game.plugins.components.Transform
import com.game.plugins.components.Visual
import com.game.plugins.components.applyKinematicMovement
import com.game.plugins.components.cleanupOnExit
import com.game.plugins.services.CameraService
import com.game.plugins.systems.BoundarySystem
import com.game.plugins.systems.CameraSystem
import com.game.plugins.systems.CleanupSystem
import com.game.plugins.systems.PhysicsSystem
import com.game.plugins.systems.RenderSystem
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.ExperimentalTime

private data class WeaponComponent(
    val cooldown: Float = 0.3f,
    var timeUntilNextShot: Float = 0f
) : Component<WeaponComponent> { override fun type() = WeaponComponent; companion object : ComponentType<WeaponComponent>() }

// --- 2. 视觉组件 (Visuals) ---

private class PlayerVisual : Visual() { override fun DrawScope.draw() { drawCircle(Color.Blue, alpha = alpha) } }
private class EnemyVisual : Visual() { override fun DrawScope.draw() { drawRect(Color.Red, alpha = alpha) } }
private class BulletVisual(private val isPlayer: Boolean) : Visual() {
    override fun DrawScope.draw() { drawRect(if (isPlayer) Color.Black else Color.Magenta, alpha = alpha) }
}

// --- 3. 游戏系统 (Game Systems) ---

private class AircraftControlSystem(
    val input: InputManager = inject()
) : IteratingSystem(family = family { all(PlayerTag, Transform, WeaponComponent) }) {

    override fun onTickEntity(entity: Entity) {
        val transform = entity[Transform]
        val weapon = entity[WeaponComponent]

        // 移动控制 (WASD/Arrows)
        var deltaX = 0f; var deltaY = 0f
        if (input.isKeyDown(Key.W) || input.isKeyDown(Key.DirectionUp)) deltaY -= 1f
        if (input.isKeyDown(Key.S) || input.isKeyDown(Key.DirectionDown)) deltaY += 1f
        if (input.isKeyDown(Key.A) || input.isKeyDown(Key.DirectionLeft)) deltaX -= 1f
        if (input.isKeyDown(Key.D) || input.isKeyDown(Key.DirectionRight)) deltaX += 1f

        transform.applyKinematicMovement(deltaTime = deltaTime, rawDeltaX = deltaX, rawDeltaY = deltaY, speed = 200f)

        // 射击控制 (Spacebar)
        weapon.timeUntilNextShot -= deltaTime
        if (input.isKeyDown(Key.Spacebar) && weapon.timeUntilNextShot <= 0f) {
            weapon.timeUntilNextShot = weapon.cooldown

            // 创建子弹实体
            world.entity {
                +Transform(
                    position = transform.position + Offset(0f, -transform.size.height / 2f),
                    size = Size(4f, 10f)
                )
                +RigidBody(velocity = Offset(0f, -300f), drag = 0f) // 子弹高速向上
                +Boundary(onExit = cleanupOnExit())
                +PlayerBulletTag(damage = 10f)
                +Renderable(BulletVisual(true))
            }
        }
    }
}

private class EnemySpawnSystem(
    val cameraService: CameraService = inject(),
    val assets: AssetsManager = inject()
) : IntervalSystem(interval = Fixed(0.5f)) { // 每 0.5 秒生成一波敌人


    override fun onTick() {
        val worldBounds = cameraService.activeCamera?.worldBounds ?: return

        val spawnXMin = worldBounds.left
        val spawnXMax = worldBounds.right

        // Y轴的生成位置固定在世界顶边之外一点点，给敌人一个进场的空间
        // worldBounds.top 在我们的中心坐标系里是负值 (e.g., -300f)
        val spawnY = worldBounds.top - 50f // e.g., at y = -300 - 50 = -350

        // 在屏幕上方随机生成 1-3 个敌人
        repeat(Random.nextInt(1, 4)) {
            val size = Size(40f, 40f)
            world.entity {
                +Transform(
                    position = Offset(
                        // 在 [spawnXMin, spawnXMax] 这个范围内随机取一个X值
                        x = Random.nextFloat() * (spawnXMax - spawnXMin) + spawnXMin,
                        y = spawnY
                    ),
                    size = size
                )
                +RigidBody(velocity = Offset((-100f..100f).random(), 150f), drag = 0f) // 缓慢向下
                +CharacterStats(maxHp = 20f)
                +Boundary(onExit = cleanupOnExit())
                +EnemyTag
                +Renderable(EnemyVisual())
            }
        }
    }
}

private class CollisionSystem(
    val audio: AudioManager = inject()
) : IntervalSystem() {
    private val playerBulletFamily = family { all(PlayerBulletTag, Transform); none(EnemyTag) }
    private val enemyFamily = family { all(EnemyTag, Transform, CharacterStats) }
    private val playerFamily = family { all(PlayerTag, Transform, CharacterStats) }

    override fun onTick() {
        // --- 1. 玩家子弹 vs 敌人 ---
        playerBulletFamily.forEach { bullet ->
            enemyFamily.forEach { enemy ->
                val bPos = bullet[Transform].position
                val ePos = enemy[Transform].position
                val eRadius = enemy[Transform].size.width / 2f

                // 简单的圆形碰撞检测 (使用距离平方优化)
                if ((bPos - ePos).getDistanceSquared() < (eRadius * eRadius)) {
                    val stats = enemy[CharacterStats]
                    stats.hp -= bullet[PlayerBulletTag].damage
                    bullet.configure { +CleanupTag }
                }
            }
        }

        // --- 2. 敌人 vs 玩家 (为了简化，只检查敌机本体) ---
        playerFamily.forEach { player ->
            enemyFamily.forEach { enemy ->
                val pPos = player[Transform].position
                val ePos = enemy[Transform].position
                val eRadius = enemy[Transform].size.width / 2f
                val pRadius = player[Transform].size.width / 2f

                if ((pPos - ePos).getDistanceSquared() < (eRadius + pRadius).pow(2)) {
                    val stats = player[CharacterStats]
//                    stats.hp -= 100f // 敌机撞到玩家，直接扣大量血
                    enemy.configure { +CleanupTag }
                }
            }
        }
    }

}


// --- 4. 场景结构 (Scenes) ---

private data object Menu
private data object Battle

@Composable
fun AircraftWarDemo(context: PlatformContext) {
    val sceneStack = rememberGameSceneStack<Any>(Menu)
    KGame(
        context = context,
        sceneStack = sceneStack
    ) {
        scene<Menu> {
            onEnter {
                println("Enter")
            }

            onForegroundUI {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text("=== 飞机大战 Demo ===", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { sceneStack.push(Battle) }) {
                        Text("开始战斗")
                    }
                }
            }
        }

        scene<Battle> {
            world(configuration = {
                systems {
                    // 物理和渲染基础系统
                    +PhysicsSystem(gravity = Offset.Zero)
                    +CameraSystem()
                    +RenderSystem()

                    // 游戏核心系统
                    +AircraftControlSystem()
                    +EnemySpawnSystem()
                    +CollisionSystem()
                    +BoundarySystem()
                    +CleanupSystem()
                }
            }) {
                val worldBounds = Rect(-800f, -600f, 800f, 600f)

                // 1. 玩家实体
                val player = entity {
                    +Transform(size = Size(50f, 50f))
                    +PlayerTag
                    +CharacterStats(maxHp = 100f)
                    +WeaponComponent(cooldown = 0.2f)
                    +Renderable(PlayerVisual())
                }

                entity {
                    +Transform()
                    +Smooth()
                    +CameraTarget("player", player)
                    +Camera(isMain = true, worldBounds = worldBounds)
                }
            }

            // ... 资源加载和退出逻辑 ...
            onUpdate {
                if (input.isKeyUp(Key.Escape)) sceneStack.pop()
            }

            onBackgroundUI {
                ActiveRectangle(BlueSky())
            }

            onForegroundUI {
                // 简单的 HUD 显示血量和分数 (需要从 ECS Injectables 中获取状态)
                // val gameState = inject<GameState>() // 假设您有 GameState
                Text("HP: ???", modifier = Modifier.padding(16.dp))
            }
        }
    }
}