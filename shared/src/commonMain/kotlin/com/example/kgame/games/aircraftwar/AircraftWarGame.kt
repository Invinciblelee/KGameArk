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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Velocity
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
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.MaterialEffect
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.GameJoypad
import com.kgame.engine.ui.Rectangle
import com.kgame.engine.ui.applyJoypad
import com.kgame.engine.utils.FpsCalculator
import com.kgame.plugins.components.AlphaAnimation
import com.kgame.plugins.components.ScrollerAxis
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
import com.kgame.plugins.components.ScrollerTarget
import com.kgame.plugins.components.SpriteAnimation
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.Movement
import com.kgame.plugins.components.ScrollerMode
import com.kgame.plugins.components.WorldBounds
import com.kgame.plugins.components.step
import com.kgame.plugins.services.AnimationService
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleContext
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.services.play
import com.kgame.plugins.systems.SystemPriorityAnchors
import com.kgame.plugins.visuals.images.ImageVisual
import com.kgame.plugins.visuals.images.SpriteVisual
import org.intellij.lang.annotations.Language
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.ExperimentalTime

/**
 * @param baseColor The theme color of the plasma.
 */
class PlasmaFireMaterial(val baseColor: Color, val context: ParticleContext) : Material {

    @Language("AGSL")
    override val sksl: String = """
        uniform float uTime;
        uniform float uProgress;
        uniform vec4 uColor;
        
        vec4 main(vec2 uv) {
            // Transform UV to -1.0 -> 1.0 space
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);
            
            // 1. Noise calculation for dynamic shape
            float noise = sin(st.x * 10.0 + uTime * 5.0) * cos(st.y * 10.0 - uTime * 3.0);
            
            // 2. Improved strength calculation (to prevent shrinking)
            // Using a higher power curve to keep the center thick and edges crisp
            float strength = 1.0 - pow(clamp(d + noise * 0.05, 0.0, 1.0), 3.0);
            
            // 3. Life cycle progress
            float lifeFade = 1.0 - smoothstep(0.7, 1.0, uProgress); 
            
            // 4. Color Logic
            // Inner glowing white-ish core based on baseColor
            vec3 coreColor = mix(uColor.rgb, vec3(1.0, 1.0, 0.9), 0.6);
            // Blend base color to core color based on strength
            vec3 finalColor = mix(uColor.rgb, coreColor, strength);
            
            // 5. Visual dynamic effects
            float flash = sin(uTime * 15.0) * 0.1 + 0.9;
            float edgeFade = smoothstep(1.0, 0.8, d); // Soften the quad edges
            
            // 6. Alpha assembly
            // Ensure finalAlpha includes uColor.a from the DSL/Material constructor
            float finalAlpha = strength * lifeFade * edgeFade * uColor.a;
            
            // 7. Output with Premultiplied Alpha for better glowing effect
            return vec4(finalColor * flash * finalAlpha, finalAlpha);
        }
    """.trimIndent()

    override fun MaterialEffect.onSetup() {
        uniform("uColor", baseColor)
    }

    override fun MaterialEffect.onUpdate() {
        uniform("uProgress", context.progress)
    }

}

fun ParticleNodeScope.explosion(center: Offset) {
    // 1. Explosion Core: The hot, dense center
    layer("explosion_core", center) {
        config {
            count = 400
            duration = 1.0f
            material = PlasmaFireMaterial(
                baseColor = Color(red = 1.0f, green = 0.5f, blue = 0.0f),
                context = context
            )
        }

        val angle = math.random(0f, 360f)
        val rad = math.toRadians(angle)

        // Distribution: Use pow(random, 2.0) to cluster more particles near the center
        val speed = math.pow(math.random(0.0f, 1.0f), 2.0f) * 180.0f

        // Physics: Exponential decay to simulate air resistance (Fluid Drag)
        // Position = origin + velocity * (1.0 - exp(-k * t)) / k
        val dragK = 3.0f
        val resistance = (1.0f - math.exp(env.time * -dragK)) / dragK

        position = vec2(
            math.cos(rad) * speed * resistance,
            math.sin(rad) * speed * resistance + (40f * env.time * env.time) // Gravity
        )

        // Visuals: Glowing particles that shrink over time
        size = math.random(2.0f, 5.0f) * (1.0f - ops.smoothstep(0.6f, 1.0f, env.progress))
    }

    // 2. Explosion Blast: High-velocity sparks and debris
    layer("explosion_blast", center) {
        config {
            count = 800
            duration = 1.5f
            material = PlasmaFireMaterial(
                baseColor = Color(red = 1.0f, green = 0.95f, blue = 0.8f),
                context = context
            )
        }

        val angle = math.random(0f, 360f)
        val rad = math.toRadians(angle)

        // Distribution: Sqrt makes particles favor the outer edge
        val speed = math.sqrt(math.random(0.0f, 1.0f)) * 350.0f

        // Physics: Different drag for lighter debris
        val dragK = 1.5f
        val resistance = (1.0f - math.exp(env.time * -dragK)) / dragK

        position = vec2(
            math.cos(rad) * speed * resistance,
            math.sin(rad) * speed * resistance + (60f * env.time * env.time)
        )

        // Visuals: Elongated "sparks" effect by making size small
        size = math.random(0.5f, 2.5f)
    }

    // 3. Shockwave: A subtle, fast-expanding ring
    layer("shockwave", center) {
        config {
            count = 100
            duration = 0.4f
            material = PlasmaFireMaterial(
                baseColor = Color(red = 0.85f, green = 0.95f, blue = 1.0f, alpha = 0.6f),
                context = context
            )
        }

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
    }
}

private data class WeaponComponent(
    val cooldown: Float = 0.3f,
    var timeUntilNextShot: Float = 0f
) : Component<WeaponComponent> {
    override fun type() = WeaponComponent
    companion object : ComponentType<WeaponComponent>()
}

// --- 3. Game Systems ---

private class AircraftControlSystem(
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
        val movement = entity[Movement]
        val renderable = entity[Renderable]
        val weapon = entity[WeaponComponent]

        // Movement control (WASD/Arrows)
        var deltaX = 0f; var deltaY = 0f
        if (input.isKeyDown(Key.W) || input.isKeyDown(Key.DirectionUp)) deltaY -= 1f
        if (input.isKeyDown(Key.S) || input.isKeyDown(Key.DirectionDown)) deltaY += 1f
        if (input.isKeyDown(Key.A) || input.isKeyDown(Key.DirectionLeft)) deltaX -= 1f
        if (input.isKeyDown(Key.D) || input.isKeyDown(Key.DirectionRight)) deltaX += 1f

        movement.step(transform, deltaX, deltaY, deltaTime)

        // Shooting control (Spacebar)
        weapon.timeUntilNextShot -= deltaTime
        if (input.isKeyDown(Key.Spacebar) && weapon.timeUntilNextShot <= 0f) {
            weapon.timeUntilNextShot = weapon.cooldown

            // Create bullet entity
            world.entity {
                +Transform(
                    position = transform.position + Offset(0f, -renderable.size.height / 2f),
                )
                +RigidBody(velocity = Velocity(0f, -600f), drag = 0f)
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
                +RigidBody(velocity = Velocity(0f, 300f), drag = 0f) // Downward
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
) : IntervalSystem(interval = Fixed(0.5f), priority = priority) { // Spawn wave every 0.5s

    val texture = assets[GameAssets.Atlas.Texture]

    val worldBounds = Rect(-400f, -400f, 400f, 300f)

    override fun onTick(deltaTime: Float) {
        val spawnXMin = worldBounds.left
        val spawnXMax = worldBounds.right

        // Spawn position is fixed slightly outside the top edge
        val spawnY = worldBounds.top

        // Randomly spawn 1-3 enemies at the top
        repeat(Random.nextInt(1, 4)) {
            world.entity {
                +Transform(
                    position = Offset(
                        x = Random.nextFloat() * (spawnXMax - spawnXMin) + spawnXMin,
                        y = spawnY
                    ),
                )
                +RigidBody(velocity = Velocity((-100f..100f).random(), 150f), drag = 0f) // Move slowly downward
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
        // --- 1. Player Bullet vs Enemies ---
        playerBulletFamily.forEach { bullet ->
            enemyFamily.forEach { enemy ->
                val bPos = bullet[Transform].position
                val ePos = enemy[Transform].position
                val eRadius = enemy[Renderable].size.width / 2f

                // Simple circular collision check
                if ((bPos - ePos).getDistanceSquared() < (eRadius * eRadius)) {
                    val stats = enemy[CharacterStats]
                    stats.hp -= bullet[PlayerBulletTag].damage

                    if (stats.hp <= 0) {
                        bullet.configure { +CleanupTag }

                        particleService.emit {
                            explosion(ePos)
                        }
                    }
                }
            }
        }

        // --- 2. Enemy vs Player ---
        playerFamily.forEach { player ->
            enemyFamily.forEach { enemy ->
                val pPos = player[Transform].position
                val ePos = enemy[Transform].position
                val eRadius = enemy[Renderable].size.width / 2f
                val pRadius = player[Renderable].size.width / 2f

                if ((pPos - ePos).getDistanceSquared() < (eRadius + pRadius).pow(2)) {
                    animationService.play(player, "flash")
                    cameraService.director.shake(1.5f)
                    enemy.configure { +CleanupTag }
                }
            }
        }
    }

}


// --- 4. Scenes ---

private sealed interface Scenes {
    data object Menu: Scenes
    data object Battle: Scenes
}

@Composable
fun AircraftWarGame() {
    val sceneStack = rememberGameSceneStack<Any>(Scenes.Menu)
    KGame(
        sceneStack = sceneStack,
        virtualSize = Size(600f, 800f)
    ) {
        scene<Scenes.Menu> {
            onBackgroundUI {
                Rectangle(Color.White)
            }

            onForegroundUI {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text("=== Aircraft War Demo ===", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { sceneStack.push(Scenes.Battle) }) {
                        Text("Start Battle")
                    }
                }
            }
        }

        scene<Scenes.Battle> {
            onWorld {
                useDefaultSystems()

                configure {
                    systems {
                        +AircraftControlSystem(priority = SystemPriorityAnchors.Input)

                        +AircraftCollisionSystem(priority = SystemPriorityAnchors.Physics after 1)

                        +EnemySpawnSystem(priority = SystemPriorityAnchors.Logic)

                        +EnemyWeaponSystem(priority = SystemPriorityAnchors.Logic after 1)
                    }
                }

                spawn {
                    val worldBounds = Rect(-400f, -400f, 400f, 300f)

                    // 1. Player Entity
                    val player = entity {
                        +Transform()
                        +Movement(120f, 120f)
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
                        +Scroller(speed = -120f)
                        +ScrollerTarget(player)
                        +Renderable(ImageVisual(assets[GameAssets.Image.Background]))
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
                assets.load(GameAssets.Image.Background)
                assets.load(GameAssets.Atlas.Texture)
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
