package com.example.kgame.games.td

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.ecs.Entity
import com.kgame.ecs.EntityTag
import com.kgame.ecs.Fixed
import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.KSimpleGame
import com.kgame.engine.geometry.normalized
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.MaterialEffect
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.Rectangle
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.distanceTo
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.visuals.Visual
import com.kgame.plugins.visuals.material.MaterialVisual
import kotlinx.coroutines.delay
import org.intellij.lang.annotations.Language

// --- 1. Components & State ---

private data object EnemyTag : EntityTag()
private data object TowerTag : EntityTag()
private data object ProjectileTag : EntityTag()

/** Component for the mobile power core (player controlled) */
internal class FluxCore(
    val powerRadius: Float = 220f,
    var currentPos: Offset = Offset.Zero
) : Component<FluxCore> {
    override fun type() = FluxCore
    companion object : ComponentType<FluxCore>()
}

/** Pathfinding for Glitch Enemies */
private class GlitchPath(
    val nodes: List<Offset>,
    var targetIndex: Int = 0,
    val speed: Float = (120f..180f).random()
) : Component<GlitchPath> {
    override fun type() = GlitchPath
    companion object : ComponentType<GlitchPath>()
}

/** Tower state: dormant unless powered by the FluxCore */
internal class SentryState(
    val range: Float = 320f,
    val fireRate: Float = 0.35f,
    var cooldown: Float = 0f,
    var isPowered: Boolean = false,
    val baseColor: Color = listOf(Color.Cyan, Color.Magenta, Color.Yellow).random()
) : Component<SentryState> {
    override fun type() = SentryState
    companion object : ComponentType<SentryState>()
}

private class BoltState(val target: Entity, val color: Color) : Component<BoltState> {
    override fun type() = BoltState
    companion object : ComponentType<BoltState>()
}

class FluxGameState {
    var gold by mutableIntStateOf(60)
    var health by mutableIntStateOf(10)
    var score by mutableIntStateOf(0)
    var isGameOver by mutableStateOf(false)

    var message by mutableStateOf("")
}

// --- 2. Shaders ---

/** Dynamic Grid Shader with ripples reactive to the FluxCore */
private class FluxGridMaterial(private val core: FluxCore) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uTime;
        uniform vec2 uCorePos;
        vec4 main(vec2 uv) {
            vec2 st = uv * 12.0;
            float d = length(uv - uCorePos);
            // Dynamic ripple radiating from the core
            float ripple = sin(d * 25.0 - uTime * 6.0) * 0.5 + 0.5;
            float grid = min(abs(fract(st.x) - 0.5), abs(fract(st.y) - 0.5));
            float mask = 1.0 - smoothstep(0.0, 0.04, grid);
            vec3 color = mix(vec3(0.01, 0.02, 0.06), vec3(0.0, 0.6, 1.0), mask * (ripple * 0.3 + 0.15));
            return vec4(color, 1.0);
        }
    """.trimIndent()
    override fun MaterialEffect.onUpdate() {
        uniform("uTime", elapsedTime)
        uniform("uCorePos", (core.currentPos.x + 500f) / 1000f, (core.currentPos.y + 500f) / 1000f)
    }
}

/** Sentry Material: Glows intensely when uPowered is active */
private class SentryMaterial(val state: SentryState) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uPowered;
        uniform float uTime;
        uniform vec4 uColor;
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);
            float pulse = sin(uTime * (uPowered * 12.0 + 3.0)) * 0.12 + 0.88;
            float ring = smoothstep(0.7 * pulse, 0.8 * pulse, d) - smoothstep(0.85, 0.95, d);
            vec3 col = mix(vec3(0.15), uColor.rgb, uPowered);
            float alpha = (ring + 0.15) * mix(0.3, 1.0, uPowered);
            return vec4(col * alpha, alpha);
        }
    """.trimIndent()
    override fun MaterialEffect.onSetup() { uniform("uColor", state.baseColor) }
    override fun MaterialEffect.onUpdate() {
        uniform("uPowered", if (state.isPowered) 1f else 0f)
        uniform("uTime", elapsedTime)
    }
}

// --- 3. Systems ---

private class FluxControlSystem(
    private val state: FluxGameState = inject(),
    private val input: InputManager = inject(),
    private val camera: CameraService = inject(),
    private val particle: ParticleService = inject()
) : IntervalSystem() {

    private val sentryFamily = world.family { all(TowerTag, SentryState, Transform) }
    private val enemyFamily = world.family { all(EnemyTag, GlitchPath, Transform) }
    private val boltFamily = world.family { all(ProjectileTag, BoltState, Transform) }

    override fun onTick(deltaTime: Float) {
        if (state.isGameOver) return

        val worldPos = camera.transformer.virtualToWorld(input.getPointerPosition())
        val coreEntity = world.family { all(FluxCore) }.firstOrNull() ?: return
        val core = coreEntity[FluxCore]
        core.currentPos = worldPos

        if (input.isPointerJustPressed()) {
            if (state.gold > 25) {
                spawnSentry(worldPos)
                state.gold -= 25
            } else {
                state.message = "Not enough gold!"
            }
        }

        sentryFamily.forEach { entity ->
            val sentry = entity[SentryState]
            val trans = entity[Transform]
            
            val distToCore = (trans.position - worldPos).getDistance()
            sentry.isPowered = distToCore < core.powerRadius

            if (sentry.isPowered) {
                sentry.cooldown -= deltaTime
                if (sentry.cooldown <= 0f) {
                    var nearestEnemy: Entity? = null
                    var minDist = sentry.range
                    enemyFamily.forEach { e ->
                        val d = e[Transform].distanceTo(trans.position)
                        if (d < minDist) {
                            minDist = d
                            nearestEnemy = e
                        }
                    }
                    
                    if (nearestEnemy != null) {
                        spawnBolt(trans.position, nearestEnemy, sentry.baseColor)
                        sentry.cooldown = sentry.fireRate
                        particle.emit { intakeParticles(trans.position, sentry.baseColor) }
                    }
                }
            }
        }

        enemyFamily.forEach { e ->
            val trans = e[Transform]
            val path = e[GlitchPath]
            val target = path.nodes[path.targetIndex]
            val diff = target - trans.position
            
            if (diff.getDistance() < 15f) {
                path.targetIndex++
                if (path.targetIndex >= path.nodes.size) {
                    state.health--
                    e.remove()
                    camera.director.shake(0.45f)
                    if (state.health <= 0) state.isGameOver = true
                    return@forEach
                }
            } else {
                trans.position += diff.normalized() * path.speed * deltaTime
            }
        }

        boltFamily.forEach { p ->
            val bolt = p[BoltState]
            val pt = p[Transform]
            if (bolt.target.wasRemoved()) {
                p.remove()
                return@forEach
            }
            val et = bolt.target[Transform]
            val diff = et.position - pt.position
            pt.position += diff.normalized() * 700f * deltaTime

            if (diff.getDistance() < 25f) {
                particle.emit { crashBurst(pt.position, bolt.color) }
                bolt.target.remove()
                p.remove()
                state.gold += 5
                state.score += 50
            }
        }
    }

    private fun spawnSentry(pos: Offset) {
        world.entity {
            val s = SentryState()
            +TowerTag; +s; +Transform(pos)
            +Renderable(object : MaterialVisual(SentryMaterial(s), 64f) {
                override fun DrawScope.draw(brush: Brush) {
                    drawCircle(brush)
                }
            }, zIndex = 10)
        }
    }

    private fun spawnBolt(pos: Offset, target: Entity, color: Color) {
        world.entity {
            +ProjectileTag; +BoltState(target, color); +Transform(pos)
            +Renderable(CircleVisual(10f, color), zIndex = 20)
        }
    }

    private fun intakeParticles(pos: Offset, col: Color) {
        particle.emit {
            layer("gather", pos) {
                config { count = 12; duration = 0.4f }
                val angle = (env.index / env.count) * math.toRadians(360f)
                val p = env.progress
                position = vec2(math.cos(angle) * (1f - p) * 100f, math.sin(angle) * (1f - p) * 100f)
                size = 3f + p * 5f
                color = color(col.red, col.green, col.blue, p)
            }
        }
    }

    private fun crashBurst(pos: Offset, col: Color) {
        particle.emit {
            layer("glitch", pos) {
                config { count = 16; duration = 0.6f }
                val angle = math.toRadians(math.random(0f, 360f))
                val dist = math.random(50f, 120f)
                val p = env.progress
                position = vec2(math.cos(angle) * dist * p, math.sin(angle) * dist * p)
                size = 10f * (1f - p)
                color = color(col.red, col.green, col.blue, 1f - p)
            }
        }
    }
}

private class GlitchSpawnSystem(private val state: FluxGameState = inject()) : IntervalSystem(interval = Fixed(1.6f)) {
    private val path = listOf(
        Offset(-500f, 0f), Offset(-250f, -350f), Offset(250f, 350f), Offset(500f, 0f)
    )
    override fun onTick(deltaTime: Float) {
        if (state.isGameOver) return
        repeat((1..3).random()) {
            world.entity {
                +EnemyTag; +GlitchPath(path); +Transform(path.first())
                +Renderable(GlitchVisual(), zIndex = 5)
            }
        }
    }
}

// --- 4. Visuals ---

private class GlitchVisual : Visual(Size(36f, 32f)) {
    override fun DrawScope.draw() {
        val glitch = (0..2).random() > 0
        val color = if (glitch) Color.White else Color.Red
        drawRect(color, style = Stroke(width = 3f))
        if (glitch) drawRect(color.copy(alpha = 0.15f))
    }
}

private class CircleVisual(val radius: Float, val color: Color) : Visual(Size(radius * 2, radius * 2)) {
    override fun DrawScope.draw() { drawCircle(color, radius = radius) }
}

// --- 5. UI Entry ---

@Composable
fun GeometricTDGame() {
    val state = remember { FluxGameState() }

    KSimpleGame(virtualSize = Size(1000f, 1000f)) {
        onWorld {
            useDefaultSystems()
            val core = FluxCore()
            configure {
                injectables { +state }
                systems { +FluxControlSystem(); +GlitchSpawnSystem() }
            }
            spawn {
                entity { +Transform(); +Camera(isMain = true); +CameraShake() }
                entity { +core }
                entity {
                    +Transform()
                    +Renderable(MaterialVisual(FluxGridMaterial(core), Size(1200f, 1200f)), zIndex = -1)
                }
            }
        }

        onBackgroundUI { Rectangle(color = Color(0xFF010206), modifier = Modifier.fillMaxSize()) }

        onForegroundUI {
            Box(Modifier.fillMaxSize().padding(32.dp)) {
                Column(Modifier.align(Alignment.TopStart)) {
                    Text("FLUX OVERSEER", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("FLUX CAPACITY: ${state.gold}", color = Color.Cyan, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text("STABILITY", color = Color.White.copy(0.5f), fontSize = 12.sp)
                    LinearProgressIndicator(
                        progress = { state.health / 10f },
                        modifier = Modifier.width(260.dp).height(8.dp),
                        color = if (state.health > 3) Color.Cyan else Color.Red,
                        trackColor = Color.White.copy(0.1f)
                    )
                }

                if (state.message.isNotEmpty()) {
                    Text(
                        text = state.message,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    LaunchedEffect(state.message) {
                        delay(2000)
                        state.message = ""
                    }
                }

                if (!state.isGameOver) {
                    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("POWER SENTRIES WITH YOUR RADIUS", color = Color.White.copy(0.4f), fontSize = 14.sp)
                        Text("TAP ANYWHERE TO BUILD", color = Color.White.copy(0.2f), fontSize = 12.sp)
                    }
                } else {
                    Surface(Modifier.align(Alignment.Center), color = Color.Black.copy(0.92f)) {
                        Column(Modifier.padding(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("FLUX COLLAPSED", color = Color.Red, fontSize = 36.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(16.dp))
                            Text("FINAL RESONANCE: ${state.score}", color = Color.White, fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}
