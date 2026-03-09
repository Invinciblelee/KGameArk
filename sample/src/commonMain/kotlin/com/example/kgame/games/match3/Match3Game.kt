@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package com.example.kgame.games.match3

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgame.ecs.*
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.KGame
import com.kgame.engine.core.rememberGameSceneStack
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.MaterialEffect
import com.kgame.engine.input.InputManager
import com.kgame.engine.ui.Rectangle
import com.kgame.plugins.components.*
import com.kgame.plugins.systems.*
import com.kgame.plugins.visuals.Visual
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.CameraService
import org.intellij.lang.annotations.Language
import kotlin.math.abs
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

/**
 * Game constants following Google Android SDK coding style conventions.
 */
private object GameConfig {
    const val GRID_COLUMNS = 8
    const val GRID_ROWS = 8
    const val CELL_SIZE_PX = 70f
    
    const val BOARD_WIDTH = GRID_COLUMNS * CELL_SIZE_PX
    const val BOARD_HEIGHT = GRID_ROWS * CELL_SIZE_PX
    
    const val VIRTUAL_WIDTH = 800f
    const val VIRTUAL_HEIGHT = 1200f
    
    const val BOARD_CENTER_X = VIRTUAL_WIDTH / 2f
    const val BOARD_CENTER_Y = VIRTUAL_HEIGHT / 2f + 80f
}

enum class GemType(val color: Color) {
    RED(Color(0xFFFF3D00)),
    BLUE(Color(0xFF2979FF)),
    GREEN(Color(0xFF00E676)),
    YELLOW(Color(0xFFFFEA00)),
    PURPLE(Color(0xFFD500F9)),
    CYAN(Color(0xFF00E5FF))
}

/**
 * Logical state component for a Gem. 
 * This is the SINGLE source of truth for its grid coordinates.
 */
private data class GemComponent(
    var gridX: Int, 
    var gridY: Int, 
    val type: GemType
) : Component<GemComponent> {
    override fun type() = GemComponent
    companion object : ComponentType<GemComponent>()
}

private data object GemTag : EntityTag()

// --- 1. Shaders ---

class GemMaterial(val color: Color) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uTime;
        uniform vec4 uColor;

        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);
            float alpha = smoothstep(1.0, 0.95, d);
            float t = uTime * 0.5;
            float angle = atan(st.y, st.x);
            float facets = sin(angle * 5.0 + d * 3.0 + t) * 0.12;
            float h = pow(max(0.0, 1.0 - length(st - vec2(-0.35, -0.35))), 5.0) * 0.8;
            vec3 col = uColor.rgb * (0.9 + facets) + h;
            col += smoothstep(0.02, 0.0, abs(d - 0.82)) * 0.4;
            float finalAlpha = alpha * uColor.a;
            return vec4(col * finalAlpha, finalAlpha);
        }
    """.trimIndent()

    override fun MaterialEffect.onSetup() {
        uniform("uColor", color)
    }

    override fun MaterialEffect.onUpdate() {
        uniform("uTime", elapsedTime)
    }
}

// --- 2. Shared State ---

private class Match3GameState {
    var score by mutableIntStateOf(0)
    var isUserLocked by mutableStateOf(false)
    var selectedGridPos by mutableStateOf<Pair<Int, Int>?>(null)
    var comboMultiplier by mutableIntStateOf(0)
}

// --- 3. Visuals ---

private class GemVisual(val type: GemType) : Visual(Size(GameConfig.CELL_SIZE_PX - 4f, GameConfig.CELL_SIZE_PX - 4f)) {
    private val effect = MaterialEffect(GemMaterial(type.color))
    override fun update(deltaTime: Float) = effect.update(deltaTime)
    override fun DrawScope.draw() {
        drawRect(brush = effect.obtainBrush(size))
    }
}

private class SelectionVisual : Visual(Size(GameConfig.CELL_SIZE_PX, GameConfig.CELL_SIZE_PX)) {
    override fun DrawScope.draw() {
        drawRect(Color.White.copy(0.5f), style = Stroke(4f))
    }
}

// --- 4. Main System (Purely Component Driven) ---

private class Match3System(
    private val state: Match3GameState = inject(),
    private val input: InputManager = inject(),
    private val particle: ParticleService = inject(),
    private val camera: CameraService = inject()
) : IntervalSystem() {

    private val gemFamily = world.family { all(GemTag, GemComponent) }
    private var selectionEntity: Entity? = null
    
    private enum class GamePhase { IDLE, WAITING, CHECKING, FALLING, REFILLING }
    private var currentPhase = GamePhase.IDLE
    private var stateTimer = 0f

    override fun onAwake() {
        selectionEntity = world.entity { 
            +Transform()
            +Renderable(SelectionVisual(), zIndex = 20, isVisible = false) 
        }
        setupBoard()
    }

    private fun setupBoard() {
        for (x in 0 until GameConfig.GRID_COLUMNS) {
            for (y in 0 until GameConfig.GRID_ROWS) {
                spawnGem(x, y, isImmediate = true)
            }
        }
        // Silence initial board scan to ensure it starts clear
        while (checkMatchesAndRemove(isSilent = true)) {
            processInternalGravity()
            processInternalRefill()
        }
    }

    override fun onTick(deltaTime: Float) {
        when (currentPhase) {
            GamePhase.IDLE -> handleUserInput()
            GamePhase.WAITING -> {
                stateTimer += deltaTime
                if (stateTimer >= 0.25f) transitionTo(GamePhase.CHECKING)
            }
            GamePhase.CHECKING -> {
                if (checkMatchesAndRemove()) {
                    state.comboMultiplier++
                    camera.director.shake(0.15f)
                    transitionTo(GamePhase.FALLING)
                } else {
                    state.comboMultiplier = 0
                    state.isUserLocked = false
                    transitionTo(GamePhase.IDLE)
                }
            }
            GamePhase.FALLING -> {
                stateTimer += deltaTime
                if (stateTimer >= 0.15f) {
                    processInternalGravity()
                    transitionTo(GamePhase.REFILLING)
                }
            }
            GamePhase.REFILLING -> {
                processInternalRefill()
                transitionTo(GamePhase.CHECKING)
            }
        }
        syncSelectionOverlay()
    }

    private fun transitionTo(phase: GamePhase) {
        currentPhase = phase
        stateTimer = 0f
    }

    private fun getEntityAt(x: Int, y: Int): Entity? {
        return gemFamily.firstOrNull { 
            val comp = it[GemComponent]
            comp.gridX == x && comp.gridY == y 
        }
    }

    private fun spawnGem(x: Int, y: Int, type: GemType = GemType.entries.random(), isImmediate: Boolean = false) {
        val targetPos = gridToWorld(x, y)
        val startPos = if (isImmediate) targetPos else targetPos - Offset(0f, 600f)
        
        world.entity {
            +GemTag
            +GemComponent(gridX = x, gridY = y, type = type)
            +Transform(position = startPos)
            +Renderable(visual = GemVisual(type), zIndex = 10)
            if (!isImmediate) {
                +TranslationAnimation(
                    name = "fall", 
                    from = startPos, 
                    to = targetPos, 
                    spec = Spring(stiffness = 250f), 
                    autoPlay = true
                )
            }
            +ScaleAnimation(
                name = "pop", 
                from = 0f, 
                to = 1f, 
                pivot = TransformOrigin.Center,
                spec = Spring(stiffness = 400f), 
                autoPlay = true
            )
        }
    }

    private fun gridToWorld(x: Int, y: Int): Offset {
        val originX = GameConfig.BOARD_CENTER_X - GameConfig.BOARD_WIDTH / 2f + GameConfig.CELL_SIZE_PX / 2f
        val originY = GameConfig.BOARD_CENTER_Y - GameConfig.BOARD_HEIGHT / 2f + GameConfig.CELL_SIZE_PX / 2f
        return Offset(originX + x * GameConfig.CELL_SIZE_PX, originY + y * GameConfig.CELL_SIZE_PX)
    }

    private fun handleUserInput() {
        if (state.isUserLocked) return
        
        val pointerWorld = camera.transformer.virtualToWorld(input.getPointerPosition())
        val localX = pointerWorld.x - (GameConfig.BOARD_CENTER_X - GameConfig.BOARD_WIDTH / 2f)
        val localY = pointerWorld.y - (GameConfig.BOARD_CENTER_Y - GameConfig.BOARD_HEIGHT / 2f)
        
        val gridX = (localX / GameConfig.CELL_SIZE_PX).toInt()
        val gridY = (localY / GameConfig.CELL_SIZE_PX).toInt()

        if (gridX in 0 until GameConfig.GRID_COLUMNS && gridY in 0 until GameConfig.GRID_ROWS && input.isMouseJustPressed(0)) {
            val lastPos = state.selectedGridPos
            if (lastPos == null) {
                state.selectedGridPos = gridX to gridY
            } else {
                if (abs(lastPos.first - gridX) + abs(lastPos.second - gridY) == 1) {
                    performGemSwap(lastPos.first, lastPos.second, gridX, gridY)
                    state.selectedGridPos = null
                    state.isUserLocked = true
                    transitionTo(GamePhase.WAITING)
                } else {
                    state.selectedGridPos = gridX to gridY
                }
            }
        }
    }

    private fun performGemSwap(x1: Int, y1: Int, x2: Int, y2: Int) {
        val e1 = getEntityAt(x1, y1)
        val e2 = getEntityAt(x2, y2)
        if (e1 != null && e2 != null) {
            e1[GemComponent].apply { gridX = x2; gridY = y2 }
            e2[GemComponent].apply { gridX = x1; gridY = y1 }
            e1.configure { 
                +TranslationAnimation(
                    name = "swap", 
                    from = e1[Transform].position, 
                    to = gridToWorld(x2, y2), 
                    spec = Tween(duration = 0.15f), 
                    autoPlay = true
                ) 
            }
            e2.configure { 
                +TranslationAnimation(
                    name = "swap", 
                    from = e2[Transform].position, 
                    to = gridToWorld(x1, y1), 
                    spec = Tween(duration = 0.15f), 
                    autoPlay = true
                ) 
            }
        }
    }

    private fun checkMatchesAndRemove(isSilent: Boolean = false): Boolean {
        val toRemove = mutableSetOf<Entity>()
        
        // Horizontal Scan
        for (y in 0 until GameConfig.GRID_ROWS) {
            var matchCount = 1
            for (x in 1 until GameConfig.GRID_COLUMNS) {
                val currentType = getEntityAt(x, y)?.get(GemComponent)?.type
                val prevType = getEntityAt(x - 1, y)?.get(GemComponent)?.type
                if (currentType != null && currentType == prevType) {
                    matchCount++
                } else {
                    if (matchCount >= 3) {
                        for (i in 1..matchCount) getEntityAt(x - i, y)?.let { toRemove.add(it) }
                    }
                    matchCount = 1
                }
            }
            if (matchCount >= 3) {
                for (i in 1..matchCount) getEntityAt(GameConfig.GRID_COLUMNS - i, y)?.let { toRemove.add(it) }
            }
        }
        
        // Vertical Scan
        for (x in 0 until GameConfig.GRID_COLUMNS) {
            var matchCount = 1
            for (y in 1 until GameConfig.GRID_ROWS) {
                val currentType = getEntityAt(x, y)?.get(GemComponent)?.type
                val prevType = getEntityAt(x, y - 1)?.get(GemComponent)?.type
                if (currentType != null && currentType == prevType) {
                    matchCount++
                } else {
                    if (matchCount >= 3) {
                        for (i in 1..matchCount) getEntityAt(x, y - i)?.let { toRemove.add(it) }
                    }
                    matchCount = 1
                }
            }
            if (matchCount >= 3) {
                for (i in 1..matchCount) getEntityAt(x, GameConfig.GRID_ROWS - i)?.let { toRemove.add(it) }
            }
        }

        if (toRemove.isNotEmpty()) {
            toRemove.forEach { entity ->
                val comp = entity[GemComponent]
                comp.gridX = -1; comp.gridY = -1 // Logic death
                if (!isSilent) {
                    particle.emit { spawnBurstEffect(entity[Transform].position, comp.type.color) }
                    state.score += 10
                }
                entity.remove()
            }
            return true
        }
        return false
    }

    private fun processInternalGravity() {
        for (x in 0 until GameConfig.GRID_COLUMNS) {
            for (y in GameConfig.GRID_ROWS - 1 downTo 1) {
                if (getEntityAt(x, y) == null) {
                    for (yy in y - 1 downTo 0) {
                        val above = getEntityAt(x, yy)
                        if (above != null) {
                            above[GemComponent].gridY = y
                            val target = gridToWorld(x, y)
                            above.configure { 
                                +TranslationAnimation(
                                    name = "gravity", 
                                    from = above[Transform].position, 
                                    to = target, 
                                    spec = Spring(stiffness = 300f), 
                                    autoPlay = true
                                ) 
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    private fun processInternalRefill() {
        for (x in 0 until GameConfig.GRID_COLUMNS) {
            for (y in 0 until GameConfig.GRID_ROWS) {
                if (getEntityAt(x, y) == null) spawnGem(x, y)
            }
        }
    }

    private fun syncSelectionOverlay() {
        val target = state.selectedGridPos
        selectionEntity?.let { entity ->
            if (target != null && !state.isUserLocked) {
                entity[Transform].position = gridToWorld(target.first, target.second)
                entity[Renderable].isVisible = true
            } else entity[Renderable].isVisible = false
        }
    }
}

// --- 5. FX ---

private fun ParticleNodeScope.spawnBurstEffect(pos: Offset, col: Color) {
    layer("burst", pos) {
        config { count = 14; duration = 0.5f }
        val r = math.toRadians(math.random(0f, 360f))
        val v = math.random(150f, 400f)
        position = vec2(math.cos(r) * v * env.progress, math.sin(r) * v * env.progress)
        size = scalar(15f) * (1.0f - env.progress)
        this.color = color(col.red, col.green, col.blue, 1.0f - env.progress)
    }
}

// --- 6. Entry ---

@Composable
fun Match3Game() {
    val sceneStack = rememberGameSceneStack("menu")
    val gameState = remember { Match3GameState() }

    KGame(sceneStack = sceneStack, virtualSize = Size(GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)) {
        scene("menu") {
            onBackgroundUI { Rectangle(Color(0xFF0A0A1C)) }
            onForegroundUI {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("JEWEL DASH", fontSize = 72.sp, color = Color.Cyan, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(100.dp))
                    Button(onClick = { sceneStack.push("play") }, modifier = Modifier.width(260.dp).height(80.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)) {
                        Text("START GAME", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }

        scene("play") {
            onWorld {
                configure {
                    injectables { +gameState }
                    systems {
                        +Match3System(); +AnimationTickSystem(); +AnimationSystem()
                        +CameraSystem(); +RenderSystem(); +ParticleRenderSystem()
                    }
                }
                spawn {
                    entity { +Transform(Offset(GameConfig.BOARD_CENTER_X, GameConfig.BOARD_CENTER_Y)); +Camera("main", isMain = true); +CameraShake() }
                    entity {
                        +Transform(Offset(GameConfig.BOARD_CENTER_X, GameConfig.BOARD_CENTER_Y))
                        +Renderable(object : Visual(Size(GameConfig.BOARD_WIDTH + 20f, GameConfig.BOARD_HEIGHT + 20f)) {
                            override fun DrawScope.draw() {
                                drawRect(Color.White.copy(0.05f))
                                drawRect(Color.Cyan.copy(0.18f), style = Stroke(2.2f))
                            }
                        }, zIndex = 0)
                    }
                }
            }
            onBackgroundUI { Rectangle(Color(0xFF050510)) }
            onForegroundUI {
                Box(Modifier.fillMaxSize().padding(40.dp)) {
                    Text("${gameState.score}", color = Color.White, fontSize = 64.sp, modifier = Modifier.align(Alignment.TopCenter), fontWeight = FontWeight.Black)
                    IconButton(onClick = { sceneStack.pop() }, modifier = Modifier.align(Alignment.TopStart)) {
                        Text("✕", color = Color.White, fontSize = 32.sp)
                    }
                }
            }
        }
    }
}
