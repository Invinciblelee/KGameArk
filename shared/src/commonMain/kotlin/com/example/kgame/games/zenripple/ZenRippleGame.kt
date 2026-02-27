@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package com.example.kgame.games.zenripple

import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgame.ecs.*
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.KGame
import com.kgame.engine.core.rememberGameSceneStack
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.Rectangle
import com.kgame.plugins.components.*
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.visuals.Visual
import com.kgame.plugins.visuals.shapes.CircleVisual
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

// --- 1. Custom Components & Tags ---

private data object DustTag : EntityTag()
private data object ZenStoneTag : EntityTag()

// --- 2. Systems ---

private class ZenRippleSystem(
    private val input: InputManager = inject(),
    private val camera: CameraService = inject(),
    private val particle: ParticleService = inject()
) : IntervalSystem() {

    private val dustFamily = world.family { all(DustTag, RigidBody) }
    private val zenStoneFamily = world.family { all(ZenStoneTag) }

    override fun onTick(deltaTime: Float) {
        if (input.isMouseJustPressed(0)) {
            val worldPos = camera.transformer.virtualToWorld(input.getPointerPosition())
            
            // Clear old stones
            zenStoneFamily.forEach { it.remove() }

            // Spawn the "Gravity" Stone
            val stone = world.entity {
                +ZenStoneTag
                +Transform(worldPos)
                +Renderable(Visual(60f) {
                    drawCircle(Color.White, radius = size.width / 2, style = Stroke(4f))
                    drawCircle(Color.Cyan.copy(alpha = 0.2f), radius = size.width / 3)
                }, zIndex = 10)
                +ScaleAnimation("pulse", 0.8f, 1.2f, spec = InfiniteRepeatable(Tween(1f), RepeatMode.Reverse), autoPlay = true)
            }

            camera.director.shake(0.2f)
            particle.emit { stoneRipple(worldPos) }

            // Order: All dusts follow the stone
            dustFamily.forEach { dust ->
                dust.configure {
                    +FollowTarget(stone)
                    +Elasticity(stiffness = 180f, damping = 12f)
                    -Wander 
                }
            }
        }

        if (input.isMouseJustPressed(1) || input.isKeyJustPressed(Key.Spacebar)) {
            zenStoneFamily.forEach { it.remove() }
            dustFamily.forEach { dust ->
                dust.configure {
                    -FollowTarget
                    -Elasticity
                    +Wander(distance = 120f, radius = 100f, jitter = 60f)
                }
            }
        }
    }
}

private fun ParticleNodeScope.stoneRipple(center: Offset) {
    layer("ripple", center) {
        config { count = 1; duration = 0.6f }
        size = scalar(20f) + env.progress * 500f
        color = color(0f, 1f, 1f, (1f - env.progress) * 0.3f)
    }
}

// --- 3. Scene and Game Entry ---

@Composable
fun ZenRippleGame() {
    val sceneStack = rememberGameSceneStack("main")

    KGame(sceneStack = sceneStack, virtualSize = Size(800f, 800f)) {
        scene("main") {
            onWorld {
                useDefaultSystems()

                configure {
                    systems {
                        +ZenRippleSystem()
                    }
                }

                spawn {
                    repeat(200) {
                        entity {
                            +DustTag
                            +Transform(Offset((0f..800f).random(), (0f..800f).random()))
                            +RigidBody(drag = 1.5f, maxSpeed = 400f)
                            +Wander(distance = 150f, radius = 100f, jitter = 80f)

                            val col = Color(
                                red = (0.5f..1f).random(),
                                green = (0.8f..1f).random(),
                                blue = 1f,
                                alpha = (0.4f..0.8f).random()
                            )

                            +Renderable(CircleVisual(8f, col))
                        }
                    }

                    entity {
                        +Transform(Offset(400f, 400f))
                        +Camera("main", isMain = true)
                        +CameraShake()
                    }
                }
            }

            onBackgroundUI { Rectangle(Color(0xFF0D1117)) }

            onForegroundUI {
                Box(Modifier.fillMaxSize().padding(40.dp)) {
                    Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ZEN RIPPLE", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
                        Text("TAP TO CREATE ORDER • SPACE TO RELEASE CHAOS", color = Color.Cyan.copy(0.5f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
