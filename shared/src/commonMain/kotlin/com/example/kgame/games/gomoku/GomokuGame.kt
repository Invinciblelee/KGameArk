@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package com.example.kgame.games.gomoku

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
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
import com.kgame.plugins.services.particles.ParticleNodeMath
import org.intellij.lang.annotations.Language
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

// --- 1. Configuration ---
private object Config {
    const val GRID_COUNT = 15
    const val CELL_SIZE = 36f 
    const val BOARD_SIZE = (GRID_COUNT - 1) * CELL_SIZE 
    const val VIRTUAL_W = 800f
    const val VIRTUAL_H = 600f
    const val BOARD_X = VIRTUAL_W / 2f
    const val BOARD_Y = VIRTUAL_H / 2f + 10f
    const val STONE_SCALE = 0.8f 
}

private data object StoneTag : EntityTag()

// --- 2. Shaders ---

class BoardMaterial : Material {
    @Language("AGSL")
    override val sksl: String = """
        vec4 main(vec2 uv) {
            float grain = sin(uv.y * 20.0 + sin(uv.x * 15.0)) * 0.03 + 0.97;
            return vec4(vec3(0.88, 0.72, 0.52) * grain, 1.0);
        }
    """.trimIndent()
}

class StoneMaterial(val isBlack: Boolean) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uTime;
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);
            if (d > 1.0) return vec4(0.0);
            float h = pow(max(0.0, 1.0 - length(st - vec2(-0.3, -0.3))), 5.0);
            vec3 col = ${if (isBlack) "vec3(0.12)" else "vec3(0.96)"};
            return vec4((col + h * 0.3) * smoothstep(1.0, 0.97, d), 1.0);
        }
    """.trimIndent()
}

// --- 3. Logic & AI Engine ---

enum class StoneType { NONE, BLACK, WHITE }

private class GomokuEngine {
    fun checkWin(board: Array<Array<StoneType?>>, x: Int, y: Int): Boolean {
        val type = board[x][y] ?: return false
        val dirs = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)
        for ((dx, dy) in dirs) {
            var count = 1
            for (dir in listOf(1, -1)) {
                var nx = x + dx * dir; var ny = y + dy * dir
                while (nx in 0 until Config.GRID_COUNT && ny in 0 until Config.GRID_COUNT && board[nx][ny] == type) {
                    count++; nx += dx * dir; ny += dy * dir
                }
            }
            if (count >= 5) return true
        }
        return false
    }

    fun findBestMove(board: Array<Array<StoneType?>>): Pair<Int, Int>? {
        var bestScore = -1
        val candidates = mutableListOf<Pair<Int, Int>>()
        for (x in 0 until Config.GRID_COUNT) for (y in 0 until Config.GRID_COUNT) {
            if (board[x][y] == null) {
                val score = evaluate(board, x, y)
                if (score > bestScore) {
                    bestScore = score
                    candidates.clear()
                    candidates.add(x to y)
                } else if (score == bestScore) {
                    candidates.add(x to y)
                }
            }
        }
        return candidates.randomOrNull()
    }

    private fun evaluate(board: Array<Array<StoneType?>>, x: Int, y: Int): Int {
        var score = 0
        val dirs = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)
        for ((dx, dy) in dirs) {
            score += getLineScore(board, x, y, dx, dy, StoneType.WHITE)
            score += (getLineScore(board, x, y, dx, dy, StoneType.BLACK) * 0.95f).toInt()
        }
        return score + (14 - (abs(x - 7) + abs(y - 7)))
    }

    private fun getLineScore(board: Array<Array<StoneType?>>, x: Int, y: Int, dx: Int, dy: Int, type: StoneType): Int {
        var count = 1; var blocked = 0
        for (dir in listOf(1, -1)) {
            var nx = x + dx * dir; var ny = y + dy * dir
            while (nx in 0 until Config.GRID_COUNT && ny in 0 until Config.GRID_COUNT && board[nx][ny] == type) {
                count++; nx += dx * dir; ny += dy * dir
            }
            if (nx !in 0 until Config.GRID_COUNT || ny !in 0 until Config.GRID_COUNT || (board[nx][ny] != null && board[nx][ny] != type)) blocked++
        }
        if (count >= 5) return 100000
        return when (count) {
            4 -> if (blocked == 0) 10000 else 1000
            3 -> if (blocked == 0) 1000 else 100
            2 -> if (blocked == 0) 100 else 10
            else -> count
        }
    }
}

// --- 4. State (Pure Data) ---

private class GomokuState {
    var board by mutableStateOf(Array(Config.GRID_COUNT) { arrayOfNulls<StoneType>(Config.GRID_COUNT) })
    var currentPlayer by mutableStateOf(StoneType.BLACK)
    var winner by mutableStateOf(StoneType.NONE)
    var isGameOver by mutableStateOf(false)
    var vsAi by mutableStateOf(true)
    var lastMove by mutableStateOf<Pair<Int, Int>?>(null)
    var pendingReset by mutableStateOf(false)
}

// --- 5. Visuals ---

private class BoardVisual : Visual(Size(Config.BOARD_SIZE + 40f, Config.BOARD_SIZE + 40f)) {
    private val effect = MaterialEffect(BoardMaterial())
    override fun DrawScope.draw() {
        drawRect(brush = effect.obtainBrush(size))
        
        val lineCol = Color.Black.copy(0.35f)
        val offset = 20f
        for (i in 0 until Config.GRID_COUNT) {
            val s = i * Config.CELL_SIZE + offset
            drawLine(lineCol, Offset(s, offset), Offset(s, Config.BOARD_SIZE + offset), 1.2f)
            drawLine(lineCol, Offset(offset, s), Offset(Config.BOARD_SIZE + offset, s), 1.2f)
        }
        
        val stars = listOf(3, 7, 11)
        stars.forEach { x -> stars.forEach { y -> 
            drawCircle(Color.Black, 4f, Offset(x * Config.CELL_SIZE + offset, y * Config.CELL_SIZE + offset))
        } }
    }
}

private class StoneVisual(val type: StoneType) : Visual(Size(Config.CELL_SIZE * Config.STONE_SCALE, Config.CELL_SIZE * Config.STONE_SCALE)) {
    private val effect = MaterialEffect(StoneMaterial(type == StoneType.BLACK))
    override fun DrawScope.draw() {
        drawCircle(brush = effect.obtainBrush(size))
    }
}

private class PreviewVisual : Visual(Size(Config.CELL_SIZE * Config.STONE_SCALE, Config.CELL_SIZE * Config.STONE_SCALE)) {
    override fun DrawScope.draw() { drawCircle(Color.White.copy(0.25f), style = Stroke(2f)) }
}

// --- 6. System ---

private class GomokuSystem(
    private val state: GomokuState = inject(),
    private val input: InputManager = inject(),
    private val particle: ParticleService = inject(),
    private val camera: CameraService = inject()
) : IntervalSystem() {
    private val engine = GomokuEngine()
    private val stoneFamily = world.family { all(StoneTag) }
    private var preview: Entity? = null
    private var aiTimer = 0f

    override fun onAwake() {
        preview = world.entity { +Transform(); +Renderable(PreviewVisual(), zIndex = 5) }
        performReset()
    }

    override fun onTick(deltaTime: Float) {
        if (state.pendingReset) { 
            performReset()
            state.pendingReset = false
            return 
        }
        
        val p = preview ?: return
        
        if (state.isGameOver) { 
            p[Renderable].isVisible = false
            return 
        }

        // AI Turn Logic
        if (state.vsAi && state.currentPlayer == StoneType.WHITE) {
            p[Renderable].isVisible = false
            aiTimer += deltaTime
            if (aiTimer >= 0.7f) {
                engine.findBestMove(state.board)?.let { placeStone(it.first, it.second) }
                aiTimer = 0f
            }
            return
        }

        // Interaction Logic
        val worldPos = camera.transformer.virtualToWorld(input.getPointerPosition())
        val relX = worldPos.x - Config.BOARD_X
        val relY = worldPos.y - Config.BOARD_Y
        
        // Accurately calculate row and column indices (0-14)
        val gx = (relX / Config.CELL_SIZE).roundToInt() + 7
        val gy = (relY / Config.CELL_SIZE).roundToInt() + 7

        if (gx in 0 until Config.GRID_COUNT && gy in 0 until Config.GRID_COUNT) {
            val snappedPos = gridToWorld(gx, gy)
            p[Transform].position = snappedPos
            p[Renderable].isVisible = true
            if (input.isMouseJustPressed(0) && state.board[gx][gy] == null) {
                placeStone(gx, gy)
            }
        } else {
            p[Renderable].isVisible = false
        }
    }

    private fun performReset() {
        state.board = Array(Config.GRID_COUNT) { arrayOfNulls<StoneType>(Config.GRID_COUNT) }
        state.currentPlayer = StoneType.BLACK
        state.winner = StoneType.NONE
        state.isGameOver = false
        state.lastMove = null
        stoneFamily.forEach { it.remove() }
        aiTimer = 0f
    }

    private fun gridToWorld(gx: Int, gy: Int) = Offset(Config.BOARD_X + (gx - 7) * Config.CELL_SIZE, Config.BOARD_Y + (gy - 7) * Config.CELL_SIZE)

    private fun placeStone(x: Int, y: Int) {
        val type = state.currentPlayer
        state.board[x][y] = type
        state.lastMove = x to y
        val worldPos = gridToWorld(x, y)
        
        world.entity {
            +StoneTag
            +Transform(worldPos)
            +Renderable(StoneVisual(type), zIndex = 10)
            +ScaleAnimation(name = "drop", from = 0f, to = 1f,
                spec = Spring(stiffness = 500f, damping = 25f, initialVelocity = 3.5f),
                autoPlay = true)
        }
        
        camera.director.shake(0.06f)
        particle.emit { ripple(worldPos, if (type == StoneType.BLACK) Color.DarkGray else Color.White) }

        if (engine.checkWin(state.board, x, y)) {
            state.winner = type; state.isGameOver = true
            particle.emit { victoryRain() }
        } else {
            state.currentPlayer = if (type == StoneType.BLACK) StoneType.WHITE else StoneType.BLACK
        }
    }
}

private fun ParticleNodeScope.ripple(c: Offset, col: Color) {
    layer("zen_ripple", c) {
        config {
            count = 24 
            duration = 0.4f
        }

        // --- Polar Coordinates Logic ---
        // Use math.TAU (constant node), operator overloading handles the rest
        val angle = (env.index / env.count) * ParticleNodeMath.TAU

        // Diffusion radius
        val radius = env.progress * 80f

        // Position mapping: vec2 constructor receives ParticleNode
        position = vec2(math.cos(angle) * radius, math.sin(angle) * radius)

        // --- Visual Dynamics (utilizing ops) ---
        val invProgress = 1.0f - env.progress

        // Use smoothstep for graceful scaling and fading
        size = ops.smoothstep(1.0f, 0.0f, env.progress) * 5f

        // Control transparency using mix or multiplication
        val alpha = invProgress * 0.5f
        color = color(col.red, col.green, col.blue, alpha)
    }
}

private fun ParticleNodeScope.victoryRain() {
    layer("v", Offset(Config.BOARD_X, Config.BOARD_Y)) {
        config { count = 120; duration = 2.5f }
        val a = math.random(0f, 360f)
        val r = math.toRadians(a)
        position = vec2(math.cos(r) * 500f * env.progress, math.sin(r) * 400f * env.progress)
        size = math.random(5f, 12f)
        color = color(1f, 0.85f, 0.2f, 1f - env.progress)
    }
}

// --- 7. Entry ---

@Composable
fun GomokuGame() {
    val sceneStack = rememberGameSceneStack("menu")
    val state = remember { GomokuState() }

    KGame(sceneStack = sceneStack, virtualSize = Size(Config.VIRTUAL_W, Config.VIRTUAL_H)) {
        scene("menu") {
            onBackgroundUI { Rectangle(Color(0xFF121212)) }
            onForegroundUI {
                Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("GOMOKU", fontSize = 72.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text("ZEN EDITION", fontSize = 18.sp, color = Color.Gray)
                    Spacer(Modifier.height(80.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI COMPETITOR", color = Color.White); Spacer(Modifier.width(16.dp))
                        Switch(state.vsAi, { state.vsAi = it })
                    }
                    Spacer(Modifier.height(40.dp))
                    Button(onClick = { sceneStack.push("play") }, modifier = Modifier.width(220.dp).height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                        Text("START GAME", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        scene("play") {
            onWorld {
                configure { 
                    injectables { +state }
                    systems { 
                        +GomokuSystem()
                        +AnimationTickSystem()
                        +AnimationSystem()
                        +CameraSystem()
                        +RenderSystem()
                        +ParticleRenderSystem()
                    } 
                }
                spawn {
                    entity { +Transform(Offset(Config.BOARD_X, Config.BOARD_Y)); +Renderable(BoardVisual()) }
                    entity { +Transform(Offset(Config.BOARD_X, Config.BOARD_Y)); +Camera("main", isMain = true); +CameraShake() }
                }
            }

            onUpdate {
                if (input.isKeyJustPressed(Key.Escape)) {
                    sceneStack.pop()
                }
            }
        }
    }
}
