@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package com.example.kgame.games.wuxing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import com.kgame.ecs.*
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.KGame
import com.kgame.engine.core.rememberGameSceneStack
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.MaterialEffect
import com.kgame.engine.input.InputManager
import com.kgame.plugins.components.*
import com.kgame.plugins.systems.*
import com.kgame.plugins.visuals.Visual
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleContext
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.AnimationService
import com.kgame.plugins.services.play
import org.intellij.lang.annotations.Language
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

// --- 1. Materials & Shaders ---

class NeonBoxMaterial(val baseColor: Color) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uTime;
        uniform vec4 uColor;
        
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = max(abs(st.x), abs(st.y));
            
            float pulse = sin(uTime * 8.0) * 0.15 + 0.85;
            float border = smoothstep(0.7, 1.0, d);
            
            vec3 color = mix(uColor.rgb * 0.5, uColor.rgb * 1.5, border * pulse);
            float alpha = uColor.a;
            
            return vec4(color * alpha, alpha);
        }
    """.trimIndent()

    override fun MaterialEffect.onSetup() {
        uniform(Material.COLOR, baseColor)
    }
}

class GlitchMaterial(val baseColor: Color, val context: ParticleContext) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uTime;
        uniform float uProgress;
        uniform vec4 uColor;
        
        vec4 main(vec2 uv) {
            float glitch = step(0.95, fract(sin(uTime * 20.0 + uv.y * 10.0)));
            vec2 st = uv;
            if (glitch > 0.5) st.x += 0.05 * sin(uTime * 50.0);
            
            vec2 centered = st * 2.0 - 1.0;
            float d = max(abs(centered.x), abs(centered.y));
            
            float alpha = (1.0 - uProgress) * step(d, 1.0);
            return vec4(uColor.rgb * alpha, alpha);
        }
    """.trimIndent()

    override fun MaterialEffect.onSetup() {
        uniform(Material.COLOR, baseColor)
    }
    override fun MaterialEffect.onUpdate() {
        uniform(Material.PROGRESS, context.progress)
    }
}

// --- 2. State & Components ---

private class ScoreState {
    var score by mutableIntStateOf(0)
    var combo by mutableIntStateOf(0)
    var comboTimer by mutableFloatStateOf(0f)

    fun update(dt: Float) {
        if (comboTimer > 0) {
            comboTimer -= dt
            if (comboTimer <= 0) {
                combo = 0
            }
        }
    }
}

private class Trail(val maxLength: Int = 20) : Component<Trail> {
    val positions = mutableListOf<Offset>()
    override fun type() = Trail
    companion object : ComponentType<Trail>()
}

private class PlayerTag : Component<PlayerTag> {
    override fun type() = PlayerTag
    companion object : ComponentType<PlayerTag>()
}

private class EnemyTag(var isBoss: Boolean = false) : Component<EnemyTag> {
    override fun type() = EnemyTag
    companion object : ComponentType<EnemyTag>()
}

// --- 3. Systems ---

private class PlayerControlSystem(
    private val input: InputManager = inject()
) : IteratingSystem(family { all(PlayerTag, Transform, Movement, Trail) }) {
    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val movement = entity[Movement]
        val transform = entity[Transform]
        val trail = entity[Trail]
        
        var dx = 0f; var dy = 0f
        if (input.isKeyDown(Key.W) || input.isKeyDown(Key.DirectionUp)) dy -= 1f
        if (input.isKeyDown(Key.S) || input.isKeyDown(Key.DirectionDown)) dy += 1f
        if (input.isKeyDown(Key.A) || input.isKeyDown(Key.DirectionLeft)) dx -= 1f
        if (input.isKeyDown(Key.D) || input.isKeyDown(Key.DirectionRight)) dx += 1f

        if (dx != 0f || dy != 0f) {
            // Record current position for trail before moving
            trail.positions.add(0, transform.position)
            if (trail.positions.size > trail.maxLength) trail.positions.removeLast()
            
            movement.step(transform, dx, dy, deltaTime)
        } else if (trail.positions.isNotEmpty()) {
            trail.positions.removeLast()
        }
    }
}

private class CombatSystem(
    private val scoreState: ScoreState = inject(),
    private val particleService: ParticleService = inject(),
    private val cameraService: CameraService = inject(),
    private val animationService: AnimationService = inject()
) : IteratingSystem(family { all(PlayerTag, Transform) }) {

    private val enemyFamily = world.family { all(EnemyTag, Transform) }


    override fun onTick(deltaTime: Float) {
        scoreState.update(deltaTime)
        super.onTick(deltaTime)
    }

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val pPos = entity[Transform].position
        val pSize = 40f

        enemyFamily.forEach { enemy ->
            val ePos = enemy[Transform].position
            val eTag = enemy[EnemyTag]
            val eSize = if (eTag.isBoss) 80f else 30f
            
            if ((pPos - ePos).getDistance() < (pSize + eSize) / 2f) {
                // Kill Enemy
                particleService.emit { explosionEffect(ePos + Offset(eSize/2, eSize/2), if(eTag.isBoss) Color.Yellow else Color.Red) }
                cameraService.director.shake(if(eTag.isBoss) 0.8f else 0.4f)
                
                scoreState.score += if(eTag.isBoss) 50 else 10
                scoreState.combo++
                scoreState.comboTimer = 2.0f
                
                enemy.remove()
                world.entity { spawnEnemy(Random.nextFloat() < 0.1f) }
                
                // Visual bounce for player
                animationService.play(entity, "hit_bounce")
            }
        }
    }
}

private fun ParticleNodeScope.explosionEffect(center: Offset, hitColor: Color) {
    layer("bits", center) {
        config { count = 50; duration = 0.8f; material = GlitchMaterial(hitColor, context) }
        val angle = math.random(0f, 360f)
        val rad = math.toRadians(angle)
        val dist = math.random(50f, 300f) * ops.smoothstep(0f, 1f, env.progress)
        position = vec2(math.cos(rad) * dist, math.sin(rad) * dist)
        size = math.random(5f, 15f)
    }
}

// --- 4. Visuals ---

private class NeonBoxVisual(
    val color: Color, 
    size: Size,
    private val trail: Trail? = null,
    private val currentTransform: Transform? = null
) : Visual(size) {
    private val effect = MaterialEffect(NeonBoxMaterial(color)).apply {
        setResolution(size)
    }

    override fun DrawScope.draw() {
        // 1. Draw High-Quality Segmented Trail (Tapered & Glow)
        if (trail != null && currentTransform != null && trail.positions.isNotEmpty()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            var lastPos = Offset(centerX, centerY)
            
            trail.positions.fastForEachIndexed { index, pos ->
                // fraction goes from 1.0 (head) to 0.0 (tail)
                val fraction = 1f - (index.toFloat() / trail.maxLength)
                val relOffset = pos - currentTransform.position
                val currentPos = Offset(relOffset.x + centerX, relOffset.y + centerY)
                
                // Outer Glow Pass
                drawLine(
                    color = color.copy(alpha = 0.15f * fraction),
                    start = lastPos,
                    end = currentPos,
                    strokeWidth = size.width * 0.9f * fraction,
                    cap = StrokeCap.Round
                )
                
                // Inner Core Pass
                drawLine(
                    color = color.copy(alpha = 0.45f * fraction),
                    start = lastPos,
                    end = currentPos,
                    strokeWidth = size.width * 0.4f * fraction,
                    cap = StrokeCap.Round
                )
                
                lastPos = currentPos
            }
        }

        // 2. Draw Main Body
        if (effect.ready) {
            drawRect(brush = effect.obtainBrush())
        } else {
            drawRect(color = color)
        }
    }
}

// --- 5. Spawn Helpers ---

private fun EntityCreateScope.spawnPlayer() {
    val transform = Transform(Offset.Zero)
    val trail = Trail(20)
    +transform
    +trail
    +PlayerTag()
    +Movement(500f, 500f)
    +Renderable(NeonBoxVisual(Color(0xFF00E5FF), Size(40f, 40f), trail, transform), zIndex = 10)
    +ScaleAnimation("hit_bounce", from = 1.4f, to = 1.0f, spec = Spring(stiffness = 600f, damping = 12f))
}

private fun EntityCreateScope.spawnEnemy(isBoss: Boolean = false) {
    val size = if (isBoss) 80f else 30f
    +Transform(Offset(Random.nextFloat() * 2000 - 1000, Random.nextFloat() * 1500 - 750))
    +EnemyTag(isBoss)
    +RigidBody()
    +Movement(if(isBoss) 100f else 180f).apply {
        applyDirection(Random.nextFloat() * 2 - 1, Random.nextFloat() * 2 - 1)
    }
    +Renderable(NeonBoxVisual(if(isBoss) Color.Yellow else Color.Red, Size(size, size)), zIndex = 5)
}

// --- 6. Main Game Entry ---

@Composable
fun PixelBattleGameDemo() {
    val sceneStack = rememberGameSceneStack("main")
    val scoreState = remember { ScoreState() }

    KGame(sceneStack = sceneStack, virtualSize = Size(1280f, 720f)) {
        scene("main") {
            onWorld {
                configure {
                    injectables {
                        +scoreState
                    }
                    systems {
                        +PlayerControlSystem()
                        +CombatSystem()
                        +PhysicsSystem(gravity = Offset.Zero)
                        +ParticleRenderSystem()
                        +CameraSystem()
                        +AnimationSystem()
                        +RenderSystem()
                    }
                }

                spawn {
                    entity {
                        +Transform(Offset(-2500f, -2500f))
                        +Renderable(object : Visual(Size(5000f, 5000f)) {
                            override fun DrawScope.draw() {
                                val step = 100f
                                for (i in 0..(size.width / step).toInt()) {
                                    drawLine(Color.Cyan.copy(0.05f), Offset(i * step, 0f), Offset(i * step, size.height))
                                    drawLine(Color.Cyan.copy(0.05f), Offset(0f, i * step), Offset(size.width, i * step))
                                }
                            }
                        }, zIndex = -100)
                    }

                    val player = entity { spawnPlayer() }
                    entity {
                        +Transform()
                        +Elasticity()
                        +Movement()
                        +CameraTarget(player)
                        +CameraShake()
                        +Camera("main", isMain = true)
                    }

                    repeat(40) { entity { spawnEnemy(Random.nextFloat() < 0.05f) } }
                }
            }

            onForegroundUI {
                Column(Modifier.padding(40.dp)) {
                    Text("SCORE: ${scoreState.score}", color = Color.White, fontSize = 40.sp, style = androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                    if (scoreState.combo > 1) {
                        Text("COMBO X${scoreState.combo}!", color = Color.Yellow, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}
