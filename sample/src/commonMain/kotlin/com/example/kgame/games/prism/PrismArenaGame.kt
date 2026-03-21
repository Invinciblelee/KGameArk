@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package com.example.kgame.games.prism

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgame.ecs.EntityTag
import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.KSimpleGame
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.Rectangle
import com.kgame.plugins.components.Arriver
import com.kgame.plugins.components.Boundary
import com.kgame.plugins.components.BoundaryStrategy
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.FollowTarget
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.RigidBody
import com.kgame.plugins.components.Transform
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.systems.SteeringSystem
import com.kgame.plugins.visuals.Visual
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

// --- 1. Custom Tags ---

private data object PlayerTag : EntityTag()
private data object EnemyTag : EntityTag()
private data object BulletTag : EntityTag()

class PrismState {
    var score by mutableIntStateOf(0)
    var health by mutableFloatStateOf(100f)
    var isGameOver by mutableStateOf(false)
    var pendingReset by mutableStateOf(false)
}

// --- 2. Systems ---

private class PrismPlayerSystem(
    private val input: InputManager = inject(),
    private val camera: CameraService = inject(),
    private val state: PrismState = inject()
) : IntervalSystem() {
    private var shootTimer = 0f
    private val playerFamily = world.family { all(PlayerTag, Transform) }

    override fun onTick(deltaTime: Float) {
        if (state.isGameOver) return
        val player = playerFamily.firstOrNull() ?: return

        val mousePos = camera.transformer.virtualToWorld(input.getPointerPosition(0))
        player[Transform].position = mousePos

        shootTimer += deltaTime
        if (shootTimer >= 0.12f) {
            spawnBurst(mousePos)
            shootTimer = 0f
        }
    }

    private fun spawnBurst(pos: Offset) {
        for (i in -1..1) {
            val angle = i * 15f - 90f
            val rad = angle * 0.01745f
            val dir = Offset(cos(rad), sin(rad))

            world.entity {
                +BulletTag
                +Transform(pos)
                +RigidBody(drag = 0f, maxSpeed = 1500f).apply {
                    velocity = androidx.compose.ui.unit.Velocity(dir.x * 1200f, dir.y * 1200f)
                }
                +Renderable(Visual(10f) {
                    drawCircle(Color.White)
                    drawCircle(Color.Cyan.copy(0.4f), radius = 8f, style = Stroke(2f))
                }, zIndex = 5)
                +Boundary(strategy = BoundaryStrategy.Cleanup)
            }
        }
    }
}

private class PrismEnemySystem(
    private val state: PrismState = inject(),
    private val particles: ParticleService = inject(),
    private val camera: CameraService = inject()
) : IntervalSystem() {
    private val enemies = world.family { all(EnemyTag, Transform) }
    private val bullets = world.family { all(BulletTag, Transform) }
    private val playerFamily = world.family { all(PlayerTag, Transform) }

    override fun onTick(deltaTime: Float) {
        if (state.pendingReset) {
            enemies.forEach { it.remove() }
            bullets.forEach { it.remove() }
            state.health = 100f; state.score = 0; state.isGameOver = false
            state.pendingReset = false
            return
        }

        if (state.isGameOver) return

        val player = playerFamily.firstOrNull() ?: return
        val pPos = player[Transform].position

        if (enemies.entitySize < 15 + (state.score / 1000)) {
            spawnEnemy()
        }

        enemies.forEach { enemy ->
            val ePos = enemy[Transform].position

            bullets.forEach { bullet ->
                if ((bullet[Transform].position - ePos).getDistance() < 35f) {
                    bullet.remove()
                    enemy.remove()
                    state.score += 50
                    particles.emit { boom(ePos, Color.Magenta) }
                    camera.director.shake(trauma = 0.1f, traumaDecay = 12f)
                }
            }

            if ((ePos - pPos).getDistance() < 40f) {
                state.health -= 30f * deltaTime
                camera.director.shake(trauma = 0.3f, traumaDecay = 8f)
                if (state.health <= 0) state.isGameOver = true
            }
        }
    }

    private fun spawnEnemy() {
        val side = (0..1).random()
        val x = if (side == 0) (0f..200f).random() else (600f..800f).random()

        world.entity {
            +EnemyTag
            +Transform(Offset(x, -50f))
            +RigidBody(drag = 0.6f, maxSpeed = 350f)
            +Arriver(speed = 280f, slowDownRadius = 0f)
            +FollowTarget(playerFamily.first())
            +Renderable(Visual(32f) {
                drawRect(Color.Magenta, style = Stroke(3f))
                drawRect(Color.Magenta.copy(0.15f))
            })
        }
    }
}

private fun ParticleNodeScope.boom(pos: Offset, col: Color) {
    layer("spark", pos) {
        config { count = 15; duration = 0.5f }
        val r = (0f..360f).random() * 0.0174f
        position = vec2(math.cos(scalar(r)) * 250f * env.progress, math.sin(scalar(r)) * 250f * env.progress)
        size = scalar(14f) * (1f - env.progress)
        color = color(col.red, col.green, col.blue, 1f - env.progress)
    }
}

// --- 3. Game Entry ---

@Composable
fun PrismArenaGame() {
    val state = remember { PrismState() }

    KSimpleGame(virtualSize = Size(800f, 800f)) {
        onWorld {
            useDefaultSystems()
            configure {
                injectables { add(state) }
                systems {
                    +PrismPlayerSystem()
                    +PrismEnemySystem()
                    +SteeringSystem()
                }
            }
            spawn {
                // Player
                val hero = entity {
                    +PlayerTag
                    +Transform(Offset(400f, 700f))
                    +Renderable(Visual(48f) {
                        drawCircle(Color.Cyan, style = Stroke(5f))
                        drawCircle(Color.White, radius = 12f)
                    }, zIndex = 10)
                }

                // Camera
                entity {
                    +Transform(Offset(400f, 400f))
                    +Camera("main", isMain = true)
                    +CameraShake()
                }
            }
        }

        onBackgroundUI { Rectangle(Color(0xFF020305)) }

        onForegroundUI {
            Box(Modifier.fillMaxSize().padding(32.dp)) {
                Column {
                    Text("PRISM ARENA", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("SCORE: ${state.score}", color = Color.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.health / 100f },
                        modifier = Modifier.width(200.dp).height(10.dp),
                        color = Color.Green,
                        trackColor = Color.White.copy(0.1f)
                    )
                }

                if (state.isGameOver) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Black.copy(0.9f),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(Modifier.padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SYSTEM OVERRUN", color = Color.Red, fontSize = 32.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(8.dp))
                            Text("FINAL SCORE: ${state.score}", color = Color.Gray)
                            Spacer(Modifier.height(32.dp))
                            Button(
                                onClick = { state.pendingReset = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                            ) {
                                Text("RE-ENTER ARENA", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}