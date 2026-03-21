@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package com.example.kgame.games.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import com.example.kgame.games.GameAssets
import com.kgame.ecs.*
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.asset.AssetsManager
import com.kgame.engine.asset.load
import com.kgame.engine.audio.AudioManager
import com.kgame.engine.core.KGame
import com.kgame.engine.core.rememberGameSceneStack
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.randomOffset
import com.kgame.engine.ui.LocalWindowManager
import com.kgame.engine.ui.Rectangle
import com.kgame.engine.ui.Window
import com.kgame.engine.ui.WindowHeader
import com.kgame.engine.utils.FpsCalculator
import com.kgame.plugins.components.*
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.systems.*
import com.kgame.plugins.visuals.Visual
import com.kgame.plugins.visuals.images.SpriteVisual
import com.kgame.plugins.visuals.shapes.PolygonVisual
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

private enum class WuXing(val color: Color) {
    Metal(Color(0xFFFFD700)),
    Wood(Color(0xFF69F0AE)),
    Water(Color(0xFF40C4FF)),
    Fire(Color(0xFFFF5252)),
    Earth(Color(0xFF8D6E63))
}

private class PlayerTag : Component<PlayerTag> {
    override fun type() = PlayerTag
    companion object : ComponentType<PlayerTag>()
}

private class EnemyTag(var isTrapped: Boolean = false) : Component<EnemyTag> {
    override fun type() = EnemyTag
    companion object : ComponentType<EnemyTag>()
}

private data class SilkNode(
    var x: Float,
    var y: Float,
    var oldX: Float = x,
    var oldY: Float = y,
    var worldX: Float = 0f,
    var worldY: Float = 0f,
    var pinned: Boolean = false
)

private data class SilkComponent(
    var type: WuXing = WuXing.Water,
    val nodes: ArrayList<SilkNode> = ArrayList()
) : Component<SilkComponent> {
    override fun type() = SilkComponent
    companion object : ComponentType<SilkComponent>()

    init {
        repeat(40) { nodes.add(SilkNode(0f, 0f)) }
    }
}

private class SilkBounds(
    var minX: Float = 0f,
    var minY: Float = 0f,
    var maxX: Float = 0f,
    var maxY: Float = 0f
) : Component<SilkBounds> {
    override fun type() = SilkBounds
    companion object : ComponentType<SilkBounds>()
}

private class EnemyVisual(private val enemyTag: EnemyTag, val color: Color, size: Size) : Visual(size) {
    private val delegate = PolygonVisual(size, color, 8)
    override fun DrawScope.draw() {
        delegate.color = if (enemyTag.isTrapped) Color.Black else color
        delegate.alpha = alpha
        with(delegate) { draw() }
    }
}

private class PlayerVisual(assets: AssetsManager) : Visual() {
    override fun DrawScope.draw() {
        drawCircle(Color.Yellow, alpha = alpha)
    }
}

private class SilkVisual(private val silkComponent: SilkComponent) : Visual() {
    private val path = Path()
    override fun DrawScope.draw() {
        path.reset()
        val nodes = silkComponent.nodes
        if (nodes.isEmpty()) return

        val halfW = size.width / 2f
        val halfH = size.height / 2f

        path.moveTo(nodes[0].x + halfW, nodes[0].y + halfH)
        for (i in 0 until nodes.size - 1) {
            val p1 = nodes[i]
            val p2 = nodes[i + 1]
            path.quadraticTo(p1.x + halfW, p1.y + halfH, (p1.x + p2.x) / 2 + halfW, (p1.y + p2.y) / 2 + halfH)
        }
        path.lineTo(nodes.last().x + halfW, nodes.last().y + halfH)

        drawPath(path, silkComponent.type.color.copy(0.2f), style = Stroke(20f, cap = StrokeCap.Round), alpha = alpha)
        drawPath(path, silkComponent.type.color, style = Stroke(5f, cap = StrokeCap.Round), alpha = alpha)
        drawPath(path, Color.White, style = Stroke(2f, cap = StrokeCap.Round), alpha = alpha)
    }
}

private class SilkPhysicsSystem(
    val input: InputManager = inject(),
    val cameraService: CameraService = inject()
) : IntervalSystem() {
    private val playerFamily = family { all(PlayerTag, Transform) }
    private val silkFamily = family { all(SilkComponent, Transform, SilkBounds) }

    override fun onTick(deltaTime: Float) {
        val player = playerFamily.firstOrNull() ?: return
        val rootWorldPos = player[Transform].position
        val targetWorldPos = cameraService.transformer.virtualToWorld(input.getPointerPosition())
        val isPointerDown = input.isPointerDown

        silkFamily.forEach {
            val silk = it[SilkComponent]
            val silkTransform = it[Transform]
            val silkWorldOrigin = silkTransform.position
            val localNodes = silk.nodes

            updateVerlet(silk.type, localNodes, rootWorldPos, targetWorldPos, silkWorldOrigin, isPointerDown)

            localNodes.forEach { node ->
                node.worldX = node.x + silkWorldOrigin.x
                node.worldY = node.y + silkWorldOrigin.y
            }

            val silkBounds = it[SilkBounds]
            if (localNodes.isNotEmpty()) {
                silkBounds.minX = localNodes.minOf { n -> n.worldX }
                silkBounds.minY = localNodes.minOf { n -> n.worldY }
                silkBounds.maxX = localNodes.maxOf { n -> n.worldX }
                silkBounds.maxY = localNodes.maxOf { n -> n.worldY }
            }
        }
    }

    private fun updateVerlet(type: WuXing, nodes: MutableList<SilkNode>, rootWorldPos: Offset, tipWorldPos: Offset, origin: Offset, isTipPinned: Boolean) {
        if (nodes.isEmpty()) return
        val (stiffness, drag) = when (type) {
            WuXing.Metal -> 40 to 0.65f
            WuXing.Water -> 4 to 0.94f
            WuXing.Fire -> 8 to 0.80f
            WuXing.Earth -> 30 to 0.70f
            else -> 15 to 0.85f
        }
        nodes.first().apply { x = rootWorldPos.x - origin.x; y = rootWorldPos.y - origin.y; pinned = true }
        nodes.last().apply { pinned = isTipPinned; if (isTipPinned) { x = tipWorldPos.x - origin.x; y = tipWorldPos.y - origin.y } }

        for (i in 1 until nodes.size - 1) {
            val p = nodes[i]
            val vx = (p.x - p.oldX) * drag
            val vy = (p.y - p.oldY) * drag
            p.oldX = p.x; p.oldY = p.y
            p.x += vx; p.y += vy
            if (type == WuXing.Fire) {
                p.x += (Random.nextFloat() - 0.5f) * 4f
                p.y += (Random.nextFloat() - 0.5f) * 4f
            }
        }

        repeat(stiffness) {
            for (j in 0 until nodes.size - 1) {
                val p1 = nodes[j]; val p2 = nodes[j + 1]
                val dx = p2.x - p1.x; val dy = p2.y - p1.y
                val dist = hypot(dx, dy)
                val diff = 10f - dist
                if (dist > 0) {
                    val percent = diff / dist / 2f
                    val ox = dx * percent; val oy = dy * percent
                    if (!p1.pinned) { p1.x -= ox; p1.y -= oy }
                    if (!p2.pinned) { p2.x += ox; p2.y += oy }
                }
            }
        }
    }
}

private class SilkCollisionSystem(
    assets: AssetsManager = inject(),
    val audio: AudioManager = inject()
) : IntervalSystem() {
    private val silkFamily = family { all(SilkComponent, Transform, SilkBounds) }
    private val enemyFamily = family { all(EnemyTag, RigidBody, Transform) }
    private val eatSound = assets[GameAssets.Sound.Eat]

    override fun onTick(deltaTime: Float) {
        val silkEntity = silkFamily.firstOrNull() ?: return
        val silk = silkEntity[SilkComponent]
        val silkBounds = silkEntity[SilkBounds]
        val nodes = silk.nodes
        if (nodes.size < 2) return

        enemyFamily.forEach {
            val enemy = it[EnemyTag]
            val rb = it[RigidBody]
            val trans = it[Transform]
            val radius = it[Renderable].size.width / 2f + 5f
            val pos = trans.position

            if (pos.x + radius < silkBounds.minX || pos.x - radius > silkBounds.maxX || pos.y + radius < silkBounds.minY || pos.y - radius > silkBounds.maxY) {
                enemy.isTrapped = false
                return@forEach
            }

            enemy.isTrapped = (nodes.first().worldX - nodes.last().worldX).let { dx -> dx*dx } + (nodes.first().worldY - nodes.last().worldY).let { dy -> dy*dy } < 40000f && isPointInPolygon(pos, nodes)

            if (!enemy.isTrapped) {
                for (i in 0 until nodes.size - 1) {
                    val p1 = nodes[i]; val p2 = nodes[i + 1]
                    if (distToSegmentSquared(pos, Offset(p1.worldX, p1.worldY), Offset(p2.worldX, p2.worldY)) < radius * radius) {
                        audio.playSound(eatSound)
                        rb.applyImpulseFromSegment(Offset(p1.worldX, p1.worldY), Offset(p2.worldX, p2.worldY), pos, 50f)
                        break
                    }
                }
            }
        }
    }

    private fun isPointInPolygon(p: Offset, nodes: List<SilkNode>): Boolean {
        var inside = false; var j = nodes.size - 1
        for (i in nodes.indices) {
            if ((nodes[i].worldY > p.y) != (nodes[j].worldY > p.y) && (p.x < (nodes[j].worldX - nodes[i].worldX) * (p.y - nodes[i].worldY) / (nodes[j].worldY - nodes[i].worldY) + nodes[i].worldX)) inside = !inside
            j = i
        }
        return inside
    }

    private fun distToSegmentSquared(p: Offset, v: Offset, w: Offset): Float {
        val l2 = (v - w).getDistanceSquared()
        if (l2 == 0f) return (p - v).getDistanceSquared()
        val t = (((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2).coerceIn(0f, 1f)
        return (p - Offset(v.x + t * (w.x - v.x), v.y + t * (w.y - v.y))).getDistanceSquared()
    }
}

private class SilkControlSystem(val input: InputManager = inject()) : IteratingSystem(family { all(SilkComponent) }) {
    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val silk = entity[SilkComponent]
        silk.type = when {
            input.isKeyDown(Key.One) -> WuXing.Metal
            input.isKeyDown(Key.Two) -> WuXing.Wood
            input.isKeyDown(Key.Three) -> WuXing.Water
            input.isKeyDown(Key.Four) -> WuXing.Fire
            input.isKeyDown(Key.Five) -> WuXing.Earth
            else -> silk.type
        }
    }
}

private class PlayerControlSystem(val input: InputManager = inject(), val cameraService: CameraService = inject()) : IteratingSystem(family { all(PlayerTag, Transform, Movement) }) {
    private var currentCamera = "player"
    override fun onTick(deltaTime: Float) {
        super.onTick(deltaTime)
        if (input.isKeyJustPressed(Key.Six)) {
            currentCamera = if (currentCamera == "player") { cameraService.director.switchCameraSmoothly("enemy"); "enemy" } else { cameraService.director.switchCameraSmoothly("player"); "player" }
        }
        if (input.isKeyJustPressed(Key.Seven)) cameraService.director.shake(1f)
    }
    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val t = entity[Transform]; val m = entity[Movement]
        var dx = 0f; var dy = 0f
        if (input.isKeyDown(Key.W) || input.isKeyDown(Key.DirectionUp)) dy -= 1f
        if (input.isKeyDown(Key.S) || input.isKeyDown(Key.DirectionDown)) dy += 1f
        if (input.isKeyDown(Key.A) || input.isKeyDown(Key.DirectionLeft)) { dx -= 1f; t.applyScale(scaleX = -1f, scaleY = 1f) }
        if (input.isKeyDown(Key.D) || input.isKeyDown(Key.DirectionRight)) { dx += 1f; t.applyScale(scaleX = 1f, scaleY = 1f) }
        m.step(t, dx, dy, deltaTime)
    }
}

@Composable
fun SampleGame() {
    val sceneStack = rememberGameSceneStack<Any>(Menu)
    KGame(sceneStack = sceneStack) {
        scene<Menu> {
            onUpdate { if (input.isKeyDown(Key.Spacebar)) sceneStack.push(Battle("Key")) }
            onBackgroundUI { Rectangle(Color.White) }
            onForegroundUI {
                Box(Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text("=== Cross-Platform Test Case ===", style = MaterialTheme.typography.titleLarge)
                        Text("Press [SPACE] to Start", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { sceneStack.push(Battle("Click")) }) { Text("Start") }
                    }
                }
            }
        }
        scene<Battle> {
            onWorld {
                configure {
                    systems { +PlayerControlSystem(); +SilkControlSystem(); +SteeringSystem(); +PhysicsSystem(); +SilkPhysicsSystem(); +SilkCollisionSystem(); +TiledMapCollisionSystem(); +CameraSystem(); +AnimationTickSystem(); +AnimationSystem(); +TiledMapRenderSystem(); +RenderSystem() }
                }
                spawn {
                    val bounds = Rect(-640f, -600f, 640f, 600f)
                    entity { +TiledMap(assets[GameAssets.TiledMap.Example]) }
                    val player = entity {
                        +Transform(position = Offset(0f, -100f))
                        +PlayerTag(); +Movement(120f, 120f)
                        +Hitbox(Rect(left = -15f, top = -10f, right = 15f, bottom = 25f))
                        +SpriteAnimation("run")
                        +Renderable(SpriteVisual(assets[GameAssets.Atlas.Walk], "frame_0_0", size = Size(50f, 50f)), zIndex = 1)
                    }
                    entity { +Transform(position = Offset(0f, -100f)); +Elasticity(stiffness = 80f, damping = 10f); +Movement(); +CameraTarget(player); +CameraShake(); +Camera("player", isMain = true, bounds = bounds) }
                    entity { val silk = SilkComponent(WuXing.Water); +Transform(); +silk; +Renderable(SilkVisual(silk), zIndex = 99); +SilkBounds() }
                    val enemy = entity { +Transform(bounds.randomOffset()); +RigidBody(); +EnemyTag(); +Renderable(EnemyVisual(EnemyTag(), color = Color.Green, size = Size(50f, 50f))) }
                    entity { +Transform(); +Elasticity(stiffness = 80f, damping = 10f); +Movement(); +CameraTarget(enemy); +CameraShake(); +Camera("enemy", bounds = bounds) }
                }
            }
            onCreate {
                assets.load(GameAssets.Image.Player, GameAssets.Sound.Eat, GameAssets.Music.BGM, GameAssets.Atlas.Walk, GameAssets.TiledMap.Example)
                TiledMapRenderSystem.isDebugging = true
            }
            onStart { audio.playMusic(assets[GameAssets.Music.BGM], loop = true) }
            onDestroy { audio.stopMusic() }
            onUpdate { if (input.isKeyJustReleased(Key.Escape) || input.isKeyJustReleased(Key.Back)) sceneStack.pop() }
            val fpsCalculator = FpsCalculator()
            onRender { fpsCalculator.advanceFrame() }
            onBackgroundUI { Rectangle(Color.Black) }
            onForegroundUI {
                val windowManager = LocalWindowManager.current
                Box(Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Battle Mode | FPS: ${fpsCalculator.fps}")
                        Text("[1-5] Switch Element  [ESC] Menu")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(5) { i -> Button(modifier = Modifier.size(30.dp, 20.dp), onClick = { input.simulateKey(listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)[i]) }, contentPadding = PaddingValues(0.dp)) { Text("${i + 1}") } }
                            Button(modifier = Modifier.size(40.dp, 20.dp), onClick = { windowManager.addWindow(TestWindow()) }, contentPadding = PaddingValues(0.dp)) { Text("Window") }
                            Button(modifier = Modifier.size(40.dp, 20.dp), onClick = { sceneStack.pop() }, contentPadding = PaddingValues(0.dp)) { Text("Exit") }
                        }
                    }
                }
            }
        }
    }
}

private class TestWindow : Window(200.dp, 200.dp) {
    private var text by mutableStateOf("")
    @Composable override fun Content() {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.fillMaxSize().padding(8.dp)) {
            WindowHeader(title = "Window$id")
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text("This is Window #${id}")
                TextField(text, onValueChange = { text = it })
                Spacer(Modifier.height(10.dp))
                Button(onClick = { dismiss() }) { Text("Dismiss") }
            }
        }
    }
}

private data object Menu
private data class Battle(val type: String)
