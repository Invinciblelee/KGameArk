@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package com.example.kgame.games.gravity

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgame.ecs.*
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.KGame
import com.kgame.engine.core.rememberGameSceneStack
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.Rectangle
import com.kgame.plugins.components.*
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.systems.*
import com.kgame.plugins.visuals.Visual
import com.kgame.plugins.visuals.shapes.CircleVisual
import org.intellij.lang.annotations.Language
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

// --- 1. Constants & Tags ---

private object Config {
    const val VIRTUAL_W = 800f
    const val VIRTUAL_H = 800f
    const val SPAWN_RADIUS = 550f
}

private data object CoreTag : EntityTag()
private data object GravityShipTag : EntityTag()
private data object DarkMatterTag : EntityTag()

class GravityState {
    var coreHealth by mutableFloatStateOf(100f)
    var score by mutableIntStateOf(0)
    var isGameOver by mutableStateOf(false)
    var pendingReset by mutableStateOf(false)
}

// --- 2. Materials ---

class BlackHoleMaterial : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uTime;
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);
            float ring = smoothstep(0.4, 0.45, d) * smoothstep(0.6, 0.55, d);
            float hole = smoothstep(0.4, 0.38, d);
            vec3 color = mix(vec3(0.0), vec3(0.5, 0.2, 1.0), ring);
            color += sin(uTime * 5.0 + d * 10.0) * 0.1;
            return vec4(color, ring + hole);
        }
    """.trimIndent()
}

// --- 3. Systems ---

private class GravitySystem(
    private val state: GravityState = inject(),
    private val input: InputManager = inject(),
    private val camera: CameraService = inject(),
    private val particles: ParticleService = inject()
) : IntervalSystem() {

    private val matters = world.family { all(DarkMatterTag, Transform, FollowTarget) }
    private val shipFamily = world.family { all(GravityShipTag, Transform) }
    private val coreFamily = world.family { all(CoreTag, Transform) }

    override fun onAwake() {
        performReset()
    }

    override fun onTick(deltaTime: Float) {
        if (state.pendingReset) {
            performReset()
            state.pendingReset = false
            return
        }

        if (state.isGameOver) return

        val ship = shipFamily.firstOrNull() ?: return
        val core = coreFamily.firstOrNull() ?: return
        
        val mousePos = camera.transformer.virtualToWorld(input.getPointerPosition(0))
        ship[Transform].position = mousePos

        val isPulling = input.isPointerDown(0)
        val shipPos = ship[Transform].position
        val corePos = core[Transform].position

        matters.forEach { matter ->
            val mTrans = matter[Transform]
            val distToShip = (shipPos - mTrans.position).getDistance()
            val distToCore = (corePos - mTrans.position).getDistance()

            // 数据驱动：通过修改 FollowTarget 切换引力目标，不再每帧 new Arriver
            val follow = matter[FollowTarget]
            if (isPulling && distToShip < 280f) {
                if (follow.actor != ship) {
                    matter.configure { +FollowTarget(ship) }
                }
                matter[Arriver].speed = 750f
            } else {
                if (follow.actor != core) {
                    matter.configure { +FollowTarget(core) }
                }
                matter[Arriver].speed = 130f
            }

            // 核心碰撞判定 (基于中心点距离)
            if (distToCore < 50f) {
                state.coreHealth -= 8f
                camera.director.shake(trauma = 0.4f, traumaDecay = 8.0f)
                particles.emit { hitEffect(mTrans.position, Color.Magenta) }
                matter.remove()
            }
        }

        if (state.coreHealth <= 0) {
            state.coreHealth = 0f
            state.isGameOver = true
        }
    }

    private fun performReset() {
        world.family { all(DarkMatterTag) }.forEach { it.remove() }
        state.coreHealth = 100f
        state.score = 0
        state.isGameOver = false
    }
}

private class MatterSpawnerSystem(
    private val state: GravityState = inject()
) : IntervalSystem(interval = Fixed(1.0f)) {
    
    private val coreEntity by lazy { world.family { all(CoreTag) }.first() }

    override fun onTick(deltaTime: Float) {
        if (state.isGameOver || world.family { all(DarkMatterTag) }.entitySize > 50) return
        
        val angle = (0f..360f).random()
        val rad = angle * 0.017453292f
        val startPos = Offset(cos(rad) * Config.SPAWN_RADIUS, sin(rad) * Config.SPAWN_RADIUS)

        world.entity {
            +DarkMatterTag
            +Transform(startPos)
            +RigidBody(drag = 0.3f, maxSpeed = 500f)
            +Arriver(speed = 130f, slowDownRadius = 0f) // 预设 Arriver，避免在 Tick 中 new
            +FollowTarget(coreEntity) // 默认飞向核心
            +Hitbox(Rect(Offset(-12f, -12f), Size(24f, 24f)))
            +Renderable(Visual(24f) {
                drawRect(Color(0xFFBB86FC), style = Stroke(3f))
                drawRect(Color(0xFFBB86FC).copy(0.15f))
            })
        }
    }
}

private fun ParticleNodeScope.hitEffect(pos: Offset, col: Color) {
    layer("p", pos) {
        config { count = 15; duration = 0.4f }
        val r = (0f..360f).random() * 0.0174f
        position = vec2(math.cos(scalar(r)) * 180f * env.progress, math.sin(scalar(r)) * 180f * env.progress)
        size = scalar(12f) * (1f - env.progress)
        color = color(col.red, col.green, col.blue, 1f - env.progress)
    }
}

// --- 4. Entry ---

@Composable
fun GravityDefenseGame() {
    val sceneStack = rememberGameSceneStack<String>("play")
    val state = remember { GravityState() }

    KGame(sceneStack = sceneStack, virtualSize = Size(Config.VIRTUAL_W, Config.VIRTUAL_H)) {
        scene("play") {
            onWorld {
                useDefaultSystems()
                configure {
                    injectables { add(state) }
                    systems {
                        +GravitySystem()
                        +MatterSpawnerSystem()
                        +SteeringSystem() // 使用你的转向系统
                        +CollisionSystem() // 使用你的物理碰撞系统
                    }
                }
                spawn {
                    entity {
                        +CoreTag
                        +Transform() 
                        +Renderable(CircleVisual(100f, Color.White))
                    }

                    entity {
                        +GravityShipTag
                        +Transform(Offset(-200f, -200f))
                        +Renderable(Visual(36f) {
                            drawCircle(Color.Cyan, style = Stroke(4f))
                            drawCircle(Color.White, radius = 5f)
                        }, zIndex = 20)
                    }

                    entity {
                        +Transform()
                        +Camera("main", isMain = true)
                        +CameraShake(traumaDecay = 8.0f)
                    }
                }
            }

            onBackgroundUI { Rectangle(Color(0xFF050505)) }

            onForegroundUI {
                Box(Modifier.fillMaxSize().padding(40.dp)) {
                    Column {
                        Text("CORE INTEGRITY", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { state.coreHealth / 100f },
                            modifier = Modifier.width(240.dp).height(12.dp),
                            color = Color(0xFFBB86FC),
                            trackColor = Color.White.copy(0.05f)
                        )
                    }

                    if (state.isGameOver) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.Black.copy(0.9f),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Column(Modifier.padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CRITICAL COLLAPSE", color = Color.Red, fontSize = 32.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = { state.pendingReset = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                                ) {
                                    Text("RESTORE EQUILIBRIUM", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
