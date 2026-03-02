@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package com.example.kgame.games.fruit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgame.ecs.*
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.KSimpleGame
import com.kgame.engine.geometry.Anchor
import com.kgame.engine.graphics.drawscope.withCenteredTransform
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.MaterialEffect
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.Rectangle
import com.kgame.plugins.components.*
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.systems.*
import com.kgame.plugins.visuals.Visual
import com.kgame.plugins.visuals.material.MaterialVisual
import org.intellij.lang.annotations.Language
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

// --- 1. Shaders ---

@OptIn(ExperimentalMaterialVisuals::class)
class FruitMaterial(val baseColor: Color) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform vec4 uColor;
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);
            if (d > 1.0) return vec4(0.0);
            vec3 n = vec3(st, sqrt(max(0.0, 1.0 - dot(st, st))));
            vec3 l = normalize(vec3(-0.5, -0.5, 1.0));
            float diff = max(0.0, dot(n, l));
            float spec = pow(max(0.0, dot(reflect(-l, n), vec3(0,0,1))), 15.0);
            vec3 col = mix(uColor.rgb * 0.2, uColor.rgb, diff) + spec * 0.4;
            float alpha = smoothstep(1.0, 0.96, d) * uColor.a;
            return vec4(col * alpha, alpha);
        }
    """.trimIndent()
    override fun MaterialEffect.onSetup() { uniform(Material.COLOR, baseColor) }
}

@OptIn(ExperimentalMaterialVisuals::class)
class BombMaterial : Material {
    @Language("AGSL")
    override val sksl: String = """
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);
            if (d > 1.0) return vec4(0.0);
            
            // Metallic sphere shading
            vec3 n = vec3(st, sqrt(max(0.0, 1.0 - dot(st, st))));
            vec3 l = normalize(vec3(-0.5, -0.5, 1.0));
            float diff = max(0.0, dot(n, l));
            float spec = pow(max(0.0, dot(reflect(-l, n), vec3(0,0,1))), 25.0);
            
            // Dark grey/black metallic look
            vec3 col = mix(vec3(0.05), vec3(0.25), diff) + spec * 0.6;
            float alpha = smoothstep(1.0, 0.96, d);
            return vec4(col * alpha, alpha);
        }
    """.trimIndent()
}

// --- 2. State & Components ---

private data object FruitTag : EntityTag()
private data object BombTag : EntityTag()
private data object BladeTag : EntityTag()

private class BladeTrail(val maxLength: Int = 15) : Component<BladeTrail> {
    val points = mutableListOf<Offset>()
    override fun type() = BladeTrail
    companion object : ComponentType<BladeTrail>()
}

class SlicerState {
    var score by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
    var isGameOver by mutableStateOf(false)
    var pendingReset by mutableStateOf(false)
}

// --- 3. Systems ---

private class FruitLogicSystem(
    private val state: SlicerState = inject(),
    private val input: InputManager = inject(),
    private val camera: CameraService = inject(),
    private val particles: ParticleService = inject()
) : IntervalSystem() {

    private val targetFamily = world.family {
        all(Transform, RigidBody).any(FruitTag, BombTag).none(CleanupTag)
    }
    private val bladeFamily = world.family { all(BladeTag, BladeTrail) }
    private var spawnTimer = 0f

    override fun onAwake() { performReset() }

    override fun onTick(deltaTime: Float) {
        if (state.pendingReset) { performReset(); state.pendingReset = false; return }
        if (state.isGameOver) return

        // 1. Blade Processing
        val blade = bladeFamily.firstOrNull() ?: return
        val trail = blade[BladeTrail]
        val worldMouse = camera.transformer.virtualToWorld(input.getPointerPosition(0))

        if (input.isPointerDown(0)) {
            val last = if (trail.points.isNotEmpty()) trail.points.first() else worldMouse
            trail.points.add(0, worldMouse)
            if (trail.points.size > trail.maxLength) trail.points.removeLast()

            // Slice Detection
            if (last != worldMouse) {
                targetFamily.forEach { target ->
                    val fPos = target[Transform].position
                    if (isPointOnSegment(fPos, last, worldMouse, 50f)) {
                        if (target.has(BombTag)) hitBomb(target) else slice(target)
                    }
                }
            }
        } else {
            if (trail.points.isNotEmpty()) trail.points.clear()
        }

        // 2. Generation
        spawnTimer += deltaTime
        if (spawnTimer >= 0.75f) {
            if ((0f..1f).random() > 0.82f) spawnBomb() else spawnFruit()
            spawnTimer = 0f
        }

        // 3. Auto Cleanup
        targetFamily.forEach { target ->
            val pos = target[Transform].position
            val rb = target[RigidBody]
            if (pos.y > 500f && rb.velocity.y > 0) {
                target.configure { +CleanupTag }
                target.remove()
            }
        }
    }

    private fun isPointOnSegment(p: Offset, a: Offset, b: Offset, r: Float): Boolean {
        val l2 = (a - b).getDistanceSquared()
        if (l2 == 0f) return (p - a).getDistance() < r
        val t = (((p.x - a.x) * (b.x - a.x)) + ((p.y - a.y) * (b.y - a.y))) / l2
        if (t !in 0f..1f) return false
        return (p - Offset(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))).getDistance() < r
    }

    private fun slice(fruit: Entity) {
        val pos = fruit[Transform].position
        val fruitColor = Color(1f, (0.2f..0.8f).random(), 0f)
        particles.emit { splash(pos, fruitColor) }
        camera.director.shake(0.12f)
        state.score += 10
        fruit.configure { +CleanupTag }
        fruit.remove()
    }

    private fun hitBomb(bomb: Entity) {
        val pos = bomb[Transform].position
        particles.emit {
            layer("bomb", pos) {
                config { count = 30; duration = 0.7f }
                val angle = math.random(0f, 360f)
                val speed = math.random(200f, 600f)
                position = vec2(math.cos(math.toRadians(angle)) * speed * env.progress,
                    math.sin(math.toRadians(angle)) * speed * env.progress)
                color = color(0.1f, 0.1f, 0.1f, 1f - env.progress)
                size = scalar(20f) * (1f - env.progress)
            }
        }
        camera.director.shake(0.6f)
        state.lives--
        bomb.configure { +CleanupTag }
        bomb.remove()
        if (state.lives <= 0) state.isGameOver = true
    }

    private fun spawnFruit() {
        val startX = ((-300f)..(300f)).random()
        val vx = if (startX > 0) ((-250f)..(-100f)).random() else ((100f)..(250f)).random()
        world.entity {
            +FruitTag
            +Transform(Offset(startX, 450f))
            +RigidBody(drag = 0.3f, maxSpeed = 1500f, velocity = Velocity(vx, -1250f))
            +Renderable(MaterialVisual(FruitMaterial(Color(1f, 0.5f, 0.1f)), 55f), zIndex = 10)
        }
    }

    private fun spawnBomb() {
        val startX = ((-250f)..(250f)).random()
        val vx = ((-120f)..(120f)).random()
        world.entity {
            +BombTag
            +Transform(Offset(startX, 450f))
            +RigidBody(drag = 0.2f, maxSpeed = 1200f, velocity = Velocity(vx, -1000f))
            +Renderable(Visual(size = 60f,
                onCreate = { MaterialEffect(BombMaterial()) },
                onDraw = { effect ->
                    // Main metallic bomb body
                    drawCircle(brush = effect.obtainBrush(size))
                    // Red sparking wick point
                    drawCircle(Color.Red, radius = 6f, center = center - Offset(15f, 15f))
                }
            ), zIndex = 11)
        }
    }

    private fun performReset() {
        targetFamily.forEach { it.remove() }
        state.score = 0; state.lives = 3; state.isGameOver = false
    }
}

private class BladeTrailVisual(private val trail: BladeTrail) : Visual() {
    private val leftSide = mutableListOf<Offset>()
    private val rightSide = mutableListOf<Offset>()
    private val meshPath = Path()

    override var anchor: Anchor = Anchor.TopLeft

    override fun DrawScope.draw() {
        val originalPoints = trail.points
        val count = originalPoints.size
        if (count < 2) return

        val points = if (count == 2) {
            val p1 = originalPoints[0]; val p2 = originalPoints[1]
            listOf(p1, (p1 + p2) / 2f, p2)
        } else originalPoints

        val currentCount = points.size
        leftSide.clear(); rightSide.clear()

        for (i in 0 until currentCount) {
            val curr = points[i]
            val dir = if (i < currentCount - 1) points[i] - points[i + 1] else points[i - 1] - points[i]
            val len = sqrt(dir.x * dir.x + dir.y * dir.y)
            val normal = if (len > 0f) Offset(-dir.y / len, dir.x / len) else Offset.Zero
            val ratio = i.toFloat() / (currentCount - 1)
            val width = sin(PI.toFloat() * (1f - ratio)) * 12f
            val center = Offset(curr.x, curr.y)
            leftSide.add(center + normal * width)
            rightSide.add(center - normal * width)
        }

        meshPath.rewind()
        meshPath.apply {
            if (leftSide.isEmpty()) return@apply
            moveTo(leftSide[0].x, leftSide[0].y)
            for (i in 1 until leftSide.size - 1) {
                val curr = leftSide[i]; val next = leftSide[i + 1]
                quadraticTo(curr.x, curr.y, (curr.x + next.x) / 2f, (curr.y + next.y) / 2f)
            }
            lineTo(leftSide.last().x, leftSide.last().y)
            lineTo(rightSide.last().x, rightSide.last().y)
            for (i in rightSide.size - 2 downTo 1) {
                val curr = rightSide[i]; val next = rightSide[i - 1]
                quadraticTo(curr.x, curr.y, (curr.x + next.x) / 2f, (curr.y + next.y) / 2f)
            }
            lineTo(leftSide[0].x, leftSide[0].y)
            close()
        }
        drawPath(path = meshPath, color = Color.Cyan.copy(alpha = 0.35f))
        drawPath(path = meshPath, color = Color.White.copy(alpha = 0.8f))
    }
}

private fun ParticleNodeScope.splash(pos: Offset, color: Color) {
    layer("splash", pos) {
        config { count = 12; duration = 0.5f }
        val angleNode = math.random(0f, 360f)
        val radNode = math.toRadians(angleNode)
        val speedNode = math.random(100f, 350f)
        position = vec2(math.cos(radNode) * speedNode * env.progress,
            math.sin(radNode) * speedNode * env.progress + (150f * env.progress))
        size = scalar(12f) * (1f - env.progress)
        this.color = color(color.red, color.green, color.blue, 1f - env.progress)
    }
}

@Composable
fun FruitSlicerGame() {
    val state = remember { SlicerState() }
    KSimpleGame(virtualSize = Size(800f, 600f)) {
        onWorld {
            useDefaultSystems()
            configure {
                injectables { +state }
                systems {
                    +FruitLogicSystem()
                    +PhysicsSystem(gravity = Offset(0f, 900f))
                }
            }
            spawn {
                entity { +Transform(); +Camera("main", isMain = true); +CameraShake() }
                val trail = BladeTrail(15)
                entity { +BladeTag; +trail; +Transform(); +Renderable(BladeTrailVisual(trail), zIndex = 100) }
            }
        }
        onBackgroundUI { Rectangle(Color(0xFF0A0A0A)) }
        onForegroundUI {
            Box(Modifier.fillMaxSize().padding(32.dp)) {
                Column {
                    Text("FRUIT SLICER", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("SCORE: ${state.score}", color = Color.Yellow, fontSize = 32.sp, fontWeight = FontWeight.Bold)

                    val currentLives = state.lives
                    Row(Modifier.padding(top = 8.dp)) {
                        repeat(3) { i ->
                            Text(
                                text = "♥",
                                color = if (i < currentLives) Color.Red else Color.DarkGray,
                                fontSize = 24.sp
                            )
                        }
                    }
                }
                if (state.isGameOver) {
                    Surface(Modifier.align(Alignment.Center), color = Color.Black.copy(0.9f), shape = MaterialTheme.shapes.extraLarge) {
                        Column(Modifier.padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BOOM!", color = Color.Red, fontSize = 48.sp, fontWeight = FontWeight.Black)
                            Text("FINAL SCORE: ${state.score}", color = Color.White.copy(0.7f), fontSize = 20.sp)
                            Button(onClick = { state.pendingReset = true }, Modifier.padding(top = 24.dp)) { Text("RETRY") }
                        }
                    }
                }
            }
        }
    }
}