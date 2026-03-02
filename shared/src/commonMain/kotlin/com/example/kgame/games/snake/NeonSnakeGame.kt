@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package com.example.kgame.games.snake

import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgame.ecs.*
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.KSimpleGame
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.Rectangle
import com.kgame.plugins.components.*
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.systems.*
import com.kgame.plugins.visuals.Visual
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

// --- 1. Constants & Tags ---

private object Config {
    const val VIRTUAL_W = 800f
    const val VIRTUAL_H = 600f
    const val CELL_SIZE = 30f
    const val GRID_W = 20
    const val GRID_H = 15
}

private data object SnakeHeadTag : EntityTag()
private data object SnakeBodyTag : EntityTag()
private data object FoodTag : EntityTag()

/**
 * Component to create a linked list of entities for the snake body.
 */
private class SnakeNode(var next: Entity? = null) : Component<SnakeNode> {
    override fun type() = SnakeNode
    companion object : ComponentType<SnakeNode>()
}

class SnakeState {
    var score by mutableIntStateOf(0)
    var isGameOver by mutableStateOf(false)
    var pendingReset by mutableStateOf(false)
    var direction by mutableStateOf(Offset(1f, 0f))
}

// --- 2. Systems ---

private class SnakeSystem(
    private val state: SnakeState = inject(),
    private val input: InputManager = inject(),
    private val camera: CameraService = inject(),
    private val particles: ParticleService = inject()
) : IntervalSystem(interval = Fixed(0.15f)) {

    private val headFamily = world.family { all(SnakeHeadTag, Transform, SnakeNode) }
    private val bodyFamily = world.family { all(SnakeBodyTag, Transform) }
    private val foodFamily = world.family { all(FoodTag, Transform) }

    private var nextDirection = Offset(1f, 0f)
    private var tailEntity: Entity? = null
    private var toGrow = 0

    override fun onAwake() {
        performReset()
    }

    override fun onTick(deltaTime: Float) {
        // Polling input
        if (input.isKeyDown(Key.W) || input.isKeyDown(Key.DirectionUp)) if (state.direction.y == 0f) nextDirection = Offset(0f, -1f)
        if (input.isKeyDown(Key.S) || input.isKeyDown(Key.DirectionDown)) if (state.direction.y == 0f) nextDirection = Offset(0f, 1f)
        if (input.isKeyDown(Key.A) || input.isKeyDown(Key.DirectionLeft)) if (state.direction.x == 0f) nextDirection = Offset(-1f, 0f)
        if (input.isKeyDown(Key.D) || input.isKeyDown(Key.DirectionRight)) if (state.direction.x == 0f) nextDirection = Offset(1f, 0f)

        if (state.pendingReset) {
            performReset()
            state.pendingReset = false
            return
        }

        if (state.isGameOver) return

        state.direction = nextDirection
        val head = headFamily.firstOrNull() ?: return
        val headTrans = head[Transform]
        val oldHeadPos = headTrans.position

        // 1. Move Body using the Linked List (No allocations!)
        var current: Entity = head
        var prevPos = oldHeadPos
        
        while (true) {
            val node = current[SnakeNode]
            val next = node.next ?: break
            val nextTrans = next[Transform]
            val currentPos = nextTrans.position
            nextTrans.position = prevPos
            prevPos = currentPos
            current = next
        }
        
        // Handle growth at the position where the tail was
        if (toGrow > 0) {
            addSegment(prevPos)
            toGrow--
        }

        // 2. Move Head
        headTrans.position += state.direction * Config.CELL_SIZE

        // 3. Wall Collision
        val limitX = (Config.GRID_W * Config.CELL_SIZE) / 2f
        val limitY = (Config.GRID_H * Config.CELL_SIZE) / 2f
        if (headTrans.position.x < -limitX || headTrans.position.x >= limitX ||
            headTrans.position.y < -limitY || headTrans.position.y >= limitY) {
            gameOver()
            return
        }

        // 4. Self Collision
        bodyFamily.forEach { 
            if (it[Transform].position == headTrans.position) {
                gameOver()
                return@forEach
            }
        }

        // 5. Food Interaction
        foodFamily.forEach { food ->
            if (food[Transform].position == headTrans.position) {
                eatFood(food)
            }
        }
    }

    private fun eatFood(food: Entity) {
        food.remove()
        state.score += 10
        camera.director.shake(0.2f)
        particles.emit {
            layer("pop", headFamily.first()[Transform].position) {
                config { count = 15; duration = 0.5f }
                val r = (0f..360f).random() * 0.017453292f
                position = vec2(math.cos(scalar(r)) * 150f * env.progress, math.sin(scalar(r)) * 150f * env.progress)
                size = scalar(10f) * (1f - env.progress)
                color = color(0f, 1f, 0.5f, 1f - env.progress)
            }
        }
        toGrow++
        spawnFood()
    }

    private fun addSegment(pos: Offset) {
        val newSegment = world.entity {
            +SnakeBodyTag
            +SnakeNode() 
            +Transform(pos)
            +Renderable(Visual(Config.CELL_SIZE - 4f) {
                drawRect(Color.Cyan.copy(alpha = 0.8f))
                drawRect(Color.White, style = Stroke(2f))
            }, zIndex = 5)
        }
        // Link to the current tail
        tailEntity?.get(SnakeNode)?.next = newSegment
        tailEntity = newSegment
    }

    private fun spawnFood() {
        var pos: Offset
        var attempts = 0
        do {
            val gx = (-Config.GRID_W / 2 until Config.GRID_W / 2).random()
            val gy = (-Config.GRID_H / 2 until Config.GRID_H / 2).random()
            pos = Offset(gx * Config.CELL_SIZE + Config.CELL_SIZE/2, gy * Config.CELL_SIZE + Config.CELL_SIZE/2)
            attempts++
            val headPos = headFamily.firstOrNull()?.get(Transform)?.position
            val occupied = headPos == pos || bodyFamily.any { it[Transform].position == pos } || foodFamily.any { it[Transform].position == pos }
        } while (occupied && attempts < 100)
        
        world.entity {
            +FoodTag
            +Transform(pos)
            +Renderable(Visual(Config.CELL_SIZE - 6f) {
                val c = Color(1f, (0.2f..0.8f).random(), 0f)
                drawCircle(c)
                drawCircle(Color.White, radius = 4f)
            }, zIndex = 4)
            +ScaleAnimation("pulse", 0.8f, 1.2f, TransformOrigin.Center, spec = InfiniteRepeatable(Tween(0.3f), RepeatMode.Reverse), autoPlay = true)
        }
    }

    private fun gameOver() {
        state.isGameOver = true
        camera.director.shake(0.5f)
    }

    private fun performReset() {
        // Cleanup snake and food
        world.family { any(SnakeHeadTag, SnakeBodyTag, FoodTag) }.forEach { it.remove() }
        
        tailEntity = null
        toGrow = 0
        state.score = 0
        state.isGameOver = false
        state.direction = Offset(1f, 0f)
        nextDirection = Offset(1f, 0f)

        val head = world.entity {
            +SnakeHeadTag
            +SnakeNode()
            +Transform(Offset(Config.CELL_SIZE / 2, Config.CELL_SIZE / 2))
            +Renderable(Visual(Config.CELL_SIZE - 2f) {
                drawRect(Color.White)
                drawRect(Color.Cyan, style = Stroke(4f))
            }, zIndex = 10)
        }
        tailEntity = head

        repeat(3) { toGrow++ }
        repeat(5) { spawnFood() }
    }
}

// --- 3. Entry ---

@Composable
fun NeonSnakeGame() {
    val state = remember { SnakeState() }

    KSimpleGame(virtualSize = Size(Config.VIRTUAL_W, Config.VIRTUAL_H)) {
        onWorld {
            useDefaultSystems()
            configure {
                injectables { add(state) }
                systems {
                    +SnakeSystem()
                }
            }
            spawn {
                entity {
                    +Transform()
                    +Camera("main", isMain = true)
                    +CameraShake()
                }

                // Grid Background
                entity {
                    +Transform()
                    +Renderable(Visual(Size(Config.GRID_W * Config.CELL_SIZE, Config.GRID_H * Config.CELL_SIZE)) {
                        val col = Color.White.copy(alpha = 0.05f)
                        val w = Config.GRID_W * Config.CELL_SIZE
                        val h = Config.GRID_H * Config.CELL_SIZE
                        for (i in 0..Config.GRID_W) {
                            val x = i * Config.CELL_SIZE - w / 2
                            drawLine(col, Offset(x, -h / 2), Offset(x, h / 2))
                        }
                        for (i in 0..Config.GRID_H) {
                            val y = i * Config.CELL_SIZE - h / 2
                            drawLine(col, Offset(-w / 2, y), Offset(w / 2, y))
                        }
                    }, zIndex = -1)
                }
            }
        }

        onBackgroundUI { Rectangle(Color(0xFF050505)) }

        onForegroundUI {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                Column(Modifier.align(Alignment.TopStart)) {
                    Text("NEON SNAKE", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("SCORE: ${state.score}", color = Color.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                if (state.isGameOver) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Black.copy(0.8f),
                        shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("GAME OVER", color = Color.Red, fontSize = 32.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { state.pendingReset = true }) {
                                Text("REBOOT")
                            }
                        }
                    }
                }
            }
        }
    }
}
