package com.example.kgame.games.survivor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgame.ecs.*
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.*
import com.kgame.engine.input.InputManager
import com.kgame.plugins.components.*
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.systems.SystemPriorityAnchors
import com.kgame.plugins.visuals.shapes.CircleVisual
import com.kgame.plugins.visuals.shapes.RectangleVisual
import kotlin.math.*
import kotlin.random.Random

// --- 1. Custom Components ---

class PlayerComp : Component<PlayerComp> {
    override fun type() = PlayerComp
    companion object : ComponentType<PlayerComp>()
}

class EnemyComp : Component<EnemyComp> {
    override fun type() = EnemyComp
    companion object : ComponentType<EnemyComp>()
}

class BulletComp : Component<BulletComp> {
    var life = 2.0f
    override fun type() = BulletComp
    companion object : ComponentType<BulletComp>()
}

class SurvivorData : Component<SurvivorData> {
    var score by mutableIntStateOf(0)
    var health by mutableFloatStateOf(100f)
    override fun type() = SurvivorData
    companion object : ComponentType<SurvivorData>()
}

// --- 2. Custom Systems ---

/**
 * PlayerControlSystem: Handles movement and directional shooting in a center-based coordinate system.
 */
class PlayerControlSystem : IteratingSystem(
    family = family { all(PlayerComp, Transform) },
    priority = SystemPriorityAnchors.Logic
) {
    private val camera = inject<CameraService>()
    private val input = inject<InputManager>()

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val t = entity[Transform]
        val speed = 350f
        
        // 1. Update player position in the center-based world coordinate system
        t.x += input.getAxis(Key.D, Key.A) * speed * deltaTime
        t.y += input.getAxis(Key.S, Key.W) * speed * deltaTime


        // 2. Shooting control: Calculate bullet direction based on mouse position
        if (input.isPointerJustPressed()) {
            val target = camera.transformer.virtualToWorld(input.getPointerPosition())

            world.entity {
                +Transform(position = t.position) // Emit from player position
                +BulletComp()
                // Set Movement direction vector, bullet path determined by degrees
                +Movement(
                    cruiseX = 900f,
                    cruiseY = 900f,
                    degrees = t.degreesTo(target)
                )
                +Renderable(CircleVisual(size = 10f, color = Color.Yellow))
            }
        }
    }
}

/**
 * Enemy AI System
 */
class EnemyAISystem : IteratingSystem(
    family = family { all(EnemyComp, Transform, Movement) },
    priority = SystemPriorityAnchors.Logic
) {
    private val players = family { all(PlayerComp, Transform) }

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        if (players.isEmpty) return
        
        val pt = players.first()[Transform]
        val et = entity[Transform]
        val m = entity[Movement]
        
        // Calculate direction vector towards player
        val angle = atan2(pt.y - et.y, pt.x - et.x)
        m.dirX = cos(angle)
        m.dirY = sin(angle)
    }
}

/**
 * Collision System
 */
class BulletCollisionSystem : IteratingSystem(
    family = family { all(BulletComp, Transform) },
    priority = SystemPriorityAnchors.Logic
) {
    private val enemies = family { all(EnemyComp, Transform) }
    private val data = inject<SurvivorData>()
    private val particle = inject<ParticleService>()

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val b = entity[BulletComp]
        val bt = entity[Transform]
        
        b.life -= deltaTime
        if (b.life <= 0) {
            entity.remove()
            return
        }

        enemies.forEach { enemy: Entity ->
            val et = enemy[Transform]
            val distSq = (bt.x - et.x).pow(2) + (bt.y - et.y).pow(2)
            
            if (distSq < 1600f) { // 40px radius squared
                enemy.remove()
                entity.remove()
                data.score += 10
                
                // Particle Explosion: Produces a diffusion effect at the enemy's position
                particle.emit {
                    layer("explosion", origin = et.position) {
                        config { count = 20; duration = 0.4f }
                        position = vec2(math.random(-60f, 60f), math.random(-60f, 60f)) * env.progress
                        val base = color(1f, 0.6f, 0f, 1f)
                        color = color(base[0], base[1], base[2], 1f - env.progress)
                        size = 10f * (1f - env.progress)
                    }
                }
            }
        }
    }
}

/**
 * Simple Physics System for basic movement processing
 */
class SimplePhysicsSystem : IteratingSystem(
    family = family { all(Transform, Movement) },
    priority = SystemPriorityAnchors.Physics
) {
    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val t = entity[Transform]
        val m = entity[Movement]
        // Apply velocity vector to world coordinates
        t.x += m.velocityX * deltaTime
        t.y += m.velocityY * deltaTime
    }
}

/**
 * Enemy Spawner System
 */
class SpawnerSystem : IntervalSystem(
    interval = Fixed(1.0f),
    priority = SystemPriorityAnchors.Logic
) {
    override fun onTick(deltaTime: Float) {
        val side = Random.nextInt(4)
        var sx = 0f; var sy = 0f
        when(side) {
            // Spawn outside the virtual screen center coordinate system [-640, 640] x [-360, 360]
            0 -> { sx = Random.nextFloat() * 1280f - 640f; sy = -400f }
            1 -> { sx = 700f; sy = Random.nextFloat() * 720f - 360f }
            2 -> { sx = Random.nextFloat() * 1280f - 640f; sy = 400f }
            3 -> { sx = -700f; sy = Random.nextFloat() * 720f - 360f }
        }
        
        world.entity {
            +Transform(position = Offset(sx, sy))
            +EnemyComp()
            +Movement(cruiseX = 150f, cruiseY = 150f)
            +Renderable(RectangleVisual(size = 45f, color = Color.Red))
            +Hitbox(Rect(Offset(-22.5f, -22.5f), Size(45f, 45f)))
        }
    }
}

// --- 3. Routes and Entry Point ---

sealed class SurvivorRoute {
    data object Menu : SurvivorRoute()
    data object Battle : SurvivorRoute()
}

@Composable
fun SurvivorGameDemo() {
    val sceneStack = rememberGameSceneStack<SurvivorRoute>(SurvivorRoute.Menu)
    val survivorData = remember { SurvivorData() }

    KGame(
        sceneStack = sceneStack,
        virtualSize = Size(1280f, 720f)
    ) {
        scene<SurvivorRoute.Menu> { _ ->
            onForegroundUI {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF111111)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("K-SURVIVOR", color = Color.White, fontSize = 80.sp)
                    Spacer(Modifier.height(40.dp))
                    Button(onClick = { 
                        survivorData.score = 0
                        sceneStack.push(SurvivorRoute.Battle) 
                    }) {
                        Text("START SURVIVING", fontSize = 24.sp)
                    }
                }
            }
        }

        scene<SurvivorRoute.Battle> { _ ->
            onWorld(capacity = 2048) {
                useDefaultSystems()
                configure {
                    injectables { +survivorData }
                    systems {
                        +PlayerControlSystem()
                        +EnemyAISystem()
                        +BulletCollisionSystem()
                        +SimplePhysicsSystem()
                        +SpawnerSystem()
                    }
                }
                spawn {
                    entity {
                        +WorldBounds(Rect(
                            Offset(-640f, -360f),
                            Offset(640f, 360f)
                        ))
                        +GlobalTag
                    }

                    entity {
                        +PlayerComp()
                        +Transform() // Player starts at the center of the screen
                        +Boundary(margin = -20f, strategy = BoundaryStrategy.Clamp)
                        +Renderable(CircleVisual(size = 40f, color = Color.Cyan))
                    }
                }
            }

            onUpdate {
                if (input.isKeyJustPressed(Key.Escape)) {
                    sceneStack.pop()
                }
            }

            onBackgroundUI {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }

            onForegroundUI {
                Box(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("SCORE: ${survivorData.score}", color = Color.Yellow, fontSize = 32.sp)
                }
            }
        }
    }
}
