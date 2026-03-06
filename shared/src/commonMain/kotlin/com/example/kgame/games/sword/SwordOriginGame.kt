package com.example.kgame.games.sword

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.Rectangle
import com.kgame.plugins.components.ArriveTarget
import com.kgame.plugins.components.Arriver
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.distanceTo
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.visuals.Visual
import com.kgame.plugins.visuals.shapes.CircleVisual

// --- 1. Domain --- //

private data object PlayerTag : EntityTag()
private data object SwordSilkTag : EntityTag()
private data object EnemyTag : EntityTag()

enum class Element { METAL, WOOD, WATER, FIRE, EARTH }

/** Player Core: The Sword Pill */
private class SwordPill(
    var level: Int = 1,
    var currentElement: Element = Element.METAL
) : Component<SwordPill> {
    override fun type() = SwordPill
    companion object : ComponentType<SwordPill>()
}

/** A segment of the physics-based sword silk */
private class SilkNode(val from: Entity, val to: Entity, val element: Element) : Component<SilkNode> {
    override fun type() = SilkNode
    companion object : ComponentType<SilkNode>()
}

/** Enemy state */
private class EnemyState(val element: Element, var health: Int = 100) : Component<EnemyState> {
    override fun type() = EnemyState
    companion object : ComponentType<EnemyState>()
}

class GameState {
    var level by mutableIntStateOf(1)
    var score by mutableIntStateOf(0)
    var isGameOver by mutableStateOf(false)
}

// --- 2. Visuals ---

private class SwordPillVisual(val pill: SwordPill) : Visual() {
    override fun DrawScope.draw() {
        drawCircle(Color.White, radius = 20f + pill.level)
        drawCircle(elementToColor(pill.currentElement), radius = 10f)
    }
}

private class EnemyVisual(val state: EnemyState) : Visual() {
    override fun DrawScope.draw() {
        val color = elementToColor(state.element)
        drawRect(color.copy(alpha = 0.5f), size = Size(30f, 30f))
        drawRect(color, size = Size(30f, 30f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
    }
}

// --- 3. Systems ---

private class PlayerControlSystem(
    private val state: GameState = inject(),
    private val input: InputManager = inject(),
    private val camera: CameraService = inject()
) : IntervalSystem() {

    private val playerFamily = world.family { all(PlayerTag, SwordPill, Transform) }

    override fun onTick(deltaTime: Float) {
        if (state.isGameOver) return

        val player = playerFamily.firstOrNull() ?: return
        val targetPos = camera.transformer.virtualToWorld(input.getPointerPosition())
        
        // The Arriver component will handle the movement smoothly
        player.configure { addIfAbsent(ArriveTarget) { ArriveTarget(targetPos) } }
    }
}

private class SwordSilkSystem(
) : IntervalSystem(interval = Fixed(0.1f)) {

    private var lastNode: Entity? = null
    private val player = world.family { all(PlayerTag) }.first()
    private val pill = player[SwordPill]

    private val silkNode = inject<SilkNode>()

    override fun onTick(deltaTime: Float) {
        val currentNode = world.entity {
            +Transform(player[Transform].position)
        }

        if (lastNode != null && lastNode!!.wasRemoved().not()) {
            world.entity {
                +SwordSilkTag
                +SilkNode(lastNode!!, currentNode, pill.currentElement)
                +Renderable(object: Visual() {
                    override fun DrawScope.draw() {
                        val p1 = silkNode.from[Transform].position
                        val p2 = silkNode.to[Transform].position
                        drawLine(elementToColor(pill.currentElement), p1, p2, 2f)
                    }
                })
            }
        }
        lastNode = currentNode
    }
}

private class EnemySpawnSystem : IntervalSystem(interval = Fixed(1.5f)) {
    override fun onTick(deltaTime: Float) {
        if (inject<GameState>().isGameOver) return

        val spawnEdge = (0..3).random()
        val spawnPos = when (spawnEdge) {
            0 -> Offset((-500..500).random().toFloat(), -400f)
            1 -> Offset((-500..500).random().toFloat(), 400f)
            2 -> Offset(-500f, (-400..400).random().toFloat())
            else -> Offset(500f, (-400..400).random().toFloat())
        }
        
        world.entity {
            val state = EnemyState(Element.entries.random())
            +EnemyTag; +state
            +Transform(spawnPos)
            +Arriver(speed = 100f)
            +ArriveTarget(Offset.Zero) // Head towards center
            +Renderable(EnemyVisual(state))
        }
    }
}

// --- 4. Game Entry ---

@Composable
fun SwordOriginGame() {
    val state = remember { GameState() }

    KSimpleGame(virtualSize = Size(1000f, 800f)) {
        onWorld {
            useDefaultSystems()
            configure {
                injectables { +state }
                systems { +PlayerControlSystem(); +SwordSilkSystem(); +EnemySpawnSystem() }
            }
            spawn {
                entity {
                    +Transform()
                    +Camera(isMain = true)
                    +CameraShake()
                }
                val pill = SwordPill()
                entity {
                    +PlayerTag; +pill; +Transform(); +Arriver(speed = 500f)
                    +Renderable(SwordPillVisual(pill))
                }
            }
        }

        onBackgroundUI {
            Rectangle(color = Color(0xFF0A080C), modifier = Modifier.fillMaxSize())
        }

        onForegroundUI {
            Box(Modifier.fillMaxSize().padding(32.dp)) {
                Column(Modifier.align(Alignment.TopStart)) {
                    Text("SWORD ORIGIN", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
                    Text("SCORE: ${state.score}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- Utility --- //

private fun elementToColor(element: Element): Color = when (element) {
    Element.METAL -> Color.Gray
    Element.WOOD -> Color(0xFF228B22)
    Element.WATER -> Color.Cyan
    Element.FIRE -> Color.Red
    Element.EARTH -> Color(0xFFD2691E)
}
