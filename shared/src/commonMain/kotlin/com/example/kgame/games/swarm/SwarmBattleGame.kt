@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package com.example.kgame.games.swarm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgame.ecs.*
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.KGame
import com.kgame.engine.core.rememberGameSceneStack
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.MaterialEffect
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.Rectangle
import com.kgame.plugins.components.*
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleContext
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.visuals.shapes.CircleVisual
import org.intellij.lang.annotations.Language
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

// --- 2. Components & State ---

private data object AllyTag : EntityTag()
private data object EnemyTag : EntityTag()

/**
 * Using internal var for angle as a pragmatic state management approach.
 */
private class OrbitPersonality(
    val radius: Float = (40f..160f).random(),
    val speed: Float = (1.5f..4f).random(),
    var angle: Float = (0f..360f).random()
) : Component<OrbitPersonality> {
    override fun type() = OrbitPersonality
    companion object : ComponentType<OrbitPersonality>()
}

class SwarmState {
    var score by mutableIntStateOf(0)
    var units by mutableIntStateOf(0)
    var isGameOver by mutableStateOf(false)
    var pendingReset by mutableStateOf(false)
    var chargePower by mutableStateOf(0f)
}

// --- 3. Shaders & Visuals ---
class ShockwaveMaterial(val baseColor: Color, val context: ParticleContext) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uProgress;
        uniform vec4 uColor;
        
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);
            float thickness = max(0.01, 0.1 * (1.0 - uProgress));
            float distDiff = abs(d - 0.9);
            float ring = 1.0 - smoothstep(0.0, thickness, distDiff);
            float fade = pow(1.0 - uProgress, 2.0);
            float alpha = ring * fade * uColor.a;
            return vec4(uColor.rgb * alpha, alpha);
        }
    """.trimIndent()

    override fun MaterialEffect.onSetup() {
        uniform("uColor", baseColor)
    }

    override fun MaterialEffect.onUpdate() {
        uniform("uProgress", context.progress)
    }
}

// --- 4. Refactored Systems ---

private class SwarmSystem(
    private val state: SwarmState = inject(),
    private val input: InputManager = inject(),
    private val camera: CameraService = inject(),
    private val particle: ParticleService = inject()
) : IntervalSystem() {

    private val allies = world.family { all(AllyTag, Transform, OrbitPersonality) }
    private val enemies = world.family { all(EnemyTag, Transform) }

    override fun onTick(deltaTime: Float) {
        if (state.pendingReset) {
            performReset()
            state.pendingReset = false
            return
        }

        if (state.isGameOver) return

        val targetPos = camera.transformer.virtualToWorld(input.getPointerPosition())
        val isPressing = input.isPointerDown

        // 1. Player Charge Logic
        if (isPressing) {
            state.chargePower = (state.chargePower + deltaTime * 1.5f).coerceAtMost(1f)
        } else {
            if (state.chargePower > 0.2f) triggerBurst(targetPos)
            state.chargePower = 0f
        }

        // 2. Swarm Movement Logic
        allies.forEach { ally ->
            val orbit = ally[OrbitPersonality]
            orbit.angle += orbit.speed * deltaTime * (1f + state.chargePower * 2f)
            val currentRadius = if (isPressing) 20f + (1f - state.chargePower) * orbit.radius else orbit.radius
            val orbitOffset = Offset(cos(orbit.angle), sin(orbit.angle)) * currentRadius
            ally.configure {
                val target = addIfAbsent(ArriveTarget) { ArriveTarget(Offset.Zero) }
                target.assign(targetPos + orbitOffset)
            }
        }

        state.units = allies.entitySize

        // 3. Collision Logic with Safety Return
        enemies.forEach { enemy ->
            val eTransform = enemy[Transform]
            val ePos = eTransform.position
            enemy.configure {
                val target = addIfAbsent(ArriveTarget) { ArriveTarget(Offset.Zero) }
                target.assign(targetPos)
            }

            allies.forEach { ally ->
                if (eTransform.distanceTo(ally[Transform]) < 25f) {
                    particle.emit { boom(ePos, Color.Red) }
                    camera.director.shake(0.08f)
                    enemy.remove()
                    ally.remove()
                    state.score += 5
                    return@forEach // Prevent "ghost" collisions in the same frame
                }
            }
        }

        if (state.units <= 0 && !state.isGameOver) state.isGameOver = true
    }

    private fun triggerBurst(center: Offset) {
        camera.director.shake(0.8f)
        particle.emit {
            layer("shock", center) {
                config { duration = 0.5f; material = ShockwaveMaterial(Color.Cyan, context) }
                size = scalar(10f) + env.progress * 700f
            }
        }
        enemies.forEach { enemy ->
            if (enemy[Transform].distanceTo(center) < 350f) {
                particle.emit { boom(enemy[Transform].position, Color.Red) }
                enemy.remove()
                state.score += 25
            }
        }
    }

    private fun performReset() {
        allies.forEach { it.remove() }
        enemies.forEach { it.remove() }
        repeat(150) {
            world.entity {
                +AllyTag
                +OrbitPersonality()
                +Transform(Offset(400f, 400f))
                +RigidBody(drag = 1.4f, maxSpeed = 800f)
                +Arriver(speed = 600f, slowDownRadius = 120f)
                +Renderable(CircleVisual(10f, Color.Cyan), zIndex = 10)
            }
        }
        state.isGameOver = false
        state.score = 0
    }
}

private class EnemySpawnSystem(
    private val state: SwarmState = inject()
) : IntervalSystem(interval = Fixed(0.6f)) {

    private val enemyFamily = world.family { all(EnemyTag) }

    override fun onTick(deltaTime: Float) {
        if (state.isGameOver) return

        // 1. Strictly limit spawning in a single Tick to prevent IntervalSystem cumulative bursts
        val currentCount = enemyFamily.entitySize
        val maxEnemies = 100

        if (currentCount < maxEnemies) {
            // 2. Bound spawn range to [50, 750] within the 800x800 arena
            val padding = 50f
            val spawnX: Float
            val spawnY: Float

            // Randomly choose a side to spawn from
            when ((0..3).random()) {
                0 -> { // Top edge
                    spawnX = (padding..800f - padding).random()
                    spawnY = padding
                }
                1 -> { // Bottom edge
                    spawnX = (padding..800f - padding).random()
                    spawnY = 800f - padding
                }
                2 -> { // Left edge
                    spawnX = padding
                    spawnY = (padding..800f - padding).random()
                }
                else -> { // Right edge
                    spawnX = 800f - padding
                    spawnY = (padding..800f - padding).random()
                }
            }

            world.entity {
                +EnemyTag
                +Transform(Offset(spawnX, spawnY))
                +RigidBody(drag = 0.7f, maxSpeed = 220f)
                +Arriver(speed = 190f, slowDownRadius = 40f)
                +Renderable(CircleVisual(28f, Color.Red))
            }
        }
    }
}

private fun ParticleNodeScope.boom(pos: Offset, col: Color) {
    layer("bits", pos) {
        config { count = 50; duration = 0.5f }

        val angle = math.random(0f, 360f)
        val rad = math.toRadians(angle)
        val dist = math.random(50f, 250f)

        // 1. Burst feeling optimization: using cubic curve (1 - (1-p)^3) for fast blast and slow halt
        val easeOut = 1f - math.pow(1f - env.progress, 3f)

        // 2. Path jitter: subtle wave based on time for more vivid explosion
        val noise = math.sin(env.progress * 10f + angle) * 5f * (1f - env.progress)

        position = vec2(
            math.cos(rad) * dist * easeOut + noise,
            math.sin(rad) * dist * easeOut + noise
        )

        // Leverage operator overloading for scalar and node operations
        size = 8f * math.pow(1f - env.progress, 2f)

        // Color flash effect
        val flash = math.pow(1f - env.progress, 5f)
        color = color(
            col.red + flash,
            col.green + flash,
            col.blue + flash,
            1f - env.progress
        )
    }
}

// --- 5. Entry Point ---



@Composable
fun SwarmBattleGame() {
    val sceneStack = rememberGameSceneStack("play")
    val state = remember { SwarmState() }

    KGame(sceneStack = sceneStack, virtualSize = Size(800f, 800f)) {
        scene("play") {
            onWorld {
                useDefaultSystems()
                configure {
                    injectables { +state }
                    systems {
                        +SwarmSystem()
                        +EnemySpawnSystem()
                    }
                }
                spawn {
                    entity {
                        +Transform()
                        +Camera("main", isMain = true)
                        +CameraShake()
                    }
                    state.pendingReset = true
                }
            }

            onBackgroundUI { Rectangle(Color(0xFF030508)) }

            onForegroundUI {
                Box(Modifier.fillMaxSize().padding(24.dp)) {
                    Column {
                        Text("NEON SWARM", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("SCORE: ${state.score}", color = Color.Cyan, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.chargePower },
                            modifier = Modifier.width(140.dp).height(6.dp),
                            color = Color.Yellow,
                            trackColor = Color.White.copy(0.1f)
                        )
                    }

                    if (state.isGameOver) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.Black.copy(0.8f),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CORE CRITICAL", color = Color.Red, fontSize = 32.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(20.dp))
                                Button(
                                    onClick = { state.pendingReset = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black)
                                ) {
                                    Text("REBOOT SWARM", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
