package com.example.kgame.games.singularity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.ecs.EntityTag
import com.kgame.ecs.Fixed
import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.KSimpleGame
import com.kgame.engine.geometry.normalized
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.MaterialEffect
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.radians
import com.kgame.engine.math.random
import com.kgame.engine.ui.Rectangle
import com.kgame.plugins.components.ArriveTarget
import com.kgame.plugins.components.Arriver
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.RigidBody
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.Wander
import com.kgame.plugins.components.applyForce
import com.kgame.plugins.components.distanceTo
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleContext
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.visuals.Visual
import com.kgame.plugins.visuals.shapes.CircleVisual
import org.intellij.lang.annotations.Language
import kotlin.math.cos
import kotlin.math.sin

// --- 1. Components & State ---

/** Tag for enemy shards */
private data object ShardTag : EntityTag()

/** Core player component: manages gravity logic and energy state */
private class Singularity(
    var radius: Float = 60f,
    var pullForce: Float = 800f,
    var pulseEnergy: Float = 0f
) : Component<Singularity> {
    override fun type() = Singularity
    companion object : ComponentType<Singularity>()
}

/** Individual state for shards, including their neon trail color */
private class ShardState(
    var isBeingPulled: Boolean = false,
    val color: Color = Color.random()
) : Component<ShardState> {
    override fun type() = ShardState
    companion object : ComponentType<ShardState>()
}

/** Shared reactive state for UI and Systems communication */
class GameState {
    var score by mutableIntStateOf(0)
    var singularityHealth by mutableFloatStateOf(1f)
    var isGameOver by mutableStateOf(false)
}

// --- 2. Shaders & Visuals ---

/** Shockwave effect: Expands a glowing ring using AGSL */
class ShockwaveShader(val context: ParticleContext) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uProgress;
        uniform vec4 uColor;
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);
            // Create a ring that thins out as it expands
            float ring = smoothstep(uProgress - 0.1, uProgress, d) - smoothstep(uProgress, uProgress + 0.1, d);
            float alpha = ring * (1.0 - uProgress);
            return vec4(uColor.rgb * alpha, alpha);
        }
    """.trimIndent()
    override fun MaterialEffect.onSetup() { uniform(Material.COLOR, Color.White) }
    override fun MaterialEffect.onUpdate() { uniform(Material.PROGRESS, context.progress) }
}

/** Intake effect: Glowing blue streaks converging to the center */
class IntakeMaterial(val context: ParticleContext) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uProgress;
        vec4 main(vec2 uv) {
            float d = length(uv - 0.5) * 2.0;
            // Exponential falloff for soft neon glow
            float mask = exp(-d * 5.0);
            vec3 color = mix(vec3(0.0, 0.5, 1.0), vec3(0.0, 1.0, 1.0), uProgress);
            return vec4(color * mask * uProgress, mask * uProgress);
        }
    """.trimIndent()
    override fun MaterialEffect.onUpdate() { uniform(Material.PROGRESS, context.progress) }
}

/** Debris effect: Sharp geometric fragments with high-energy initial flash */
class DebrisMaterial(val baseColor: Color, val context: ParticleContext) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uProgress;
        uniform vec4 uColor;
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            // Box distance field for geometric shard look
            float d = max(abs(st.x), abs(st.y)); 
            float mask = 1.0 - step(0.5, d);
            // Intense white flash at birth
            float flash = pow(1.0 - uProgress, 8.0);
            vec3 color = mix(uColor.rgb, vec3(1.0), flash);
            return vec4(color * mask * (1.0 - uProgress), mask * (1.0 - uProgress));
        }
    """.trimIndent()
    override fun MaterialEffect.onSetup() { uniform(Material.COLOR, baseColor) }
    override fun MaterialEffect.onUpdate() { uniform(Material.PROGRESS, context.progress) }
}

/** Custom visual for the Singularity: Reactive core and glowing rings */
private class SingularityVisual(val sing: Singularity) : Visual(160f) {
    override fun DrawScope.draw() {
        val p = sing.pulseEnergy
        val r = sing.radius
        // Dense dark core
        drawCircle(Color(0xFF080015), radius = r)
        // Primary resonance ring
        drawCircle(
            color = Color.Cyan.copy(alpha = 0.4f + p * 0.4f),
            radius = r + 5f + p * 20f,
            style = Stroke(width = 4f + p * 10f)
        )
        // High-energy overflow ring
        if (p > 0.5f) {
            drawCircle(
                color = Color.Magenta.copy(alpha = (p - 0.5f).coerceAtMost(1f)),
                radius = r + 15f + p * 30f,
                style = Stroke(width = 2f)
            )
        }
    }
}

// --- 3. Systems ---

/** Main logic system: Handles player input, gravity fields, and collisions */
private class SingularitySystem(
    private val state: GameState = inject(),
    private val input: InputManager = inject(),
    private val camera: CameraService = inject(),
    private val particle: ParticleService = inject()
) : IntervalSystem() {

    private val playerFamily = world.family { all(Singularity, Transform) }
    private val shards = world.family { all(ShardTag, Transform, RigidBody) }

    override fun onTick(deltaTime: Float) {
        if (state.isGameOver) return

        val player = playerFamily.firstOrNull() ?: return
        val sing = player[Singularity]
        val trans = player[Transform]

        // Move singularity to pointer position
        val targetPos = camera.transformer.virtualToWorld(input.getPointerPosition())
        trans.position = targetPos

        if (input.isPointerDown) {
            // Charging: accumulate pulse energy and expand physical radius
            sing.pulseEnergy = (sing.pulseEnergy + deltaTime * 1.0f).coerceAtMost(1.5f)
            sing.radius = 60f + sing.pulseEnergy * 30f

            shards.forEach { shard ->
                val sTrans = shard[Transform]
                val sRigid = shard[RigidBody]
                val dist = sTrans.distanceTo(trans.position)

                if (dist < 500f) {
                    // Attraction logic: Shards are pulled into the singularity
                    shard[ShardState].isBeingPulled = true
                    val dir = (trans.position - sTrans.position).normalized()
                    // Gravitational force increases with charge energy
                    sRigid.applyForce(dir * (sing.pullForce * (1f + sing.pulseEnergy)))
                    // Disable automatic steering to let physics take over
                    shard.getOrNull(ArriveTarget)?.enabled = false
                } else {
                    shard[ShardState].isBeingPulled = false
                    shard.getOrNull(ArriveTarget)?.enabled = true
                }
            }

            // High-frequency intake particles
            if ((0f..1f).random() > 0.8f) {
                particle.emit { intakeEffect(trans.position) }
            }
        } else {
            // Release: trigger explosive pulse if sufficiently charged
            if (sing.pulseEnergy > 0.2f) {
                triggerPulse(trans.position, sing.pulseEnergy)
            }
            sing.pulseEnergy = 0f
            sing.radius = 60f // Reset to base radius
            shards.forEach {
                it[ShardState].isBeingPulled = false
                it.getOrNull(ArriveTarget)?.enabled = true
            }
        }

        // Damage check: Shards colliding with the core deplete stability
        shards.forEach { shard ->
            if (shard[Transform].distanceTo(trans.position) < sing.radius) {
                shard.remove()
                state.singularityHealth = (state.singularityHealth - 0.08f).coerceAtLeast(0f)
                camera.director.shake(0.3f)
                if (state.singularityHealth <= 0f) state.isGameOver = true
            }
        }
    }

    private fun triggerPulse(pos: Offset, energy: Float) {
        val range = 250f + energy * 300f
        camera.director.shake(energy * 0.6f)

        // Spawn explosive shockwave material
        particle.emit {
            layer("shock", pos) {
                config { duration = 0.5f; material = ShockwaveShader(context) }
                size = scalar(range * 2f)
            }
        }

        // Obliterate shards within the blast radius
        shards.forEach { shard ->
            val dist = shard[Transform].distanceTo(pos)
            if (dist < range) {
                particle.emit { burstEffect(shard[Transform].position, shard[ShardState].color) }
                shard.remove()
                state.score += (20 * energy).toInt()
            }
        }
    }
}

/** Spawner system: Handles waves of shards emerging from the edges */
private class ShardSpawnSystem(private val state: GameState = inject()) : IntervalSystem(interval = Fixed(0.6f)) {
    override fun onTick(deltaTime: Float) {
        if (state.isGameOver) return

        val angle = radians((0f..360f).random())
        val spawnPos = Offset(cos(angle), sin(angle)) * 600f

        val sharedState = ShardState()

        world.entity {
            +ShardTag
            +sharedState
            +Transform(spawnPos)
            +RigidBody(drag = 0.5f, maxSpeed = 400f)
            // Combined steering: wander naturally while drifting towards origin
            +Wander(radius = 50f, distance = 100f)
            +Arriver(speed = 150f)
            +ArriveTarget(Offset.Zero)
            +Renderable(CircleVisual(14f, sharedState.color), zIndex = 5)
        }
    }
}

// --- 4. Particles ---

/** Intake: Uses env.index for perfectly symmetrical convergence from all directions */
private fun ParticleNodeScope.intakeEffect(center: Offset) {
    layer("intake", center) {
        config {
            count = 16
            duration = 0.6f
            material = IntakeMaterial(context)
        }

        // Determine angle based on particle index for uniform distribution
        val angle = (env.index / env.count) * math.toRadians(360f)
        // Interleaved start distances for layered depth effect
        val startDist = 300f + math.mod(env.index, 4f) * 50f

        val p = env.progress
        position = vec2(
            math.cos(angle) * startDist * (1f - p),
            math.sin(angle) * startDist * (1f - p)
        )
        size = 2f + p * 6f
    }
}

/** Burst: Disperses geometric shards using per-particle math.random nodes */
private fun ParticleNodeScope.burstEffect(pos: Offset, col: Color) {
    layer("explode", pos) {
        config {
            count = 15
            duration = 0.7f
            material = DebrisMaterial(col, context)
        }
        // Correct usage: Use math.random nodes for unique trajectories per particle
        val angle = math.toRadians(math.random(0f, 360f))
        val dist = math.random(100f, 200f)
        val p = env.progress

        position = vec2(
            math.cos(angle) * dist * (1f - math.pow(1f - p, 3f)),
            math.sin(angle) * dist * (1f - math.pow(1f - p, 3f))
        )
        size = 10f * (1f - p)
    }
}

// --- 5. UI Entry ---

/** Entry point for the Neon Singularity demo using KSimpleGame DSL */
@Composable
fun NeonSingularityGame() {
    val state = remember { GameState() }

    KSimpleGame(virtualSize = Size(800f, 800f)) {
        onWorld {
            useDefaultSystems() // Injects Physics, Steering, and Rendering systems
            configure {
                injectables { +state }
                systems {
                    +SingularitySystem()
                    +ShardSpawnSystem()
                }
            }
            spawn {
                // Setup view environment
                entity {
                    +Transform()
                    +Camera(isMain = true)
                    +CameraShake()
                }
                // Setup player singularity
                val singComp = Singularity()
                entity {
                    +singComp
                    +Transform()
                    +Renderable(SingularityVisual(singComp), zIndex = 20)
                }
            }
        }

        // Render deep void background
        onBackgroundUI {
            Rectangle(color = Color(0xFF020008), modifier = Modifier.fillMaxSize())
        }

        // Render reactive HUD elements
        onForegroundUI {
            Box(Modifier.fillMaxSize().padding(32.dp)) {
                // Scoring
                Column(Modifier.align(Alignment.TopStart)) {
                    Text("NEON SINGULARITY", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("RESONANCE: ${state.score}", color = Color.Cyan, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                }

                // Core Health
                Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CORE STABILITY", color = Color.White.copy(0.7f), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.singularityHealth },
                        modifier = Modifier.width(300.dp).height(8.dp),
                        color = if (state.singularityHealth > 0.3f) Color.Cyan else Color.Red,
                        trackColor = Color.White.copy(0.1f)
                    )
                }

                // Game Over Overlay
                if (state.isGameOver) {
                    Surface(Modifier.align(Alignment.Center), color = Color.Black.copy(0.9f)) {
                        Column(Modifier.padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SINGULARITY COLLAPSED", color = Color.Red, fontSize = 28.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(16.dp))
                            Text("FINAL SCORE: ${state.score}", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
