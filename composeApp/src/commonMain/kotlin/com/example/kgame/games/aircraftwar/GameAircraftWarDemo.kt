@file:OptIn(ExperimentalTime::class)

package com.example.kgame.games.aircraftwar

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import com.example.kgame.games.GameAssets
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.ecs.Entity
import com.kgame.ecs.Fixed
import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.asset.AssetsManager
import com.kgame.engine.audio.AudioManager
import com.kgame.engine.core.KGame
import com.kgame.engine.core.rememberGameSceneStack
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.GameJoypad
import com.kgame.engine.ui.applyJoypad
import com.kgame.plugins.components.Axis
import com.kgame.plugins.components.Boundary
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.CameraTarget
import com.kgame.plugins.components.CharacterStats
import com.kgame.plugins.components.CleanupTag
import com.kgame.plugins.components.EnemyBulletTag
import com.kgame.plugins.components.EnemyTag
import com.kgame.plugins.components.ImageVisual
import com.kgame.plugins.components.PlayerBulletTag
import com.kgame.plugins.components.PlayerTag
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.RigidBody
import com.kgame.plugins.components.Scroller
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.Visual
import com.kgame.plugins.components.WorldBounds
import com.kgame.plugins.components.applyKinematicMovement
import com.kgame.plugins.components.cleanupOnExit
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.systems.BoundarySystem
import com.kgame.plugins.systems.CameraSystem
import com.kgame.plugins.systems.CleanupSystem
import com.kgame.plugins.systems.PhysicsSystem
import com.kgame.plugins.systems.RenderSystem
import com.kgame.plugins.systems.ScrollerDriveSystem
import com.kgame.plugins.systems.ScrollerRenderSystem
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.ExperimentalTime

private data class WeaponComponent(
    val cooldown: Float = 0.3f,
    var timeUntilNextShot: Float = 0f
) : Component<WeaponComponent> { override fun type() = WeaponComponent; companion object : ComponentType<WeaponComponent>() }

// --- 2. 视觉组件 (Visuals) ---

private class PlayerVisual() : Visual(60f) {
    override fun DrawScope.draw() { drawCircle(Color.Blue, alpha = alpha) }
}
private class EnemyVisual() : Visual(40f) {
    override fun DrawScope.draw() { drawCircle(Color.Red, alpha = alpha) }
}
private class BulletVisual(isPlayer: Boolean) : Visual(10f, 15f) {
    private val brush = if (isPlayer) PlayerBrush else EnemyBrush

    override fun DrawScope.draw() {
        drawOval(brush = brush, alpha = alpha)
    }

    private companion object {
        val PlayerBrush = Brush.verticalGradient(
            0.0f to Color.Blue.copy(alpha = 0.0f),
            0.5f to Color.Blue.copy(alpha = 0.6f),
            1.0f to Color.White.copy(alpha = 0.9f)
        )

        val EnemyBrush = Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = 0.9f),
            0.5f to Color.Red.copy(alpha = 0.6f),
            1.0f to Color.Red.copy(alpha = 0.0f)
        )
    }

}



// --- 3. 游戏系统 (Game Systems) ---

private class AircraftControlSystem(
    val cameraService: CameraService = inject(),
    val input: InputManager = inject()
) : IteratingSystem(family = family { all(PlayerTag, Transform, Renderable, WeaponComponent) }) {

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val transform = entity[Transform]
        val renderable = entity[Renderable]
        val weapon = entity[WeaponComponent]

        // 移动控制 (WASD/Arrows)
        var deltaX = 0f; var deltaY = 0f
        if (input.isKeyDown(Key.W) || input.isKeyDown(Key.DirectionUp)) deltaY -= 1f
        if (input.isKeyDown(Key.S) || input.isKeyDown(Key.DirectionDown)) deltaY += 1f
        if (input.isKeyDown(Key.A) || input.isKeyDown(Key.DirectionLeft)) deltaX -= 1f
        if (input.isKeyDown(Key.D) || input.isKeyDown(Key.DirectionRight)) deltaX += 1f

        transform.applyKinematicMovement(deltaTime = deltaTime, rawDeltaX = deltaX, rawDeltaY = deltaY, speed = 200f)
        transform.position = cameraService.transformer.clampToBounds(transform.position)

        // 射击控制 (Spacebar)
        weapon.timeUntilNextShot -= deltaTime
        if (input.isKeyDown(Key.Spacebar) && weapon.timeUntilNextShot <= 0f) {
            weapon.timeUntilNextShot = weapon.cooldown

            // 创建子弹实体
            world.entity {
                +Transform(
                    position = transform.position + Offset(0f, -renderable.size.height / 2f),
                )
                +RigidBody(velocity = Offset(0f, -600f), drag = 0f)
                +Boundary(onExit = cleanupOnExit())
                +PlayerBulletTag(damage = 10f)
                +Renderable(BulletVisual(true), zIndex = -1)
            }
        }
    }
}

private class EnemyWeaponSystem : IteratingSystem(
    family = family { all(EnemyTag, Transform, WeaponComponent) }
) {
    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val weapon = entity[WeaponComponent]
        weapon.timeUntilNextShot -= deltaTime
        if (weapon.timeUntilNextShot <= 0f) {
            weapon.timeUntilNextShot = weapon.cooldown

            val transform = entity[Transform]
            world.entity {
                +Transform(
                    position = transform.position,
                )
                +RigidBody(velocity = Offset(0f, 300f), drag = 0f) // 向下
                +Boundary(onExit = cleanupOnExit())
                +EnemyBulletTag(damage = 15f)
                +Renderable(BulletVisual(false), zIndex = -1)
            }
        }
    }
}

private class EnemySpawnSystem(
    val cameraService: CameraService = inject(),
    val assets: AssetsManager = inject()
) : IntervalSystem(interval = Fixed(0.5f)) { // 每 0.5 秒生成一波敌人

    private val worldBounds = MutableRect(Offset.Zero, Size.Zero)

    override fun onTick(deltaTime: Float) {

        val spawnXMin = worldBounds.left
        val spawnXMax = worldBounds.right

        // Y轴的生成位置固定在世界顶边之外一点点，给敌人一个进场的空间
        // worldBounds.top 在我们的中心坐标系里是负值 (e.g., -300f)
        val spawnY = worldBounds.top - 50f // e.g., at y = -300 - 50 = -350

        // 在屏幕上方随机生成 1-3 个敌人
        repeat(Random.nextInt(1, 4)) {
            world.entity {
                +Transform(
                    position = Offset(
                        // 在 [spawnXMin, spawnXMax] 这个范围内随机取一个X值
                        x = Random.nextFloat() * (spawnXMax - spawnXMin) + spawnXMin,
                        y = spawnY
                    ),
                )
                +RigidBody(velocity = Offset((-100f..100f).random(), 150f), drag = 0f) // 缓慢向下
                +CharacterStats(maxHp = 20f)
                +Boundary(onExit = cleanupOnExit())
                +WeaponComponent(cooldown = 0.5f)
                +EnemyTag
                +Renderable(EnemyVisual())
            }
        }
    }
}

private class CollisionSystem(
    val cameraService: CameraService = inject(),
    val audio: AudioManager = inject()
) : IntervalSystem() {
    private val playerBulletFamily = family { all(PlayerBulletTag, Transform); none(EnemyTag) }
    private val enemyFamily = family { all(EnemyTag, Transform, CharacterStats) }
    private val playerFamily = family { all(PlayerTag, Transform, CharacterStats) }

    override fun onTick(deltaTime: Float) {
        // --- 1. 玩家子弹 vs 敌人 ---
        playerBulletFamily.forEach { bullet ->
            enemyFamily.forEach { enemy ->
                val bPos = bullet[Transform].position
                val ePos = enemy[Transform].position
                val eRadius = enemy[Renderable].size.width / 2f

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
                val eRadius = enemy[Renderable].size.width / 2f
                val pRadius = player[Renderable].size.width / 2f

                if ((pPos - ePos).getDistanceSquared() < (eRadius + pRadius).pow(2)) {
                    val stats = player[CharacterStats]
//                    stats.hp -= 100f // 敌机撞到玩家，直接扣大量血
                    cameraService.director.shake(1f)
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
fun GameAircraftWarDemo() {
    val sceneStack = rememberGameSceneStack<Any>(Menu)
    KGame(sceneStack = sceneStack) {
        scene<Menu> {
            onStart {
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
            world {
                configure {
                    systems {
                        // 1. 先收集玩家输入
                        +AircraftControlSystem()

                        // 2. 物理/边界结算（真正改变 Transform）
                        +PhysicsSystem(gravity = Offset.Zero)
                        +BoundarySystem()

                        // 3. 游戏逻辑
                        +EnemySpawnSystem()
                        +EnemyWeaponSystem()
                        +CollisionSystem()
                        +CleanupSystem()

                        // 4. 摄像机必须在“所有位移”完成之后
                        +CameraSystem()

                        +ScrollerDriveSystem()
                        +ScrollerRenderSystem()

                        // 5. 最后才画
                        +RenderSystem()
                    }
                }

                spawn {
                    val worldBounds = Rect(-400f, -400f, 400f, 300f)

                    entity {
                        val image = assets[GameAssets.Image.Background]
                        +Transform()
                        +Scroller(speed = -120f, axis = Axis.Y)
                        +Renderable(ImageVisual(image), zIndex = -100)
                    }

                    // 1. 玩家实体
                    val player = entity {
                        +Transform()
                        +PlayerTag
                        +CharacterStats(maxHp = 100f)
                        +WeaponComponent(cooldown = 0.2f)
                        +Renderable(PlayerVisual())
                    }

                    entity {
                        +Transform()
                        +WorldBounds(worldBounds)
                        +CameraTarget(player)
                        +CameraShake()
                        +Camera("player", isMain = true, isTracking = false)
                    }
                }
            }

            onCreate {
                load(GameAssets.Image.Background)
            }

            onUpdate {
                if (input.isKeyJustPressed(Key.Escape)) sceneStack.pop()
            }

            onForegroundUI {
                GameJoypad(onValue = input::applyJoypad)


                // 简单的 HUD 显示血量和分数 (需要从 ECS Injectables 中获取状态)
                // val gameState = inject<GameState>() // 假设您有 GameState
                Text("HP: ???", modifier = Modifier.padding(16.dp))
            }
        }
    }
}