@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package com.example.kgame.games.rpg

import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kgame.games.GameAssets
import com.kgame.ecs.*
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.KGame
import com.kgame.engine.core.rememberGameSceneStack
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.Rectangle
import com.kgame.plugins.components.*
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.systems.*
import com.kgame.plugins.visuals.Visual
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

// --- 1. Custom Components ---

private data object PlayerTag : EntityTag()
private data object StarTag : EntityTag()
private data object EnemyTag : EntityTag()

class GameState {
    var health by mutableFloatStateOf(100f)
    var starsCollected by mutableIntStateOf(0)
    var isGameOver by mutableStateOf(false)
}

// --- 2. Systems ---

private class HeroSystem(
    private val input: InputManager = inject(),
    private val particle: ParticleService = inject(),
    private val state: GameState = inject()
) : IteratingSystem(family { all(PlayerTag, Transform, Movement) }) {

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        if (state.isGameOver) return
        
        val trans = entity[Transform]
        val move = entity[Movement]
        
        var dx = 0f; var dy = 0f
        if (input.isKeyDown(Key.W) || input.isKeyDown(Key.DirectionUp)) dy -= 1f
        if (input.isKeyDown(Key.S) || input.isKeyDown(Key.DirectionDown)) dy += 1f
        if (input.isKeyDown(Key.A) || input.isKeyDown(Key.DirectionLeft)) dx -= 1f
        if (input.isKeyDown(Key.D) || input.isKeyDown(Key.DirectionRight)) dx += 1f

        if (dx != 0f || dy != 0f) {
            move.step(trans, dx, dy, deltaTime)
            // Trail particles
            if ((0f..1f).random() < 0.15f) {
                particle.emit {
                    layer("trail", trans.position + Offset(0f, 10f)) {
                        config { count = 1; duration = 0.4f }
                        size = scalar(12f) * (1f - env.progress)
                        color = color(0f, 1f, 1f, 0.3f * (1f - env.progress))
                    }
                }
            }
        }
    }
}

private class WorldInteractionSystem(
    private val state: GameState = inject(),
    private val camera: CameraService = inject(),
    private val particle: ParticleService = inject()
) : IntervalSystem() {
    
    private val playerFamily = world.family { all(PlayerTag, Transform) }
    private val starFamily = world.family { all(StarTag, Transform) }
    private val enemyFamily = world.family { all(EnemyTag, Transform) }

    override fun onTick(deltaTime: Float) {
        if (state.isGameOver) return
        
        val player = playerFamily.firstOrNull() ?: return
        val pPos = player[Transform].position

        // 1. Star Collection
        starFamily.forEach { star ->
            val sPos = star[Transform].position
            if ((pPos - sPos).getDistance() < 45f) {
                star.remove()
                state.starsCollected++
                particle.emit {
                    layer("flare", sPos) {
                        config { count = 8; duration = 0.5f }
                        val ang = math.random(0f, 360f); val r = math.toRadians(ang)
                        position = vec2(math.cos(r) * 120f * env.progress, math.sin(r) * 120f * env.progress)
                        size = scalar(8f) * (1f - env.progress)
                        color = color(1f, 0.8f, 0f, 1f - env.progress)
                    }
                }
            }
        }

        // 2. Enemy Interaction
        enemyFamily.forEach { enemy ->
            val ePos = enemy[Transform].position
            if ((pPos - ePos).getDistance() < 35f) {
                state.health -= 25f * deltaTime
                camera.director.shake(0.2f)
                if (state.health <= 0) {
                    state.health = 0f
                    state.isGameOver = true
                }
            }
        }
    }
}

// --- 3. Main Entry ---

@Composable
fun StarCollectorGame() {
    val sceneStack = rememberGameSceneStack<String>("play")
    val state = remember { GameState() }

    KGame(sceneStack = sceneStack, virtualSize = Size(800f, 800f)) {
        scene("play") {
            onWorld {
                useDefaultSystems()
                configure {
                    injectables { +state }
                    systems {
                        +HeroSystem()
                        +WorldInteractionSystem()
                        +SteeringSystem()
                    }
                }


                spawn {
                    val worldBounds = Rect(-640f, -600f, 640f, 600f)

                    // Tiled Map Base
                    entity {
                        +TiledMap(assets[GameAssets.TiledMap.Example])
                    }

                    // The Player
                    val hero = entity {
                        +PlayerTag
                        +Transform()
                        +Movement(320f, 320f)
                        +Renderable(object : Visual(Size(44f, 44f)) {
                            override fun DrawScope.draw() {
                                drawRect(Color(0xFF00E5FF), style = Stroke(4f))
                                drawRect(Color(0xFF00E5FF).copy(0.15f))
                                // Core
                                drawCircle(Color.White, radius = 6f, center = Offset(22f, 22f))
                            }
                        }, zIndex = 10)
                    }

                    // Camera follow
                    entity {
                        +Transform()
                        +Camera("main", isMain = true, bounds = worldBounds)
                        +CameraTarget(hero)
                        +Elasticity(stiffness = 180f, damping = 15f)
                        +CameraShake()
                    }

                    // Stars
                    repeat(25) {
                        entity {
                            +StarTag
                            +Transform(Offset((50f..1950f).random(), (50f..1950f).random()))
                            +Renderable(object : Visual(Size(20f, 24f)) {
                                override fun DrawScope.draw() {
                                    drawCircle(Color(0xFFFFD600), radius = 8f)
                                    drawCircle(Color.White, radius = 3f)
                                }
                            })
                            +ScaleAnimation("pulse", 0.7f, 1.3f, spec = InfiniteRepeatable(Tween(0.4f), RepeatMode.Reverse), autoPlay = true)
                        }
                    }

                    // Enemies
                    repeat(10) {
                        entity {
                            +EnemyTag
                            +Transform(Offset((0f..2000f).random(), (0f..2000f).random()))
                            +RigidBody(drag = 0.8f, maxSpeed = 180f)
                            +Wander(distance = 120f, radius = 80f, jitter = 50f)
                            +Renderable(object : Visual(Size(36f, 36f)) {
                                override fun DrawScope.draw() {
                                    drawRect(Color(0xFFFF1744), style = Stroke(3f))
                                    drawRect(Color(0xFFFF1744).copy(0.1f))
                                }
                            })
                        }
                    }
                }
            }

            onCreate {
                assets.load(GameAssets.TiledMap.Example)
            }

            onBackgroundUI { Rectangle(Color(0xFF05070A)) }

            onForegroundUI {
                Box(Modifier.fillMaxSize().padding(32.dp)) {
                    Column(Modifier.align(Alignment.TopStart)) {
                        Text("POWER GRID", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { state.health / 100f },
                            modifier = Modifier.width(180.dp).height(10.dp),
                            color = if (state.health > 40) Color(0xFF00E676) else Color(0xFFFF5252),
                            trackColor = Color.White.copy(0.05f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("CORES: ${state.starsCollected}", color = Color(0xFFFFD600), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    if (state.isGameOver) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.Black.copy(0.9f),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Column(Modifier.padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SYSTEM BREACH", color = Color(0xFFFF1744), fontSize = 36.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(8.dp))
                                Text("RECOVERY DATA: ${state.starsCollected} UNITS", color = Color.Gray)
                                Spacer(Modifier.height(40.dp))
                                Button(
                                    onClick = { 
                                        state.health = 100f
                                        state.starsCollected = 0
                                        state.isGameOver = false
                                        sceneStack.push("play") 
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                                ) {
                                    Text("INITIATE REBOOT", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
