package com.example.kgame.games.catalyst

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.MaterialEffect
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.Rectangle
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.RigidBody
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.Wander
import com.kgame.plugins.components.distanceTo
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleContext
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.visuals.Visual
import com.kgame.plugins.visuals.material.MaterialVisual
import org.intellij.lang.annotations.Language
import kotlin.math.sin

// --- 1. Components & State ---

/** Tag for wandering quantum atoms */
private data object AtomTag : EntityTag()

/** Component for atoms containing visual properties */
private class AtomState(
    val color: Color = Color.random(),
    val radius: Float = (12f..24f).random()
) : Component<AtomState> {
    override fun type() = AtomState
    companion object : ComponentType<AtomState>()
}

/** Explosion component that manages its own expansion cycle */
private class Explosion(
    var radius: Float = 0f,
    val maxRadius: Float = 90f,
    val duration: Float = 1.2f,
    var elapsed: Float = 0f
) : Component<Explosion> {
    override fun type() = Explosion
    companion object : ComponentType<Explosion>()
}

/** Game state shared between ECS and UI */
class CatalystState {
    var score by mutableIntStateOf(0)
    var chainCount by mutableIntStateOf(0)
    var maxChain by mutableIntStateOf(0)
    var isReacting by mutableStateOf(false)
}

// --- 2. Shaders & Visuals ---

/** Pulsing Core Material for Atoms */
class AtomCoreMaterial(val baseColor: Color) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uTime;
        uniform vec4 uColor;
        vec4 main(vec2 uv) {
            float d = length(uv - 0.5) * 2.0;
            float pulse = sin(uTime * 3.0) * 0.1 + 0.9;
            float core = 1.0 - smoothstep(0.0, 0.6 * pulse, d);
            float glow = exp(-d * 3.0) * 0.5;
            return vec4(uColor.rgb * (core + glow), core + glow);
        }
    """.trimIndent()
    override fun MaterialEffect.onSetup() { uniform("uColor", baseColor) }
    override fun MaterialEffect.onUpdate() { uniform("uTime", elapsedTime) }
}

/** Blast wave material for explosions */
class BlastWaveMaterial(val baseColor: Color, val context: ParticleContext) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uProgress;
        uniform vec4 uColor;
        vec4 main(vec2 uv) {
            float d = length(uv - 0.5) * 2.0;
            float ring = smoothstep(uProgress - 0.15, uProgress, d) - smoothstep(uProgress, uProgress + 0.1, d);
            return vec4(uColor.rgb * ring * (1.0 - uProgress), ring * (1.0 - uProgress));
        }
    """.trimIndent()
    override fun MaterialEffect.onSetup() { uniform("uColor", baseColor) }
    override fun MaterialEffect.onUpdate() { uniform("uProgress", context.progress) }
}

private class AtomVisual(val state: AtomState) : Visual(Size(60f, 60f)) {
    override fun DrawScope.draw() {
        drawCircle(state.color, radius = state.radius)
    }
}

private fun ParticleNodeScope.explosionEffect(pos: Offset, col: Color) {
    // Harmonic wave effect
    layer("wave", pos) {
        config { count = 1; duration = 0.8f; material = BlastWaveMaterial(col, context) }
        size = scalar(180f)
    }
    // Dispersive quantum sparks
    layer("sparks", pos) {
        config { count = 24; duration = 1.0f }
        val angle = math.toRadians(math.random(0f, 360f))
        val dist = math.random(20f, 100f)
        val p = env.progress
        position = vec2(math.cos(angle) * dist * p, math.sin(angle) * dist * p)
        size = 4f * (1f - p)
        // Idiomatic color mixing
        color = color(
            red = ops.mix(col.red, 1f, p),
            green = ops.mix(col.green, 1f, p),
            blue = ops.mix(col.blue, 1f, p),
            alpha = 1f - p
        )
    }
}

// --- 3. Systems ---

/** Handles the catalyst trigger and subsequent chain reaction logic */
private class CatalystSystem(
    private val state: CatalystState = inject(),
    private val input: InputManager = inject(),
    private val camera: CameraService = inject(),
    private val particle: ParticleService = inject()
) : IntervalSystem() {

    private val atoms = world.family { all(AtomTag, AtomState, Transform) }
    private val explosions = world.family { all(Explosion, Transform) }

    override fun onTick(deltaTime: Float) {
        // Handle User Input: Place the first Catalyst
        if (input.isPointerJustPressed() && !state.isReacting) {
            val worldPos = camera.transformer.virtualToWorld(input.getPointerPosition())
            triggerExplosion(worldPos, Color.White)
            state.isReacting = true
            state.chainCount = 0
        }

        // Manage Active Explosions
        explosions.forEach { expEntity ->
            val exp = expEntity[Explosion]
            val expTrans = expEntity[Transform]

            exp.elapsed += deltaTime
            val progress = (exp.elapsed / exp.duration).coerceIn(0f, 1f)
            // Non-linear expansion curve
            exp.radius = exp.maxRadius * sin(progress * 3.14159f)

            if (progress >= 1.0f) {
                expEntity.remove()
                return@forEach
            }

            // Detect neighbor atoms to continue the chain
            atoms.forEach { atomEntity ->
                val atom = atomEntity[AtomState]
                val atomTrans = atomEntity[Transform]

                if (atomTrans.distanceTo(expTrans.position) < exp.radius + atom.radius) {
                    // Catalyst successful: Atom destabilizes and explodes
                    triggerExplosion(atomTrans.position, atom.color)
                    atomEntity.remove()
                    
                    state.chainCount++
                    state.score += state.chainCount * 15
                    if (state.chainCount > state.maxChain) state.maxChain = state.chainCount
                    
                    camera.director.shake(0.15f * (state.chainCount.coerceAtMost(10) / 10f))
                }
            }
        }

        // Reset state when reaction settles
        if (state.isReacting && explosions.entitySize == 0) {
            state.isReacting = false
        }
    }

    private fun triggerExplosion(pos: Offset, color: Color) {
        world.entity {
            +Explosion()
            +Transform(pos)
        }
        
        particle.emit { explosionEffect(pos, color) }
    }
}

/** Handles continuous spawning of atoms to keep the field populated */
private class AtomSpawnSystem : IntervalSystem(interval = Fixed(0.5f)) {
    private val atoms = world.family { all(AtomTag) }
    private val maxAtoms = 60

    override fun onTick(deltaTime: Float) {
        if (atoms.entitySize < maxAtoms) {
            spawnAtom()
        }
    }

    private fun spawnAtom() {
        val spawnPos = Offset((-350f..350f).random(), (-350f..350f).random())
        world.entity {
            val atom = AtomState()
            +AtomTag
            +atom
            +Transform(spawnPos)
            +RigidBody(drag = 0.3f, maxSpeed = 100f)
            +Wander(radius = 60f, jitter = 80f)
            +Renderable(MaterialVisual(AtomCoreMaterial(atom.color), atom.radius * 2f))
        }
    }
}

// --- 4. UI Entry ---

@Composable
fun QuantumCatalystGame() {
    val state = remember { CatalystState() }

    KSimpleGame(virtualSize = Size(800f, 800f)) {
        onWorld {
            useDefaultSystems()
            configure {
                injectables { +state }
                systems { 
                    +CatalystSystem()
                    +AtomSpawnSystem()
                }
            }
            spawn {
                entity {
                    +Transform()
                    +Camera(isMain = true)
                    +CameraShake()
                }
            }
        }

        onBackgroundUI {
            Rectangle(color = Color(0xFF010208), modifier = Modifier.fillMaxSize())
        }

        onForegroundUI {
            Box(Modifier.fillMaxSize().padding(32.dp)) {
                // Header
                Column(Modifier.align(Alignment.TopStart)) {
                    Text("QUANTUM CATALYST", color = Color.White.copy(0.7f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("RESONANCE: ${state.score}", color = Color(0xFF00FFCC), fontSize = 32.sp, fontWeight = FontWeight.Black)
                }

                // Stats
                Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                    Text("HIGHEST CHAIN", color = Color.White.copy(0.5f), fontSize = 10.sp)
                    Text("${state.maxChain}", color = Color.Yellow, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }

                if (!state.isReacting) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
                        color = Color.White.copy(0.05f),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.2f))
                    ) {
                        Text(
                            "TOUCH FIELD TO INITIATE CATALYSIS",
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Reaction feedback
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${state.chainCount}",
                            color = Color.White.copy(0.2f),
                            fontSize = 120.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text("CHAIN REACTION ACTIVE", color = Color.Cyan.copy(0.5f), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
