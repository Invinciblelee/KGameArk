package com.example.cmp.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.ecs.Component
import com.game.ecs.ComponentType
import com.game.ecs.Entity
import com.game.ecs.IntervalSystem
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.World.Companion.inject
import com.game.ecs.components.Camera
import com.game.ecs.components.CameraTarget
import com.game.ecs.components.Renderable
import com.game.ecs.components.Rigidbody
import com.game.ecs.components.SpringEffect
import com.game.ecs.components.Transform
import com.game.ecs.components.Visual
import com.game.ecs.components.applyImpulseFromSegment
import com.game.ecs.components.applyMovement
import com.game.ecs.injectables.CoordinateTransform
import com.game.ecs.injectables.coerceViewportSafeBounds
import com.game.ecs.systems.CameraSystem
import com.game.ecs.systems.PhysicsSystem
import com.game.ecs.systems.RenderSystem
import com.game.ecs.systems.SteeringSystem
import com.game.ecs.systems.panTo
import com.game.ecs.systems.switchCameraSmoothly
import com.game.engine.asset.AssetsManager
import com.game.engine.asset.ImageKey
import com.game.engine.asset.MusicKey
import com.game.engine.asset.SoundKey
import com.game.engine.audio.AudioManager
import com.game.engine.context.PlatformContext
import com.game.engine.core.KGame
import com.game.engine.core.KSimpleGame
import com.game.engine.core.rememberGameSceneStack
import com.game.engine.input.InputManager
import com.game.engine.math.random
import com.game.engine.math.randomOffset
import com.game.engine.ui.GameJoypad
import com.game.engine.ui.Rectangle
import com.game.engine.ui.applyToInput
import com.game.engine.utils.KeyTrigger
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.random.Random

// ==========================================
// PART 2: 业务逻辑层 (剑气朝元 Demo)
// ==========================================

// --- 组件 ---
enum class WuXing(val color: Color) {
    Metal(Color(0xFFFFD700)),
    Wood(Color(0xFF69F0AE)),
    Water(Color(0xFF40C4FF)),
    Fire(Color(0xFFFF5252)),
    Earth(Color(0xFF8D6E63))
}

class PlayerTag : Component<PlayerTag> {
    override fun type() = PlayerTag

    companion object : ComponentType<PlayerTag>()
}

class EnemyTag(val radius: Float = 15f, var isTrapped: Boolean = false) : Component<EnemyTag> {
    override fun type() = EnemyTag

    companion object : ComponentType<EnemyTag>()
}

data class SilkNode(
    var x: Float,
    var y: Float,
    var oldX: Float = x,
    var oldY: Float = y,
    var pinned: Boolean = false
)

data class SilkComponent(
    var type: WuXing = WuXing.Water,
    val nodes: ArrayList<SilkNode> = ArrayList() // 存储局部坐标
) : Component<SilkComponent> {
    override fun type() = SilkComponent

    companion object : ComponentType<SilkComponent>()

    init {
        repeat(40) { nodes.add(SilkNode(0f, 0f)) }
    }
}

// --- Visual 实现 (新增) ---

class EnemyVisual(private val enemyTag: EnemyTag, val color: Color? = null) : Visual {
    override val size: Size = Size(enemyTag.radius * 2, enemyTag.radius * 2)

    override fun DrawScope.draw() { // 纯粹的局部绘制
        drawCircle(color ?: (if (enemyTag.isTrapped) Color.Red else Color.Gray))
    }
}

class PlayerVisual(assets: AssetsManager) : Visual {

    val player = assets[GameAssets.Texture.Player]

    override val size: Size = Size(50f, 50f)

    // 1. 定义源矩形 (Source Rectangle): 从原图的哪个部分裁剪 (通常是整个图片)
    val sourceRect = Rect(Offset.Zero, Size(player.width.toFloat(), player.height.toFloat()))

    // 2. 定义目标矩形 (Destination Rectangle): 将原图绘制到画布的哪个区域
    //    这里我们将图片的左上角放在 (0,0)，并绘制成 PlayerVisual.size 指定的大小
    val destRect = Rect(Offset.Zero, size) // 使用 PlayerVisual 自己的 size 作为目标尺寸

    override fun DrawScope.draw() { // 纯粹的局部绘制
        // 3. 使用 drawImage 的重载，指定源和目标矩形
//        drawImage(
//            image = player,
//            srcOffset = sourceRect.topLeft.toIntOffset(), // 源矩形的左上角
//            srcSize = sourceRect.size.toIntSize(),         // 源矩形的尺寸
//            dstOffset = destRect.topLeft.toIntOffset(),   // 目标矩形的左上角
//            dstSize = destRect.size.toIntSize()           // 目标矩形的尺寸
//        )

        drawCircle(Color.Yellow, )
    }
}

class SilkVisual(private val silkComponent: SilkComponent) : Visual {

    private val path = Path()

    override fun DrawScope.draw() { // 纯粹的局部绘制
        val silk = silkComponent
        val color = silk.type.color
        path.reset()
        val nodes = silk.nodes
        if (nodes.isNotEmpty()) {
            path.moveTo(nodes[0].x, nodes[0].y)
            for (i in 0 until nodes.size - 1) {
                val p1 = nodes[i]
                val p2 = nodes[i + 1]
                path.quadraticTo(p1.x, p1.y, (p1.x + p2.x) / 2, (p1.y + p2.y) / 2)
            }
            path.lineTo(nodes.last().x, nodes.last().y)
        }

        drawPath(path, color.copy(0.2f), style = Stroke(20f, cap = StrokeCap.Round))
        drawPath(path, color, style = Stroke(5f, cap = StrokeCap.Round))
        drawPath(path, Color.White, style = Stroke(2f, cap = StrokeCap.Round))
    }

}

// --- 系统: 物理 (改造：处理 Local ↔ World 转换) ---
class SilkPhysicsSystem(
    val input: InputManager = inject(),
    val coordinateTransform: CoordinateTransform = inject()
) : IntervalSystem() {

    private val playerFamily = family { all(PlayerTag, Transform) }
    private val silkFamily = family { all(SilkComponent, Transform) }


    override fun onTick() {
        val player = playerFamily.firstOrNull() ?: return
        val rootWorldPos = player[Transform].position

        // 假设 coordinateTransform.screenToWorld 使用了 scratch object
        val targetWorldPos = coordinateTransform.screenToWorld(input.pointerPosition)
        val isPointerDown = input.isPointerDown

        silkFamily.forEach {
            val silk = it[SilkComponent]
            val silkTransform = it[Transform]

            // 实体在世界中的原点 (Origin)
            val silkWorldOrigin = silkTransform.position

            // 💥 零分配核心：直接操作 silk.nodes 列表
            // updateVerlet 函数现在负责使用 silkWorldOrigin 偏移量，
            // 将 World 坐标逻辑转换为 Local 坐标操作
            updateVerlet(
                silk.type,
                silk.nodes,
                rootWorldPos,
                targetWorldPos,
                silkWorldOrigin,
                isPointerDown
            )
        }
    }

    /**
     * 执行 Verlet 积分和约束求解。
     * 所有读写操作都直接在 nodes 列表的局部坐标上进行，
     * 仅在设置锚点时，使用 origin 偏移进行世界坐标转换。
     */
    private fun updateVerlet(
        type: WuXing,
        nodes: MutableList<SilkNode>,
        rootWorldPos: Offset,
        tipWorldPos: Offset,
        origin: Offset, // 实体的世界坐标原点
        isTipPinned: Boolean // 💥 新增参数
    ) {
        if (nodes.isEmpty()) return

        // ... (保持 stiffness, drag, WuXing logic 不变) ...
        val (stiffness, drag) = when (type) {
            WuXing.Metal -> 40 to 0.65f
            WuXing.Water -> 4 to 0.94f
            WuXing.Fire -> 8 to 0.80f
            WuXing.Earth -> 30 to 0.70f
            else -> 15 to 0.85f
        }

        // 1. 锚定点修正 (Anchoring) - 需转换为 Local 坐标
        // 目标世界位置 - 世界原点 = 局部位置
        nodes.first().apply {
            x = rootWorldPos.x - origin.x
            y = rootWorldPos.y - origin.y
            pinned = true
        }

        // 💥 条件锚定：只有当鼠标按下时，才锚定末端顶点
        nodes.last().apply {
            pinned = isTipPinned // 设置锚定状态
            if (isTipPinned) {
                // 如果锚定，强制设置到目标位置
                x = tipWorldPos.x - origin.x
                y = tipWorldPos.y - origin.y
            }
        }

        // 2. Verlet 积分 (Verlet Integration) - 运动计算在 Local Space 中与原点无关
        for (i in 1 until nodes.size - 1) {
            val p = nodes[i]
            val vx = (p.x - p.oldX) * drag
            val vy = (p.y - p.oldY) * drag
            p.oldX = p.x; p.oldY = p.y
            p.x += vx; p.y += vy
            if (type == WuXing.Fire) {
                p.x += (Random.nextFloat() - 0.5f) * 4f; p.y += (Random.nextFloat() - 0.5f) * 4f
            }
        }

        // 3. 约束求解 (Constraint Solving) - 距离差计算与原点无关 (平移不变性)
        repeat(stiffness) {
            for (i in 0 until nodes.size - 1) {
                val p1 = nodes[i];
                val p2 = nodes[i + 1]

                // 由于距离差 (dx, dy) 具有平移不变性，我们直接使用 Local 坐标计算距离
                val dx = p2.x - p1.x;
                val dy = p2.y - p1.y

                val dist = hypot(dx, dy);
                val diff = 10f - dist

                if (dist > 0) {
                    val percent = diff / dist / 2f
                    val ox = dx * percent;
                    val oy = dy * percent

                    // 修正结果直接写回 Local 坐标
                    if (!p1.pinned) {
                        p1.x -= ox; p1.y -= oy
                    }
                    if (!p2.pinned) {
                        p2.x += ox; p2.y += oy
                    }
                }
            }
        }
    }
}

// --- 系统: 绞杀 (改造：处理 Local -> World 转换) ---
class CollisionSystem(
    assets: AssetsManager = inject(),
    val audio: AudioManager = inject()
) : IntervalSystem() {

    private val silkFamily = family { all(SilkComponent, Transform) }
    private val enemyFamily = family { all(EnemyTag) }

    private val eatSound = assets[GameAssets.Sound.Eat]

    override fun onTick() {
        // 1. 获取剑丝数据及 Transform
        val silkEntity = silkFamily.firstOrNull() ?: return
        val silk = silkEntity[SilkComponent]
        val silkTransform = silkEntity[Transform]

        val worldOrigin = silkTransform.position
        val localNodes = silk.nodes

        // 💥 Local -> World: 转换为世界坐标节点列表，用于碰撞检测
        val worldNodes = localNodes.map { localNode ->
            Offset(x = localNode.x + worldOrigin.x, y = localNode.y + worldOrigin.y)
        }

        // 2. 计算是否闭环 (使用世界坐标)
        val head = worldNodes.first()
        val tail = worldNodes.last()
        val isClosed = (head.x - tail.x) * (head.x - tail.x) +
                (head.y - tail.y) * (head.y - tail.y) < 200 * 200

        // 3. 遍历所有敌人
        enemyFamily.forEach {
            val enemy = it[EnemyTag]
            val rigidBody = it[Rigidbody]
            val transform = it[Transform]

            val enemyPos = transform.position

            // --- 逻辑 A: 绞杀判定 (使用世界坐标) ---
            enemy.isTrapped = if (isClosed) isPointInPolygon(enemyPos, worldNodes) else false

            // --- 逻辑 B: 物理切割/碰撞 (使用世界坐标) ---
            if (!enemy.isTrapped) {
                // 遍历剑丝的每一段线段 (使用世界坐标)
                for (i in 0 until worldNodes.size - 1) {
                    val p1 = worldNodes[i]
                    val p2 = worldNodes[i + 1]

                    // 计算圆心到线段的距离平方 (使用世界坐标)
                    val distSq = distToSegmentSquared(enemyPos, p1, p2)

                    // 判定阈值：(敌人半径 + 剑丝粗细)^2
                    val hitRadius = enemy.radius + 5f
                    if (distSq < hitRadius * hitRadius) {
//                        audio.playSound(eatSound)

                        val baseImpulse = 50f

                        // ⚡️ 极简调用：将线段和冲量大小直接交给 Rigidbody 处理 (使用世界坐标)
                        rigidBody.applyImpulseFromSegment(
                            segmentStart = p1,
                            segmentEnd = p2,
                            center = transform.position,
                            magnitude = baseImpulse
                        )

                        break // 一帧只撞一次，避免鬼畜
                    }
                }
            }
        }
    }

    // 💥 完整实现：使用 Offset 列表作为多边形顶点
    private fun isPointInPolygon(p: Offset, nodes: List<Offset>): Boolean {
        var inside = false
        var j = nodes.size - 1
        for (i in nodes.indices) {
            if ((nodes[i].y > p.y) != (nodes[j].y > p.y) &&
                (p.x < (nodes[j].x - nodes[i].x) * (p.y - nodes[i].y) / (nodes[j].y - nodes[i].y) + nodes[i].x)
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    // 💥 完整实现
    private fun distToSegmentSquared(p: Offset, v: Offset, w: Offset): Float {
        val l2 = (v - w).getDistanceSquared()
        if (l2 == 0f) return (p - v).getDistanceSquared()

        // 投影点 t (0..1)
        var t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2
        t = t.coerceIn(0f, 1f)

        // 投影坐标
        val projX = v.x + t * (w.x - v.x)
        val projY = v.y + t * (w.y - v.y)

        val dx = p.x - projX
        val dy = p.y - projY

        return dx * dx + dy * dy
    }
}

// --- 系统: 控制 ---
class SilkControlSystem(
    val input: InputManager = inject()
) : IteratingSystem(
    family = family { all(SilkComponent) }
) {

    override fun onTickEntity(entity: Entity) {
        val silk = entity[SilkComponent]
        if (input.isKeyDown(Key.One)) silk.type = WuXing.Metal
        if (input.isKeyDown(Key.Two)) silk.type = WuXing.Wood
        if (input.isKeyDown(Key.Three)) silk.type = WuXing.Water
        if (input.isKeyDown(Key.Four)) silk.type = WuXing.Fire
        if (input.isKeyDown(Key.Five)) silk.type = WuXing.Earth
    }

}

// -- 系统：玩家控制 ---
class PlayerControlSystem(
    val input: InputManager = inject()
) : IteratingSystem(
    family = family { all(PlayerTag, Transform) }
) {


    private val keyTrigger by lazy { KeyTrigger(Key.Six) }
    private val keyTrigger2 by lazy { KeyTrigger(Key.Seven) }

    private var currentCamera = "player"

    override fun onTick() {
        super.onTick()

        keyTrigger.check(input) {
            world.system<CameraSystem>().apply {
                currentCamera = if (currentCamera == "player") {
                    switchCameraSmoothly("enemy"); "enemy"
                } else {
                    switchCameraSmoothly("player"); "player"
                }
            }
        }

        keyTrigger2.check(input) {
            val system = world.system<CameraSystem>()
//            val camera = system.findActiveCamera() ?: return@check
//            camera.shake(10f)
            val x = (-400f..1200f).random()
            val y = (-300f..900f).random()
            system.panTo(Offset(x, y))
        }
    }

    override fun onTickEntity(entity: Entity) {
        val playerTransform = entity[Transform]

        // 2. 初始化位移向量
        var deltaX = 0f
        var deltaY = 0f

        // 3. 读取输入并计算位移
        // W/上箭头：向上移动 (Y轴负方向)
        if (input.isKeyDown(Key.W) || input.isKeyDown(Key.DirectionUp)) {
            deltaY -= 1f
        }
        // S/下箭头：向下移动 (Y轴正方向)
        if (input.isKeyDown(Key.S) || input.isKeyDown(Key.DirectionDown)) {
            deltaY += 1f
        }
        // A/左箭头：向左移动 (X轴负方向)
        if (input.isKeyDown(Key.A) || input.isKeyDown(Key.DirectionLeft)) {
            deltaX -= 1f
        }
        // D/右箭头：向右移动 (X轴正方向)
        if (input.isKeyDown(Key.D) || input.isKeyDown(Key.DirectionRight)) {
            deltaX += 1f
        }

        playerTransform.applyMovement(
            deltaTime = deltaTime,
            rawDeltaX = deltaX,
            rawDeltaY = deltaY,
            speed = 100f
        )
    }
}

object GameAssets {

    object Texture {
        val Player = ImageKey("drawable/image.jpeg")
    }

    object Sound {
        val Eat = SoundKey("files/eat.mp3")
    }

    object Music {
        val BGM = MusicKey("files/bgm3.wav")
    }

}

data object Menu

data class Battle(val value: String)

@Composable
fun GameDemo(context: PlatformContext) {
    val sceneStack = rememberGameSceneStack<Any>(Menu)
    KGame(
        context = context,
        sceneStack = sceneStack,
        modifier = Modifier.fillMaxSize(),
    ) {
        // --- 场景 1: 菜单 ---
        scene<Menu> {
            resources {
                it += GameAssets.Music.BGM
                it += GameAssets.Sound.Eat
            }

            onEnter {
                println("Menu Scene Entered")
            }

            onExit {
                println("Menu Scene Exited")
            }

            onUpdate {
                if (input.isKeyDown(Key.Spacebar)) {
                    sceneStack.push(Battle("From Key Event"))
                }
            }

            onForegroundUI {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text("=== 跨平台测试用例 ===", style = MaterialTheme.typography.titleLarge)
                    Text("按 [SPACE] 开始", style = MaterialTheme.typography.bodyLarge)

                    Button(
                        onClick = {
                            sceneStack.push(Battle("From Click Event"))
                        }
                    ) {
                        Text("开始")
                    }
                }
            }
        }

        // --- 场景 2: 战斗 (改造：添加 Renderable) ---
        scene<Battle> {
            world(configuration = {
                systems {
                    // 1. 输入/控制：处理意图
                    +PlayerControlSystem()
                    +SilkControlSystem()

                    // 2. 逻辑/计算：将意图转化为力
                    +SteeringSystem()

                    // 3. 物理/运动：通用运动计算
                    +PhysicsSystem()

                    // 4. 物理/特殊：处理 Silk 特有约束
                    +SilkPhysicsSystem()

                    // 5. 修正/解决：修复碰撞后的位置
                    +CollisionSystem()

                    // 6. 工具/辅助：更新摄像机
                    +CameraSystem()

                    // 7. 输出/渲染：永远在最后
                    +RenderSystem()
                }
            }) {
                val mapBounds = Rect(-800f, -600f, 800f, 600f)
                val safeBounds = viewportTransform.coerceViewportSafeBounds(mapBounds)

                val player = entity {
                    it += Transform()
                    it += Renderable(PlayerVisual(assets), zIndex = 1)
                    it += PlayerTag()
                }

                entity {
                    it += Transform()
                    it += SpringEffect(stiffness = 20f, damping = 5f)
                    it += CameraTarget("player", player)
                    it += Camera(isMain = true, mapBounds = mapBounds)
                }

                entity {
                    val silk = SilkComponent(WuXing.Water)
                    it += Transform()
                    it += silk
                    it += Renderable(SilkVisual(silk), zIndex = 2)
                }

                val enemy = entity {
                    it += Transform(safeBounds.randomOffset())
                    it += Rigidbody()
                    it += EnemyTag()
                    it += Renderable(EnemyVisual(EnemyTag(), color = Color.Magenta))
                }

                entity {
                    it += Transform()
                    it += SpringEffect(stiffness = 20f, damping = 5f)
                    it += CameraTarget("enemy", enemy)
                    it += Camera(isActive = false, mapBounds = mapBounds)
                }

                entities(100) {
                    val enemy = EnemyTag()
                    it += Transform(mapBounds.randomOffset())
                    it += Rigidbody()
                    it += enemy
                    it += Renderable(EnemyVisual(enemy))
                }
            }

            resources {
                it += GameAssets.Texture.Player
                it += GameAssets.Sound.Eat
                it += GameAssets.Music.BGM
            }

            onEnter {
                println("Battle Scene Entered: ${key.value}")
//                audio.playMusic(assets[GameAssets.Music.BGM], loop = true)
            }

            onExit {
                println("Battle Scene Exited")
                audio.stopMusic()
            }

            onUpdate {
                if (input.isKeyUp(Key.Escape)) {
                    sceneStack.pop()
                }
                if (input.isKeyUp(Key.Back)) {
                    sceneStack.pop()
                }
            }

            onForegroundUI {
                GameJoypad(
                    onValue = { it.applyToInput(input) }
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text("Battle Mode | FPS: $fps")
                    Text("[1-5] Switch Element  [ESC] Menu")

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val keys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)
                        for ((index, key) in keys.withIndex()) {
                            Button(modifier = Modifier.size(30.dp, 20.dp), onClick = {
                                input.simulateKey(key)
                            }, contentPadding = PaddingValues(0.dp)) {
                                Text("$index")
                            }
                        }
                        Button(modifier = Modifier.size(40.dp, 20.dp), onClick = {
                            sceneStack.pop()
                        }, contentPadding = PaddingValues(0.dp)) {
                            Text("退出")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleGameDemo(context: PlatformContext) {
    KSimpleGame(
        context = context,
        modifier = Modifier.fillMaxSize(),
    ) {
        var color = Color.Red

        onEnter {
            println("Game Entered")
        }

        onExit {
            println("Menu Scene Exited")
        }

        onEnable {
            println("Game Enabled")
        }

        onDisable {
            println("Game Disabled")
        }

        onUpdate {
            if (input.isKeyDown(Key.Spacebar)) {
                color = if (color == Color.Red) Color.Yellow else Color.Red
            }
        }

        onRender {
            drawCircle(color, radius = 50f)
        }

        onBackgroundUI {
            Rectangle(Color.Blue)
        }

        onForegroundUI {
            Button(
                onClick = {

                } ,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp)
            ) {
                Text(
                    text = "Hello World",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            Text("Fps: $fps")
        }
    }
}