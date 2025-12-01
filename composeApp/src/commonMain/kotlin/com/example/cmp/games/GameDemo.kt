@file:OptIn(ExperimentalTime::class)

package com.example.cmp.games

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
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
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.unit.dp
import cmp.composeapp.generated.resources.Res
import com.game.ecs.Component
import com.game.ecs.ComponentType
import com.game.ecs.Entity
import com.game.ecs.IntervalSystem
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.World.Companion.inject
import com.game.engine.asset.AssetsManager
import com.game.engine.asset.AtlasKey
import com.game.engine.asset.ImageKey
import com.game.engine.asset.MusicKey
import com.game.engine.asset.ResourceProvider
import com.game.engine.asset.SoundKey
import com.game.engine.audio.AudioManager
import com.game.engine.context.PlatformContext
import com.game.engine.core.KGame
import com.game.engine.core.KSimpleGame
import com.game.engine.core.rememberGameSceneStack
import com.game.engine.geometry.ViewportTransform
import com.game.engine.geometry.clampInBounds
import com.game.engine.geometry.safeBounds
import com.game.engine.graphics.shader.GlossyGradient
import com.game.engine.graphics.shader.MeshGradient
import com.game.engine.input.InputManager
import com.game.engine.math.random
import com.game.engine.math.randomOffset
import com.game.engine.ui.ActiveRectangle
import com.game.engine.ui.GameJoypad
import com.game.engine.ui.Rectangle
import com.game.engine.ui.applyToInput
import com.game.engine.utils.FpsCalculator
import com.game.engine.utils.KeyTrigger
import com.game.plugins.components.AlphaAnimation
import com.game.plugins.components.Camera
import com.game.plugins.components.CameraTarget
import com.game.plugins.components.Elasticity
import com.game.plugins.components.InfiniteRepeatable
import com.game.plugins.components.Renderable
import com.game.plugins.components.RigidBody
import com.game.plugins.components.ScaleAnimation
import com.game.plugins.components.Spring
import com.game.plugins.components.Sprite
import com.game.plugins.components.SpriteAnimation
import com.game.plugins.components.Transform
import com.game.plugins.components.Tween
import com.game.plugins.components.Visual
import com.game.plugins.components.applyImpulseFromSegment
import com.game.plugins.components.applyKinematicMovement
import com.game.plugins.components.applyScale
import com.game.plugins.components.shake
import com.game.plugins.services.CameraService
import com.game.plugins.systems.AnimationSystem
import com.game.plugins.systems.CameraSystem
import com.game.plugins.systems.CollisionSystem
import com.game.plugins.systems.PhysicsSystem
import com.game.plugins.systems.RenderSystem
import com.game.plugins.systems.SteeringSystem
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.time.ExperimentalTime

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

class EnemyTag(var isTrapped: Boolean = false) : Component<EnemyTag> {
    override fun type() = EnemyTag
    companion object : ComponentType<EnemyTag>()
}

data class SilkNode(
    var x: Float,
    var y: Float,
    var oldX: Float = x,
    var oldY: Float = y,
    var worldX: Float = 0f,
    var worldY: Float = 0f,
    var pinned: Boolean = false
)

data class SilkComponent(
    var type: WuXing = WuXing.Water,
    val nodes: ArrayList<SilkNode> = ArrayList()
) : Component<SilkComponent> {
    override fun type() = SilkComponent
    companion object : ComponentType<SilkComponent>()

    init {
        var i = 0
        while (i < 40) {
            nodes.add(SilkNode(0f, 0f))
            i++
        }
    }
}

class SilkBounds(
    var minX: Float = 0f,
    var minY: Float = 0f,
    var maxX: Float = 0f,
    var maxY: Float = 0f
) : Component<SilkBounds> {
    override fun type() = SilkBounds
    companion object : ComponentType<SilkBounds>()
}

class EnemyVisual(private val enemyTag: EnemyTag, val color: Color? = null) : Visual() {
    override fun DrawScope.draw() {
        drawCircle(if (enemyTag.isTrapped) Color.Black else color ?: Color.Gray, alpha = alpha)
    }
}

class PlayerVisual(assets: AssetsManager) : Visual() {
    val player = assets[GameAssets.Image.Player]

    override fun DrawScope.draw() {
        drawCircle(Color.Yellow, alpha = alpha)
    }
}

class SilkVisual(private val silkComponent: SilkComponent) : Visual() {
    private val path = Path()

    override fun DrawScope.draw() {
        path.reset()
        val nodes = silkComponent.nodes
        if (nodes.isEmpty()) return

        val halfW = size.width / 2f
        val halfH = size.height / 2f

        path.moveTo(nodes[0].x + halfW, nodes[0].y + halfH)
        var i = 0
        while (i < nodes.size - 1) {
            val p1 = nodes[i]
            val p2 = nodes[i + 1]

            val p1x = p1.x + halfW
            val p1y = p1.y + halfH
            val p2x = p2.x + halfW
            val p2y = p2.y + halfH

            path.quadraticTo(p1x, p1y, (p1x + p2x) / 2, (p1y + p2y) / 2)
            i++
        }
        path.lineTo(nodes.last().x + halfW, nodes.last().y + halfH)


        drawPath(path, silkComponent.type.color.copy(0.2f), style = Stroke(20f, cap = StrokeCap.Round), alpha = alpha)
        drawPath(path, silkComponent.type.color, style = Stroke(5f, cap = StrokeCap.Round), alpha = alpha)
        drawPath(path, Color.White, style = Stroke(2f, cap = StrokeCap.Round), alpha = alpha)
    }
}

class SilkPhysicsSystem(
    val input: InputManager = inject(),
    val cameraService: CameraService = inject()
) : IntervalSystem() {

    private val playerFamily = family { all(PlayerTag, Transform) }
    private val silkFamily = family { all(SilkComponent, Transform, SilkBounds) }

    override fun onTick() {
        val player = playerFamily.firstOrNull() ?: return
        val rootWorldPos = player[Transform].position
        val targetWorldPos = cameraService.transformer.virtualToWorld(input.pointerPosition)
        val isPointerDown = input.isPointerDown

        silkFamily.forEach {
            val silk = it[SilkComponent]
            val silkTransform = it[Transform]

            val silkWorldOrigin = silkTransform.position
            val localNodes = silk.nodes

            updateVerlet(
                silk.type,
                localNodes,
                rootWorldPos,
                targetWorldPos,
                silkWorldOrigin,
                isPointerDown
            )

            var i = 0
            while (i < localNodes.size) {
                val node = localNodes[i]
                node.worldX = node.x + silkWorldOrigin.x
                node.worldY = node.y + silkWorldOrigin.y
                i++
            }

            // Update SilkBounds
            val silkBounds = it[SilkBounds]
            if (localNodes.isNotEmpty()) {
                var minX = localNodes[0].worldX
                var minY = localNodes[0].worldY
                var maxX = minX
                var maxY = minY

                var i = 1
                while (i < localNodes.size) {
                    val node = localNodes[i]
                    val wx = node.worldX
                    val wy = node.worldY

                    if (wx < minX) minX = wx
                    if (wy < minY) minY = wy
                    if (wx > maxX) maxX = wx
                    if (wy > maxY) maxY = wy
                    i++
                }

                silkBounds.minX = minX
                silkBounds.minY = minY
                silkBounds.maxX = maxX
                silkBounds.maxY = maxY
            }
        }
    }

    private fun updateVerlet(
        type: WuXing,
        nodes: MutableList<SilkNode>,
        rootWorldPos: Offset,
        tipWorldPos: Offset,
        origin: Offset,
        isTipPinned: Boolean
    ) {
        if (nodes.isEmpty()) return

        val (stiffness, drag) = when (type) {
            WuXing.Metal -> 40 to 0.65f
            WuXing.Water -> 4 to 0.94f
            WuXing.Fire -> 8 to 0.80f
            WuXing.Earth -> 30 to 0.70f
            else -> 15 to 0.85f
        }

        nodes.first().apply {
            x = rootWorldPos.x - origin.x
            y = rootWorldPos.y - origin.y
            pinned = true
        }

        nodes.last().apply {
            pinned = isTipPinned
            if (isTipPinned) {
                x = tipWorldPos.x - origin.x
                y = tipWorldPos.y - origin.y
            }
        }

        var i = 1
        while (i < nodes.size - 1) {
            val p = nodes[i]
            val vx = (p.x - p.oldX) * drag
            val vy = (p.y - p.oldY) * drag
            p.oldX = p.x; p.oldY = p.y
            p.x += vx; p.y += vy
            if (type == WuXing.Fire) {
                p.x += (Random.nextFloat() - 0.5f) * 4f
                p.y += (Random.nextFloat() - 0.5f) * 4f
            }
            i++
        }

        var k = 0
        while (k < stiffness) {
            var j = 0
            while (j < nodes.size - 1) {
                val p1 = nodes[j]
                val p2 = nodes[j + 1]

                val dx = p2.x - p1.x
                val dy = p2.y - p1.y

                val dist = hypot(dx, dy)
                val diff = 10f - dist

                if (dist > 0) {
                    val percent = diff / dist / 2f
                    val ox = dx * percent
                    val oy = dy * percent

                    if (!p1.pinned) {
                        p1.x -= ox; p1.y -= oy
                    }
                    if (!p2.pinned) {
                        p2.x += ox; p2.y += oy
                    }
                }
                j++
            }
            k++
        }
    }
}

class SilkCollisionSystem(
    assets: AssetsManager = inject(),
    val audio: AudioManager = inject()
) : IntervalSystem() {

    private val silkFamily = family { all(SilkComponent, Transform, SilkBounds) }
    private val enemyFamily = family { all(EnemyTag, RigidBody, Transform) }
    private val eatSound = assets[GameAssets.Sound.Eat]

    private fun Float.sq() = this * this

    override fun onTick() {
        val silkEntity = silkFamily.firstOrNull() ?: return
        val silk = silkEntity[SilkComponent]
        val silkBounds = silkEntity[SilkBounds]
        val nodes = silk.nodes

        if (nodes.size < 2) return

        val head = nodes.first()
        val tail = nodes.last()

        enemyFamily.forEach {
            val enemy = it[EnemyTag]
            val rigidBody = it[RigidBody]
            val transform = it[Transform]

            val enemyPos = transform.position
            val radius = transform.size.width / 2f + 5f

            // COARSE AABB CHECK: Use SilkBounds to quickly reject enemies far away
            val enemyMinX = enemyPos.x - radius
            val enemyMinY = enemyPos.y - radius
            val enemyMaxX = enemyPos.x + radius
            val enemyMaxY = enemyPos.y + radius

            val overlapX = enemyMaxX >= silkBounds.minX && enemyMinX <= silkBounds.maxX
            val overlapY = enemyMaxY >= silkBounds.minY && enemyMinY <= silkBounds.maxY

            if (!overlapX || !overlapY) {
                enemy.isTrapped = false
                return@forEach
            }
            // END COARSE AABB CHECK

            val dxClose = head.worldX - tail.worldX
            val dyClose = head.worldY - tail.worldY
            val isClosed = (dxClose.sq() + dyClose.sq()) < 200f.sq()

            enemy.isTrapped = isClosed && isPointInPolygon(enemyPos, nodes)

            if (!enemy.isTrapped) {
                val hitRadiusSq = radius.sq()
                var i = 0
                while (i < nodes.size - 1) {
                    val p1 = nodes[i]
                    val p2 = nodes[i + 1]

                    val distSq = distToSegmentSquared(
                        enemyPos,
                        Offset(p1.worldX, p1.worldY),
                        Offset(p2.worldX, p2.worldY)
                    )

                    if (distSq < hitRadiusSq) {
                        audio.playSound(eatSound)
                        rigidBody.applyImpulseFromSegment(
                            segmentStart = Offset(p1.worldX, p1.worldY),
                            segmentEnd = Offset(p2.worldX, p2.worldY),
                            center = enemyPos,
                            impulseMag = 50f
                        )
                        break
                    }
                    i++
                }
            }
        }
    }

    private fun isPointInPolygon(p: Offset, nodes: List<SilkNode>): Boolean {
        var inside = false
        var j = nodes.size - 1
        var i = 0
        while (i < nodes.size) {
            val nodeI = nodes[i]
            val nodeJ = nodes[j]
            if ((nodeI.worldY > p.y) != (nodeJ.worldY > p.y) &&
                (p.x < (nodeJ.worldX - nodeI.worldX) * (p.y - nodeI.worldY) / (nodeJ.worldY - nodeI.worldY) + nodeI.worldX)
            ) {
                inside = !inside
            }
            j = i
            i++
        }
        return inside
    }

    private fun distToSegmentSquared(p: Offset, v: Offset, w: Offset): Float {
        val l2 = (v - w).getDistanceSquared()
        if (l2 == 0f) return (p - v).getDistanceSquared()

        var t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2
        t = t.coerceIn(0f, 1f)

        val projX = v.x + t * (w.x - v.x)
        val projY = v.y + t * (w.y - v.y)

        val dx = p.x - projX
        val dy = p.y - projY

        return dx.sq() + dy.sq()
    }
}

class SilkControlSystem(
    val input: InputManager = inject()
) : IteratingSystem(
    family = family { all(SilkComponent) }
) {
    override fun onTickEntity(entity: Entity) {
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

class PlayerControlSystem(
    val input: InputManager = inject(),
    val cameraService: CameraService = inject()
) : IteratingSystem(
    family = family { all(PlayerTag, Transform) }
) {
    private var currentCamera = "player"

    override fun onTick() {
        super.onTick()
        KeyTrigger.check(input, Key.Six) {
            currentCamera = if (currentCamera == "player") {
                cameraService.director.switchCameraSmoothly("enemy")
                "enemy"
            } else {
                cameraService.director.switchCameraSmoothly("player")
                "player"
            }
        }
        KeyTrigger.check(input, Key.Seven) {
            cameraService.activeCamera?.shake(10f)
        }
    }

    override fun onTickEntity(entity: Entity) {
        val playerTransform = entity[Transform]
        var deltaX = 0f
        var deltaY = 0f

        if (input.isKeyDown(Key.W) || input.isKeyDown(Key.DirectionUp)) {
            deltaY -= 1f
        }
        if (input.isKeyDown(Key.S) || input.isKeyDown(Key.DirectionDown)) {
            deltaY += 1f
        }
        if (input.isKeyDown(Key.A) || input.isKeyDown(Key.DirectionLeft)) {
            deltaX -= 1f

            playerTransform.applyScale(scaleX = -1f, scaleY = 1f)
        }
        if (input.isKeyDown(Key.D) || input.isKeyDown(Key.DirectionRight)) {
            deltaX += 1f

            playerTransform.applyScale(scaleX = 1f, scaleY = 1f)
        }

        playerTransform.applyKinematicMovement(
            deltaTime = deltaTime,
            rawDeltaX = deltaX,
            rawDeltaY = deltaY,
            speed = 100f
        )
    }
}

object GameAssets {
    object Image {
        val Player = ImageKey("drawable/image.jpeg")
    }
    object Sound {
        val Eat = SoundKey("files/eat.mp3")
    }
    object Music {
        val BGM = MusicKey("files/bgm3.wav")
    }

    object Atlas {
        val Walk = AtlasKey("files/Walk.json")
    }
}

data object Menu
data class Battle(val value: String)

val DefaultResourceProvider = object : ResourceProvider {
    override suspend fun read(path: String): ByteArray = Res.readBytes(path)
    override fun getUri(path: String): String = Res.getUri(path)
}

@Composable
fun GameDemo(context: PlatformContext) {
    val sceneStack = rememberGameSceneStack<Any>(Menu)
    KGame(
        context = context,
        sceneStack = sceneStack,
        virtualSize = Size(600f, 800f),
        resourceProvider = DefaultResourceProvider,
        modifier = Modifier.fillMaxSize().preferredFrameRate(FrameRateCategory.High),
    ) {
        scene<Menu> {
            resources {
                it += GameAssets.Music.BGM
                it += GameAssets.Sound.Eat
            }

            onUpdate {
                if (input.isKeyDown(Key.Spacebar)) {
                    sceneStack.push(Battle("From Key Event"))
                }
            }

            onBackgroundUI { Rectangle(Color.White) }

            onForegroundUI {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text("=== 跨平台测试用例 ===", style = MaterialTheme.typography.titleLarge)
                    Text("按 [SPACE] 开始", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { sceneStack.push(Battle("From Click Event")) }) {
                        Text("开始")
                    }
                }
            }
        }

        scene<Battle> {
            world(configuration = {
                systems {
                    +PlayerControlSystem()
                    +SilkControlSystem()
                    +SteeringSystem()
                    +PhysicsSystem()
                    +SilkPhysicsSystem()
                    +SilkCollisionSystem()
                    +CameraSystem()
                    +AnimationSystem()
                    +RenderSystem()
                }
            }) {
                val mapBounds = Rect(-800f, -600f, 800f, 600f)
                val safeBounds = viewportTransform.safeBounds(mapBounds)

                val player = entity {
                    it += Transform(size = Size(50f, 50f))
                    it += PlayerTag()
                    it += SpriteAnimation("run")
                    it += Renderable(Sprite(assets[GameAssets.Atlas.Walk], "frame_0_0"), zIndex = 1)
                }

                entity {
                    it += Transform()
                    it += Elasticity(stiffness = 80f, damping = 10f)
                    it += RigidBody()
                    it += CameraTarget("player", player)
                    it += Camera(isMain = true, mapBounds = mapBounds)
                }

                entity {
                    val silk = SilkComponent(WuXing.Water)
                    it += Transform()
                    it += silk
                    it += Renderable(SilkVisual(silk), zIndex = 2)
                    it += SilkBounds()
                }

                val enemy = entity {
                    it += Transform(safeBounds.randomOffset(), size = Size(50f, 50f))
                    it += RigidBody()
                    it += EnemyTag()
                    it += Renderable(EnemyVisual(EnemyTag(), color = Color.Green))
                }

                entity {
                    it += Transform()
                    it += Elasticity(stiffness = 80f, damping = 10f)
                    it += RigidBody()
                    it += CameraTarget("enemy", enemy)
                    it += Camera(isActive = false, mapBounds = mapBounds)
                }

                entities(500) {
                    val enemyInstance = EnemyTag()
                    it += Transform(mapBounds.randomOffset(), size = Size(25f, 25f))
                    it += RigidBody()
                    it += enemyInstance
                    it += Renderable(EnemyVisual(enemyInstance, color = Color.random()))
                }
            }

            resources {
                it += GameAssets.Image.Player
                it += GameAssets.Sound.Eat
                it += GameAssets.Music.BGM
                it += GameAssets.Atlas.Walk
            }

            onEnter {
                audio.playMusic(assets[GameAssets.Music.BGM], loop = true)
                println("Game enter")
            }

            onExit {
                audio.stopMusic()
                println("Game exit")
            }

            onUpdate {
                if (input.isKeyUp(Key.Escape) || input.isKeyUp(Key.Back)) {
                    sceneStack.pop()
                }
            }

            val fpsCalculator = FpsCalculator()

            onRender { fpsCalculator.advanceFrame() }

            onBackgroundUI {
                ActiveRectangle(MeshGradient(arrayOf(Color.Red, Color.Blue, Color.Green, Color.Yellow)))
            }

            onForegroundUI {
                GameJoypad(onValue = { it.applyToInput(input) })
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Battle Mode | FPS: ${fpsCalculator.fps}")
                    Text("[1-5] Switch Element  [ESC] Menu")

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val keys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)
                        var i = 0
                        while (i < keys.size) {
                            val key = keys[i]
                            Button(
                                modifier = Modifier.size(30.dp, 20.dp),
                                onClick = { input.simulateKey(key) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("${i + 1}")
                            }
                            i++
                        }
                        Button(
                            modifier = Modifier.size(40.dp, 20.dp),
                            onClick = { sceneStack.pop() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("退出")
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 5. 渲染组件 Visual (用于显示实体)
// ==============================================================================

class CircleVisual(val color: Color) : Visual() {
    override fun DrawScope.draw() {
        drawCircle(color, radius = size.minDimension / 2f, alpha = alpha)
    }
}

class MovementSystem(
    val viewportTransform: ViewportTransform = inject()
) : IteratingSystem(
    family { all(Transform, RigidBody) }
) {

    val worldBounds = Rect(Offset.Zero, viewportTransform.virtualSize)

    override fun onTickEntity(entity: Entity) {
        val t = entity[Transform]
        val r = entity[RigidBody]

        /* 运动（复用基本类型） */
        t.position = t.position.copy(
            x = t.position.x + r.velocity.x * deltaTime,
            y = t.position.y + r.velocity.y * deltaTime
        )

        /* 边界反弹：一行调用你的封装（零临时对象） */
        val clamped = viewportTransform.clampInBounds(
            worldBounds = worldBounds,
            position = t.position
        )

        /* 若被 clamp → 反弹速度 */
        if (clamped.x != t.position.x) r.velocity = r.velocity.copy(x = -r.velocity.x)
        if (clamped.y != t.position.y) r.velocity = r.velocity.copy(y = -r.velocity.y)

        /* 写回最终位置（零临时对象） */
        t.position = clamped
    }
}

@Composable
fun ZeroGCCollisionDemo(context: PlatformContext) {
    KSimpleGame(
        context = context,
        resourceProvider = DefaultResourceProvider,
        modifier = Modifier.fillMaxSize(),
    ) {
       val anim = ScaleAnimation(
            from = 0f,
            to = 1f,
            spec = Spring(
                stiffness = 80f,
                damping   = 15f,
            )
        )

        world(configuration = {
            systems {
                +CollisionSystem()
                +MovementSystem()
                +AnimationSystem()
                +RenderSystem()
            }
        }) {
            val entityCount = 50
            val entitySize = Size(40f, 40f)

            val bounds = Rect(0f, 0f, 800f, 600f)

            entities(entityCount) {
                val velX = (-40f..40f).random()
                val velY = (-40f..40f).random()
                val mass = 1f + Random.nextFloat()
                val color = Color.random()

                // 创建随机移动和碰撞的实体
                it += Transform(bounds.randomOffset(), entitySize)
                it += RigidBody(Offset(velX, velY), mass = mass)
                it += ScaleAnimation(
                    from = 0f,
                    to = 1f,
                    spec = InfiniteRepeatable(
                        Tween(1f, easing = EaseOutOvershoot),
                        RepeatMode.Reverse
                    )
                )
                it += AlphaAnimation(
                    from = 0f,
                    to = 1f,
                    spec = InfiniteRepeatable(
                        Tween(2f, easing = LinearEasing),
                        RepeatMode.Reverse
                    )
                )
                it += Renderable(CircleVisual(color), zIndex = 1)
            }

            // 创建一个静态墙体来验证分离逻辑 (mass = 0f)
            entity {
                it += Transform(Offset(400f, 300f), Size(100f, 100f))
                it += RigidBody(Offset.Zero, mass = 0f)
                it += anim
                it += SpriteAnimation("run")
                it += Renderable(
                    visual = Sprite(assets[GameAssets.Atlas.Walk], "frame_0_0"),
                    zIndex = 1
                )
            }
        }

        resources {
            it += GameAssets.Atlas.Walk
        }

        val fpsCalculator = FpsCalculator()

        onRender { fpsCalculator.advanceFrame() }

        onForegroundUI {
            Text("FPS: ${fpsCalculator.fps}")

            Button(
                onClick = { anim.play() },
                modifier = Modifier.padding(top = 100.dp)
            ) {
                Text("Test")
            }
        }
    }
}
val EaseOutOvershoot = CubicBezierEasing(0.4f, 1.5f, 0.8f, 1.0f)

@Composable
fun SimpleGameDemo(context: PlatformContext) {
    KSimpleGame(
        context = context,
        resourceProvider = DefaultResourceProvider,
        modifier = Modifier.fillMaxSize(),
    ) {
        var color = Color.Red

        onUpdate {
            if (input.isKeyDown(Key.Spacebar)) {
                color = if (color == Color.Red) Color.Yellow else Color.Red
            }
        }

        onRender { drawCircle(color, radius = 50f) }

        onBackgroundUI { Rectangle(Color.Blue) }

        onForegroundUI {
            Button(
                onClick = {} ,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp)
            ) {
                Text(
                    text = "Hello World",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}