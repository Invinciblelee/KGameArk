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
import androidx.compose.ui.graphics.toArgb
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
import com.kgame.plugins.services.play
import com.kgame.plugins.systems.PhysicsSystem
import com.kgame.plugins.systems.SystemPriorityAnchors
import com.kgame.plugins.visuals.images.ImageVisual
import com.kgame.plugins.visuals.images.SpriteVisual
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticlePattern
import com.kgame.plugins.services.particles.ParticleService
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun ParticleNodeScope.explosion(origin: Offset) {

    layer("explosion_core") {
        count = 600
        duration = 1.2f
        position = vec2(origin.x, origin.y)

        // 核心层：速度上限拉高到 60，主动向外层靠拢
        velocity = random(0.0f, 60.0f)
        angle = random(0f, 360f)

        // 核心层也加入随机摩擦力，产生乱序分布
        friction = random(0.96f, 0.99f)
        gravity = scalar(0.08f)

        size = random(1.0f, 4.0f)
        // 增加中间色阶，使用暖橙色作为过渡
        color = select(Ratio(0.4f), color(1f, 1f, 1f), color(1f, 0.8f, 0.3f))
        alpha = scalar(1.0f) - progress
    }

    layer("explosion_blast") {
        count = 2400
        duration = 1.5f
        position = vec2(origin.x, origin.y)

        // 爆发层：速度下限降到 20，主动向核心层渗透
        // 此时 20~60 这个区间是两层共有的，断层消失
        velocity = random(20.0f, 250.0f)
        angle = random(0f, 360f)

        // 冲击力的关键：起步快，减速狠
        // 让摩擦力的下限更低(0.82)，产生那种“炸开即定格”的烟尘感
        friction = random(0.82f, 0.96f)
        gravity = scalar(0.12f)

        size = random(0.4f, 2.5f)
        color = select(Ratio(0.5f), color(1f, 0.5f, 0.1f), color(0.4f, 0.1f, 0.05f))

        alpha = (scalar(1.0f) - progress) * scalar(0.8f)
    }
}

fun ParticleNodeScope.sandFlow(origin: Offset) {

    // --- 图层 1：流动的极光带 (测试横向场) ---
    layer("aurora_stream") {
        count = 1000
        duration = 4.0f // 寿命长，表现流动感

        // 初始位置：在 X 轴上随机分布，形成一个宽度
        position = vec2(random(-200f, 200f) + origin.x, origin.y)

        // 速度：主轴向右 (100+)，纵轴微弱波动
        velocity = vec2(random(80f, 150f), random(-10f, 10f))

        // 阻力极小，让它保持匀速滑动
        friction = scalar(0.995f)
        gravity = scalar(0.01f) // 几乎无重力

        // 颜色测试：利用 Index 产生冷色调交替
        color = select(
            condition = Modulo(3),
            onTrue = color(0.2f, 1.0f, 0.6f, 0.8f), // 翡翠绿
            onFalse = color(0.1f, 0.4f, 1.0f, 0.6f) // 宝石蓝
        )

        // 长度感：让粒子在 X 轴拉长（如果你的 size 支持宽深比，这里效果更佳）
        size = random(2.0f, 5.0f)

        // 呼吸效果：从透明到半透明再到透明
        alpha = select(
            condition = Ratio(0.5f),
            onTrue = progress,
            onFalse = scalar(1.0f) - progress
        )
    }

    // --- 图层 2：坠落的星尘 (测试垂直场) ---
    layer("glitter_dust") {
        count = 2000
        duration = 3.0f

        // 从上方随机飘落
        position = vec2(random(-300f, 300f) + origin.x, origin.y - 100f)

        // 向下掉落的速度，带一点横向漂移
        velocity = vec2(random(-20f, 20f), random(30f, 60f))

        // 阻力稍大，模拟空气浮力
        friction = random(0.94f, 0.98f)
        gravity = scalar(0.08f)

        // 逻辑测试：10% 的粒子是极亮的白光，其余是微弱的星光
        color = select(
            condition = Ratio(0.1f),
            onTrue = color(1f, 1f, 1f, 1f),    // 亮白
            onFalse = color(1f, 0.8f, 0.4f, 0.5f) // 暖金
        )

        size = random(0.5f, 1.2f)

        // 闪烁感：高频 Mod 切换
        alpha = select(
            condition = Modulo(4),
            onTrue = scalar(1.0f) - progress,
            onFalse = scalar(0.2f)
        )
    }
}

fun ParticleNodeScope.testCollision(origin: Offset) {
    // Particle 1: Moving Right (0 degrees)
    layer("to_right") {
        count = 1
        duration = 1.0f
        position = vec2(origin.x, origin.y)
        velocity = scalar(100f)
        angle = scalar(0f)
        friction = scalar(0.95f)
        gravity = scalar(0f)
        size = scalar(10f)
        color = color(0xFFFF0000) // Red

        // Fades from 1.0 to 0.0 over 5 seconds
        alpha = scalar(1.0f) - progress
    }

    // Particle 2: Moving Left (180 degrees)
    layer("to_left") {
        count = 1
        duration = 1.0f
        position = vec2(origin.x, origin.y)
        velocity = scalar(100f)
        angle = scalar(180f)
        friction = scalar(0.95f)
        gravity = scalar(0f)
        size = scalar(10f)
        color = color(0xFF0000FF) // Blue

        // Fades from 1.0 to 0.0 over 5 seconds
        alpha = scalar(1.0f) - progress
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

                        particleService.emit(false) {
                            arcanePortal(ePos)
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