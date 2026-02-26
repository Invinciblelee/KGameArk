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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgame.ecs.*
import com.kgame.ecs.World.Companion.family
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
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.systems.*
import com.kgame.plugins.visuals.Visual
import org.intellij.lang.annotations.Language
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

// --- 1. Custom Components ---

private data object AllyTag : EntityTag()
private data object EnemyTag : EntityTag()

class NeonState {
    var score by mutableIntStateOf(0)
    var alliesAlive by mutableIntStateOf(0)
    var isGameOver by mutableStateOf(false)
    var pendingReset by mutableStateOf(false)
}

// --- 2. Shaders ---

class GlowMaterial(val color: Color) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform vec4 uColor;
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);
            float glow = exp(-d * 4.0);
            return vec4(uColor.rgb * glow, glow * uColor.a);
        }
    """.trimIndent()
    override fun MaterialEffect.onSetup() { uniform(Material.COLOR, color) }
}

// --- 3. Systems ---

private class SwarmSystem(
    private val state: NeonState = inject(),
    private val input: InputManager = inject(),
    private val camera: CameraService = inject(),
    private val particles: ParticleService = inject()
) : IntervalSystem() {

    private val allies = world.family { all(AllyTag, Transform, RigidBody) }
    private val enemies = world.family { all(EnemyTag, Transform, RigidBody) }

    override fun onTick(deltaTime: Float) {
        if (state.pendingReset) {
            performReset()
            state.pendingReset = false
            return
        }

        if (state.isGameOver) return

        val targetPos = camera.transformer.virtualToWorld(input.pointerPosition)
        
        // 1. Allies follow mouse
        allies.forEach { ally ->
            ally.configure {
                +ArriveTarget(targetPos)
            }
        }
        state.alliesAlive = allies.size

        // 2. Enemy Spawning (Simple Logic)
        if (enemies.size < 15 && (0f..1f).random() < 0.05f) {
            spawnEnemy()
        }

        // 3. Simple AI & Collision
        enemies.forEach { enemy ->
            val ePos = enemy[Transform].position
            // Enemies chase the center of the swarm
            enemy.configure { +ArriveTarget(targetPos) }

            // Collision with allies
            allies.forEach { ally ->
                val aPos = ally[Transform].position
                if ((ePos - aPos).getDistance() < 20f) {
                    particles.emit { explosion(aPos, Color.Cyan) }
                    camera.director.shake(0.1f)
                    ally.remove()
                    enemy.remove()
                    state.score += 10
                }
            }
        }

        if (state.alliesAlive <= 0 && !state.isGameOver) {
            state.isGameOver = true
        }
    }

    private fun spawnEnemy() {
        world.entity {
            +EnemyTag
            +Transform(Offset((0f..800f).random(), (0f..800f).random()))
            +RigidBody(drag = 0.5f, maxSpeed = 200f)
            +Arriver(speed = 150f, slowDownRadius = 50f)
            +Renderable(object : Visual(Size(24f, 24f)) {
                override fun DrawScope.draw() {
                    drawRect(Color.Red, style = Stroke(2f))
                    drawRect(Color.Red.copy(0.2f))
                }
            })
        }
    }

    private fun performReset() {
        allies.forEach { it.remove() }
        enemies.forEach { it.remove() }
        repeat(150) {
            world.entity {
                +AllyTag
                +Transform(Offset((300f..500f).random(), (300f..500f).random()))
                +RigidBody(drag = 1.2f, maxSpeed = 600f)
                +Arriver(speed = 400f, slowDownRadius = 150f)
                +Renderable(object : Visual(Size(10f, 10f)) {
                    override fun DrawScope.draw() { drawCircle(Color.Cyan) }
                })
            }
        }
        state.isGameOver = false
        state.score = 0
    }
}

private fun ParticleNodeScope.explosion(pos: Offset, color: Color) {
    layer("boom", pos) {
        config { count = 20; duration = 0.5f }
        val angle = math.random(0f, 360f)
        val rad = math.toRadians(angle)
        val speed = math.random(100f, 300f)
        position = vec2(math.cos(rad) * speed * env.progress, math.sin(rad) * speed * env.progress)
        size = scalar(10f) * (1f - env.progress)
        this.color = color(color.red, color.green, color.blue, 1f - env.progress)
    }
}

// --- 4. Scene ---

@Composable
fun SwarmBattleGame() {
    val sceneStack = rememberGameSceneStack<String>("main")
    val state = remember { NeonState() }

    KGame(sceneStack = sceneStack, virtualSize = Size(800f, 800f)) {
        scene("main") {
            world {
                useDefaultSystems()
                configure {
                    injectables { +state }
                    systems { +SwarmSystem() }
                }
                spawn {
                    entity {
                        +Transform(Offset(400f, 400f))
                        +Camera("main", isMain = true)
                        +CameraShake()
                    }
                    state.pendingReset = true
                }
            }

            onBackgroundUI { Rectangle(Color(0xFF05070A)) }

            onForegroundUI {
                Box(Modifier.fillMaxSize().padding(30.dp)) {
                    Column {
                        Text("NEON SWARM", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text("Score: ${state.score}", color = Color.Cyan, fontSize = 18.sp)
                        Text("Units: ${state.alliesAlive}", color = Color.Gray, fontSize = 14.sp)
                    }

                    if (state.isGameOver) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CORE DISRUPTED", color = Color.Red, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = { state.pendingReset = true }) {
                                Text("REBOOT SYSTEM")
                            }
                        }
                    }
                }
            }
        }
    }
}
