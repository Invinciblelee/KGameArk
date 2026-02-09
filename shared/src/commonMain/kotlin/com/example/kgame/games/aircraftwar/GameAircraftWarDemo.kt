@file:OptIn(ExperimentalTime::class)

package com.example.kgame.games.aircraftwar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import com.example.kgame.games.GameAssets
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.ecs.Entity
import com.kgame.ecs.Fixed
import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.asset.AssetsManager
import com.kgame.engine.audio.AudioManager
import com.kgame.engine.core.KGame
import com.kgame.engine.core.rememberGameSceneStack
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.radians
import com.kgame.engine.math.random
import com.kgame.engine.ui.GameJoypad
import com.kgame.engine.ui.applyJoypad
import com.kgame.engine.utils.FpsCalculator
import com.kgame.plugins.components.AlphaAnimation
import com.kgame.plugins.components.AutoCleanupTag
import com.kgame.plugins.components.Axis
import com.kgame.plugins.components.Boundary
import com.kgame.plugins.components.BoundaryStrategy
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.CameraTarget
import com.kgame.plugins.components.CharacterStats
import com.kgame.plugins.components.CleanupTag
import com.kgame.plugins.components.EnemyBulletTag
import com.kgame.plugins.components.EnemyTag
import com.kgame.plugins.components.InfiniteRepeatable
import com.kgame.plugins.components.PlayerBulletTag
import com.kgame.plugins.components.PlayerTag
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.RigidBody
import com.kgame.plugins.components.Scroller
import com.kgame.plugins.components.SpriteAnimation
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.WorldBounds
import com.kgame.plugins.components.applyKinematicMovement
import com.kgame.plugins.services.AnimationService
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.play
import com.kgame.plugins.systems.PhysicsSystem
import com.kgame.plugins.systems.SystemPriorityAnchors
import com.kgame.plugins.visuals.images.ImageVisual
import com.kgame.plugins.visuals.images.SpriteVisual
import com.kgame.plugins.visuals.particles.ParticleBuffer
import com.kgame.plugins.visuals.particles.ParticlePattern
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.ExperimentalTime

/**
 * A test pattern that mixes Quads and Triangles to simulate an explosion.
 */
class ExplosionPattern(private val origin: Offset) : ParticlePattern {
    override fun onPopulate(count: Int, buffer: ParticleBuffer) {
        // 使用传入的 origin 替代硬编码的 400f, 300f
        val centerX = origin.x
        val centerY = origin.y

        repeat(count) { i ->
            val angle = Random.nextFloat() * 360f
            val speed = Random.nextFloat() * 8f + 2f

            // 保持你原汁原味的数学写法，不乱改
            val vx = cos(radians(angle.toDouble())).toFloat() * speed
            val vy = sin(radians(angle.toDouble())).toFloat() * speed

            val velocity = Offset(vx, vy)
            val life = Random.nextFloat() * 1.5f + 0.5f

            if (i % 5 == 0) {
                // Every 5th particle is a "Core" Quad
                buffer.putQuad(
                    position = Offset(centerX, centerY),
                    velocity = velocity,
                    life = life,
                    width = 12f,
                    height = 12f,
                    friction = 0.96f, // Air resistance
                    gravity = 0.1f,   // Slight gravity pull
                    color = Color.Yellow
                )
            } else {
                // The rest are "Spark" Triangles
                val p = Offset(centerX, centerY)
                buffer.putTriangle(
                    p1 = p,
                    p2 = p + Offset(-4f, 8f),
                    p3 = p + Offset(4f, 8f),
                    velocity = velocity * 1.2f, // Sparks fly faster
                    life = life * 0.7f,
                    friction = 0.98f,
                    color = Color.Red
                )
            }
        }
    }
}

private data class WeaponComponent(
    val cooldown: Float = 0.3f,
    var timeUntilNextShot: Float = 0f
) : Component<WeaponComponent> {
    override fun type() = WeaponComponent
    companion object : ComponentType<WeaponComponent>()
}

// --- 3. 游戏系统 (Game Systems) ---

private class AircraftControlSystem(
    val cameraService: CameraService = inject(),
    val input: InputManager = inject(),
    val assets: AssetsManager = inject(),
    priority: SystemPriority
) : IteratingSystem(
    family = family { all(PlayerTag, Transform, Renderable, WeaponComponent) },
    priority = priority
) {
    val texture = assets[GameAssets.Atlas.Texture]

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
                +Boundary(strategy = BoundaryStrategy.Cleanup)
                +PlayerBulletTag(damage = 10f)
                +Renderable(SpriteVisual(atlas = texture, name = "stormplane_bullet_hero.png"), zIndex = -1)
            }
        }
    }
}

private class EnemyWeaponSystem(
    assets: AssetsManager = inject(),
    priority: SystemPriority
) : IteratingSystem(
    family = family { all(EnemyTag, Transform, WeaponComponent) },
    priority = priority
) {
    val texture = assets[GameAssets.Atlas.Texture]

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
                +Boundary(strategy = BoundaryStrategy.Cleanup)
                +EnemyBulletTag(damage = 15f)
                +Renderable(SpriteVisual(atlas = texture, name = "stormplane_bullet_elite.png"), zIndex = -1)
            }
        }
    }
}

private class EnemySpawnSystem(
    assets: AssetsManager = inject(),
    priority: SystemPriority,
) : IntervalSystem(interval = Fixed(0.5f), priority = priority) { // 每 0.5 秒生成一波敌人

    val texture = assets[GameAssets.Atlas.Texture]

    val worldBounds = Rect(-400f, -400f, 400f, 300f)

    override fun onTick(deltaTime: Float) {
        val spawnXMin = worldBounds.left
        val spawnXMax = worldBounds.right

        // Y轴的生成位置固定在世界顶边之外一点点，给敌人一个进场的空间
        // worldBounds.top 在我们的中心坐标系里是负值 (e.g., -300f)
        val spawnY = worldBounds.top // e.g., at y = -300 - 50 = -350

        // 在屏幕上方随机生成 1-3 个敌人
        repeat(Random.nextInt(1, 4)) {
            world.entity {
                +Transform(
                    position = Offset(
                        x = Random.nextFloat() * (spawnXMax - spawnXMin) + spawnXMin,
                        y = spawnY
                    ),
                )
                +RigidBody(velocity = Offset((-100f..100f).random(), 150f), drag = 0f) // 缓慢向下
                +CharacterStats(maxHp = 20f)
                +WorldBounds(worldBounds)
                +Boundary(strategy = BoundaryStrategy.Cleanup)
                +WeaponComponent(cooldown = 0.5f)
                +EnemyTag
                val name = if (Random.nextFloat() > 0.8) "stormplane_mob.png" else "stormplane_elite.png"
                +Renderable(SpriteVisual(atlas = texture, name = name, size = Size(60f, 60f)))
            }
        }
    }
}

private class AircraftCollisionSystem(
    val cameraService: CameraService = inject(),
    val animationService: AnimationService = inject(),
    audio: AudioManager = inject(),
    assets: AssetsManager = inject(),
    priority: SystemPriority
) : IntervalSystem(priority = priority) {
    private val playerBulletFamily = family { all(PlayerBulletTag, Transform); none(EnemyTag) }
    private val enemyFamily = family { all(EnemyTag, Transform, CharacterStats) }
    private val playerFamily = family { all(PlayerTag, Transform, CharacterStats) }

    private val texture = assets[GameAssets.Atlas.Texture]

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

                    if (stats.hp <= 0) {
                        bullet.configure { +CleanupTag }
                        world.entity {
                            +Transform(position = ePos)
                            +SpriteAnimation(name = "boom", loop = false)
                            +AutoCleanupTag
                            +Renderable(
                                SpriteVisual(
                                    atlas = texture,
                                    name = "stormplane_boom_1.png",
                                    size = Size(60f, 60f)
                                )
                            )
                        }
                    }
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

                    animationService.play(player, "flash")

                    cameraService.director.shake(0.5f)
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
                useDefaultSystems()

                configure {
                    systems {
                        +PhysicsSystem(gravity = Offset.Zero)

                        +AircraftControlSystem(priority = SystemPriorityAnchors.Input)

                        +AircraftCollisionSystem(priority = SystemPriorityAnchors.Physics after 1)

                        +EnemySpawnSystem(priority = SystemPriorityAnchors.Logic)

                        +EnemyWeaponSystem(priority = SystemPriorityAnchors.Logic after 1)
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
                        +SpriteAnimation(name = "hero")
                        +AlphaAnimation(name = "flash", 1.0f, to = 0.0f, spec = InfiniteRepeatable(repeatMode = RepeatMode.Restart, iterations = 4))
                        +WorldBounds(worldBounds)
                        +Boundary(margin = 0f, strategy = BoundaryStrategy.Clamp)
                        +Renderable(
                            SpriteVisual(
                                atlas = assets[GameAssets.Atlas.Texture],
                                name = "stormplane_hero1.png",
                                size = Size(80f, 80f)
                            )
                        )
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
                load(GameAssets.Atlas.Texture)
            }

            onUpdate {
                if (input.isKeyJustPressed(Key.Escape)) sceneStack.pop()
            }

            val fpsCalculator = FpsCalculator()

            onRender { fpsCalculator.advanceFrame() }

            onForegroundUI {
                GameJoypad(onValue = input::applyJoypad)


                Text("FPS: ${fpsCalculator.fps}", modifier = Modifier.padding(16.dp))
            }
        }
    }
}