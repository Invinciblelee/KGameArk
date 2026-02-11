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
import com.kgame.engine.geometry.expandToRect
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.GameJoypad
import com.kgame.engine.ui.applyJoypad
import com.kgame.engine.utils.FpsCalculator
import com.kgame.plugins.components.AlphaAnimation
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
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.services.play
import com.kgame.plugins.systems.PhysicsSystem
import com.kgame.plugins.systems.SystemPriorityAnchors
import com.kgame.plugins.visuals.images.ImageVisual
import com.kgame.plugins.visuals.images.SpriteVisual
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.ExperimentalTime

fun ParticleNodeScope.explosion(center: Offset) {
    val frame = center.expandToRect(200f)

    // 1. Explosion Core: The hot, dense center
    layer("explosion_core", 800, frame) {
        duration = 1.0f

        val angle = random(0f, 360f)
        val rad = math.toRadians(angle)

        // Distribution: Use pow(random, 2.0) to cluster more particles near the center
        val speed = math.pow(random(0.0f, 1.0f), 2.0f) * 180.0f

        // Physics: Exponential decay to simulate air resistance (Fluid Drag)
        // Position = origin + velocity * (1.0 - exp(-k * t)) / k
        val dragK = 3.0f
        val resistance = (1.0f - math.exp(env.time * -dragK)) / dragK

        position = vec2(
            math.cos(rad) * speed * resistance,
            math.sin(rad) * speed * resistance + (40f * env.time * env.time) // Gravity
        )

        // Visuals: Glowing particles that shrink over time
        size = random(2.0f, 5.0f) * (1.0f - math.smoothstep(0.6f, 1.0f, env.progress))

        // Color: Transitions from White-Hot to Orange
        val alpha = math.smoothstep(0.0f, 0.1f, env.progress) * (1.0f - math.smoothstep(0.7f, 1.0f, env.progress))
        val hotColor = color(1.0f, 1.0f, 1.0f, alpha = alpha)
        val fireColor = color(1.0f, 0.8f, 0.2f, alpha = alpha)
        color = math.mix(hotColor, fireColor, env.progress)
    }

    // 2. Explosion Blast: High-velocity sparks and debris
    layer("explosion_blast", 2500, frame) {
        duration = 1.5f

        val angle = random(0f, 360f)
        val rad = math.toRadians(angle)

        // Distribution: Sqrt makes particles favor the outer edge
        val speed = math.sqrt(random(0.0f, 1.0f)) * 350.0f

        // Physics: Different drag for lighter debris
        val dragK = 1.5f
        val resistance = (1.0f - math.exp(env.time * -dragK)) / dragK

        position = vec2(
            math.cos(rad) * speed * resistance,
            math.sin(rad) * speed * resistance + (60f * env.time * env.time)
        )

        // Visuals: Elongated "sparks" effect by making size small
        size = random(0.5f, 2.5f)

        val alpha = (1.0f - env.progress) * 0.9f

        // Color: Deep red/embers using a select for variety
        val ashColor = color(0.2f, 0.1f, 0.05f, alpha = alpha)
        val emberColor = color(1.0f, 0.3f, 0.1f, alpha = alpha)
        color = select(Ratio(0.4f), emberColor, ashColor)
    }

    // 3. Shockwave: A subtle, fast-expanding ring
    layer("shockwave", 100, frame) {
        duration = 0.4f

        val angle = (env.index / env.count) * 360f
        val rad = math.toRadians(angle)

        // Speed: Very fast expansion with high drag
        val speed = 500.0f
        val expansion = speed * (1.0f - math.exp(env.time * -8.0f))

        position = vec2(
            math.cos(rad) * expansion,
            math.sin(rad) * expansion
        )

        size = 2.0f + env.progress * 10.0f // Expanding ring dots
        color = color(1f, 1f, 1f, alpha = (1.0f - math.smoothstep(0.0f, 1.0f, env.progress)) * 0.5f)
    }
}

fun ParticleNodeScope.simpleTest(center: Offset) {
    layer("test_layer", 1, center.expandToRect(400f)) { // 先只画 1 个，排除叠加干扰
        duration = 5.0f

        size = scalar(40.0f) // 矩形的“边长”
        color = color(1.0f, 0.0f, 0.0f) // 红色
    }
}

//class ExplosionPattern(
//    private val origin: Offset,
//    private val count: Int = 4000
//) : ParticlePattern {
//
//    override fun onPopulate(buffer: ParticleBuffer) {
//        val random = Random(Clock.System.now().toEpochMilliseconds())
//
//        repeat(count) { i ->
//            val angle = random.nextFloat() * 2f * PI.toFloat()
//
//            // --- 1. 解决“中间空白”的关键：距离分层 ---
//            // p 越小越靠近圆心。通过平方根分布让更多粒子留在中心区域
//            val p = random.nextFloat().let { it * it }
//
//            // --- 2. 动态速度曲线 ---
//            // 核心区的粒子给极低的速度，边缘的给极高初速
//            val baseSpeed = if (i < count * 0.4f) {
//                random.nextFloat() * 5f // 慢速填充中心
//            } else {
//                20f + random.nextFloat() * 45f // 高速向外炸
//            }
//
//            val vx = cos(angle.toDouble()).toFloat() * baseSpeed
//            val vy = sin(angle.toDouble()).toFloat() * baseSpeed
//
//            // --- 3. 异步出生（伪Shader效果） ---
//            // 给 life 加一个负偏置，让一部分粒子稍晚一点才显现（或消失得更早）
//            val lifeOffset = random.nextFloat() * 0.4f
//            val life = (0.7f + random.nextFloat() * 0.8f) - lifeOffset
//
//            // --- 4. 摩擦力梯度 ---
//            // 外部粒子摩擦力极大（迅速定格），内部粒子摩擦力小一点（保持微弱游走）
//            val friction = if (i < count * 0.4f) 0.96f else 0.72f + random.nextFloat() * 0.05f
//
//            // --- 5. 颜色与缩放的非线性叠加 ---
//            val isSpark = i % 10 == 0 // 每10个粒子出一个高亮的亮斑
//            val size = when {
//                i < count * 0.2f -> 2.5f + random.nextFloat() * 2f // 中心大火球
//                isSpark -> 0.8f + random.nextFloat() * 2f // 飞溅的亮星
//                else -> 0.4f + random.nextFloat() * 0.8f // 基础烟尘
//            }
//
//            val color = when {
//                i < count * 0.15f -> Color(1f, 1f, 1f, 1f)      // 纯白核心
//                i < count * 0.4f -> Color(1f, 0.95f, 0.6f, 1f)  // 暖白填充
//                i < count * 0.7f -> Color(1f, 0.6f, 0.1f, 1f)   // 橙黄主色
//                else -> Color(0.6f, 0.1f, 0.05f, 1f)           // 暗红边缘
//            }
//
//            buffer.putQuad(
//                position = origin,
//                velocity = Offset(vx, vy),
//                life = life,
//                friction = friction,
//                gravity = 0.05f, // 微弱重力让定格后的粒子缓缓下沉，增加厚重感
//                size = size,
//                color = color.toArgb()
//            )
//        }
//    }
//}


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
    val particleService: ParticleService = inject(),
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

                        particleService.emit(true) {
                            explosion(ePos)
                        }
//                        world.entity {
//                            +Transform(position = ePos)
//                            +SpriteAnimation(name = "boom", loop = false)
//                            +AutoCleanupTag
//                            +Renderable(
//                                SpriteVisual(
//                                    atlas = texture,
//                                    name = "stormplane_boom_1.png",
//                                    size = Size(60f, 60f)
//                                )
//                            )
//                        }
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

private sealed interface Scenes {
    data object Menu: Scenes
    data object Battle: Scenes
}

@Composable
fun GameAircraftWarDemo() {
    val sceneStack = rememberGameSceneStack<Any>(Scenes.Menu)
    KGame(sceneStack = sceneStack) {
        scene<Scenes.Menu> {
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
                    Button(onClick = { sceneStack.push(Scenes.Battle) }) {
                        Text("开始战斗")
                    }
                }
            }
        }

        scene<Scenes.Battle> {
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