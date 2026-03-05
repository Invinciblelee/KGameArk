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

private data object ShardTag : EntityTag()

private class Singularity(
    var pullForce: Float = 800f,
    var pulseEnergy: Float = 0f
) : Component<Singularity> {
    override fun type() = Singularity
    companion object : ComponentType<Singularity>()
}

private class ShardState(
    var isBeingPulled: Boolean = false,
    val color: Color = Color.random()
) : Component<ShardState> {
    override fun type() = ShardState
    companion object : ComponentType<ShardState>()
}

class GameState {
    var score by mutableIntStateOf(0)
    var singularityHealth by mutableFloatStateOf(1f)
    var isGameOver by mutableStateOf(false)
}

// --- 2. Shaders ---

/** 冲击波材质：环状能量扩散 */
class ShockwaveShader(val context: ParticleContext) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uProgress;
        uniform vec4 uColor;
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);
            float ring = smoothstep(uProgress - 0.1, uProgress, d) - smoothstep(uProgress, uProgress + 0.1, d);
            float alpha = ring * (1.0 - uProgress);
            return vec4(uColor.rgb * alpha, alpha);
        }
    """.trimIndent()
    override fun MaterialEffect.onSetup() { uniform(Material.COLOR, Color.White) }
    override fun MaterialEffect.onUpdate() { uniform(Material.PROGRESS, context.progress) }
}

class IntakeMaterial(val context: ParticleContext) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uProgress;
        vec4 main(vec2 uv) {
            float d = length(uv - 0.5) * 2.0;
            float mask = exp(-d * 5.0);
            vec3 color = mix(vec3(0.0, 0.5, 1.0), vec3(0.0, 1.0, 1.0), uProgress);
            return vec4(color * mask * uProgress, mask * uProgress);
        }
    """.trimIndent()
    override fun MaterialEffect.onUpdate() { uniform(Material.PROGRESS, context.progress) }
}

class DebrisMaterial(val baseColor: Color, val context: ParticleContext) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uProgress;
        uniform vec4 uColor;
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = max(abs(st.x), abs(st.y)); 
            float mask = 1.0 - step(0.5, d);
            float flash = pow(1.0 - uProgress, 8.0);
            vec3 color = mix(uColor.rgb, vec3(1.0), flash);
            return vec4(color * mask * (1.0 - uProgress), mask * (1.0 - uProgress));
        }
    """.trimIndent()
    override fun MaterialEffect.onSetup() { uniform(Material.COLOR, baseColor) }
    override fun MaterialEffect.onUpdate() { uniform(Material.PROGRESS, context.progress) }
}

private class SingularityVisual(val sing: Singularity) : Visual(120f) {
    override fun DrawScope.draw() {
        val p = sing.pulseEnergy
        drawCircle(Color(0xFF080015), radius = 40f + p * 10f)
        drawCircle(
            color = Color.Cyan.copy(alpha = 0.4f + p * 0.4f),
            radius = 45f + p * 20f,
            style = Stroke(width = 4f + p * 10f)
        )
        if (p > 0.5f) {
            drawCircle(
                color = Color.Magenta.copy(alpha = (p - 0.5f).coerceAtMost(1f)),
                radius = 55f + p * 30f,
                style = Stroke(width = 2f)
            )
        }
    }
}

// --- 3. Systems ---

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

        val targetPos = camera.transformer.virtualToWorld(input.getPointerPosition())
        trans.position = targetPos

        if (input.isPointerDown) {
            sing.pulseEnergy = (sing.pulseEnergy + deltaTime * 1.0f).coerceAtMost(1.5f)

            shards.forEach { shard ->
                val sTrans = shard[Transform]
                val sRigid = shard[RigidBody]
                val dist = sTrans.distanceTo(trans.position)

                if (dist < 500f) {
                    shard[ShardState].isBeingPulled = true
                    val dir = (trans.position - sTrans.position).normalized()
                    sRigid.applyForce(dir * (sing.pullForce * (1f + sing.pulseEnergy)))
                    shard.getOrNull(ArriveTarget)?.enabled = false
                } else {
                    shard[ShardState].isBeingPulled = false
                    shard.getOrNull(ArriveTarget)?.enabled = true
                }
            }

            if ((0f..1f).random() > 0.8f) {
                particle.emit { intakeEffect(trans.position) }
            }
        } else {
            if (sing.pulseEnergy > 0.2f) {
                triggerPulse(trans.position, sing.pulseEnergy)
            }
            sing.pulseEnergy = 0f
            shards.forEach {
                it[ShardState].isBeingPulled = false
                it.getOrNull(ArriveTarget)?.enabled = true
            }
        }

        shards.forEach { shard ->
            if (shard[Transform].distanceTo(trans.position) < 40f) {
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

        particle.emit {
            layer("shock", pos) {
                config { duration = 0.5f; material = ShockwaveShader(context) }
                size = scalar(range * 2f)
            }
        }

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

private class ShardSpawnSystem(private val state: GameState = inject()) : IntervalSystem(interval = Fixed(0.6f)) {
    override fun onTick(deltaTime: Float) {
        if (state.isGameOver) return

        val angle = radians((0f..360f).random())
        val spawnPos = Offset(cos(angle), sin(angle)) * 600f

        world.entity {
            +ShardTag
            +ShardState()
            +Transform(spawnPos)
            +RigidBody(drag = 0.5f, maxSpeed = 400f)
            +Wander(radius = 50f, distance = 100f)
            +Arriver(speed = 150f)
            +ArriveTarget(Offset.Zero)
            +Renderable(CircleVisual(14f, Color.White), zIndex = 5)
        }
    }
}

// --- 4. Particles (材质驱动) ---

private fun ParticleNodeScope.intakeEffect(center: Offset) {
    layer("intake", center) {
        config {
            count = 1
            duration = 0.4f
            material = IntakeMaterial(context)
        }
        val angle = math.toRadians(math.random(0f, 360f))
        val startDist = math.random(150f, 250f)

        position = vec2(
            math.cos(angle) * startDist * (1f - env.progress),
            math.sin(angle) * startDist * (1f - env.progress)
        )
        size = 3f + env.progress * 5f
    }
}

private fun ParticleNodeScope.burstEffect(pos: Offset, col: Color) {
    layer("explode", pos) {
        config {
            count = 15
            duration = 0.7f
            material = DebrisMaterial(col, context)
        }
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

// --- 5. UI Entry (KSimpleGame) ---

@Composable
fun NeonSingularityGame() {
    val state = remember { GameState() }

    KSimpleGame(virtualSize = Size(800f, 800f)) {
        onWorld {
            useDefaultSystems()
            configure {
                injectables { +state }
                systems {
                    +SingularitySystem()
                    +ShardSpawnSystem()
                }
            }
            spawn {
                entity {
                    +Transform()
                    +Camera(isMain = true)
                    +CameraShake()
                }
                val singComp = Singularity()
                entity {
                    +singComp
                    +Transform()
                    +Renderable(SingularityVisual(singComp), zIndex = 20)
                }
            }
        }

        onBackgroundUI {
            Rectangle(color = Color(0xFF020008), modifier = Modifier.fillMaxSize())
        }

        onForegroundUI {
            Box(Modifier.fillMaxSize().padding(32.dp)) {
                Column(Modifier.align(Alignment.TopStart)) {
                    Text("NEON SINGULARITY", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("RESONANCE: ${state.score}", color = Color.Cyan, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                }

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