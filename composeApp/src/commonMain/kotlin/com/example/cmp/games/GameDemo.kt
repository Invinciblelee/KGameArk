package com.example.cmp.games

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import com.game.engine.dsl.KGame
import com.game.engine.ecs.Component
import com.game.engine.ecs.System
import com.game.engine.ecs.components.Transform
import com.game.engine.ecs.getOrNull
import com.game.engine.ecs.each
import com.game.engine.ecs.firstEntityOrNull
import com.game.engine.ecs.getPair
import com.game.engine.input.KeyCodes
import kotlin.math.hypot
import kotlin.random.Random


// ==========================================
// PART 2: 业务逻辑层 (剑气朝元 Demo)
// ==========================================

// --- 组件 ---
enum class WuXing(val color: Color) {
    Metal(Color(0xFFFFD700)), Wood(Color(0xFF69F0AE)), Water(Color(0xFF40C4FF)), Fire(Color(0xFFFF5252)), Earth(Color(0xFF8D6E63))
}
class PlayerTag : Component
class EnemyTag(val radius: Float = 15f, var isTrapped: Boolean = false) : Component

data class SilkNode(var x: Float, var y: Float, var oldX: Float = x, var oldY: Float = y, var pinned: Boolean = false)
data class SilkComponent(var type: WuXing = WuXing.Water, val nodes: ArrayList<SilkNode> = ArrayList()) : Component {
    init { repeat(40) { nodes.add(SilkNode(0f, 0f)) } }
}

// --- 系统: 物理 ---
class SilkPhysicsSystem : System() {
    override fun update(dt: Float) {
        val player = world.firstEntityOrNull<PlayerTag>() ?: return
        val root = player.get<Transform>().position
        val target = if (world.input.mousePosition == Offset.Zero) root + Offset(100f, 0f) else world.input.mousePosition

        world.each<SilkComponent> { _, silk ->
            updateVerlet(silk, root, target)
        }
    }

    private fun updateVerlet(silk: SilkComponent, root: Offset, tip: Offset) {
        val nodes = silk.nodes
        if (nodes.isEmpty()) return
        val (stiffness, drag) = when (silk.type) {
            WuXing.Metal -> 40 to 0.65f
            WuXing.Water -> 4 to 0.94f
            WuXing.Fire -> 8 to 0.80f
            WuXing.Earth -> 30 to 0.70f
            else -> 15 to 0.85f
        }
        nodes.first().apply { x = root.x; y = root.y; pinned = true }
        nodes.last().apply { x = tip.x; y = tip.y; pinned = true }

        for (i in 1 until nodes.size - 1) {
            val p = nodes[i]
            val vx = (p.x - p.oldX) * drag
            val vy = (p.y - p.oldY) * drag
            p.oldX = p.x; p.oldY = p.y
            p.x += vx; p.y += vy
            if (silk.type == WuXing.Fire) {
                p.x += (Random.nextFloat() - 0.5f) * 4f; p.y += (Random.nextFloat() - 0.5f) * 4f
            }
        }
        repeat(stiffness) {
            for (i in 0 until nodes.size - 1) {
                val p1 = nodes[i]; val p2 = nodes[i+1]
                val dx = p2.x - p1.x; val dy = p2.y - p1.y
                val dist = hypot(dx, dy); val diff = 10f - dist
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

// --- 系统: 绞杀 ---
class CollisionSystem : System() {

    override fun update(dt: Float) {
        // 1. 获取剑丝数据
        val silk = world.getOrNull<SilkComponent>() ?: return
        val nodes = silk.nodes
        if (nodes.isEmpty()) return

        // 2. 计算是否闭环 (用于绞杀)
        val head = nodes.first()
        val tail = nodes.last()
        val isClosed = (head.x - tail.x) * (head.x - tail.x) +
                (head.y - tail.y) * (head.y - tail.y) < 200 * 200

        // 3. 遍历所有敌人
        world.each<EnemyTag, Transform> { _, enemy, transform ->

            val enemyPos = transform.position

            // --- 逻辑 A: 绞杀判定 (只改变状态，不变位置) ---
            enemy.isTrapped = if (isClosed) isPointInPolygon(enemyPos, nodes) else false

            // --- 逻辑 B: 物理切割/碰撞 (这是你丢失的让球动起来的逻辑) ---
            // 只有没被绞杀的时候才算撞击，或者两者并存，看你设计
            if (!enemy.isTrapped) {
                // 遍历剑丝的每一段线段
                for (i in 0 until nodes.size - 1) {
                    val p1 = nodes[i]
                    val p2 = nodes[i+1]

                    // 计算圆心到线段的距离平方
                    val distSq = distToSegmentSquared(
                        enemyPos,
                        Offset(p1.x, p1.y),
                        Offset(p2.x, p2.y)
                    )

                    // 判定阈值：(敌人半径 + 剑丝粗细)^2
                    val hitRadius = enemy.radius + 5f
                    if (distSq < hitRadius * hitRadius) {
                        // 💥 撞到了！产生物理反馈 (击退)

                        // 简单的随机击退效果
                        val knockbackX =  20f
                        val knockbackY = 20f

                        // 修改位置
                        transform.position += Offset(knockbackX, knockbackY)

                        // 甚至可以减扣血量
                        // enemy.hp -= 10

                        break // 一帧只撞一次，避免鬼畜
                    }
                }
            }
        }
    }

    private fun isPointInPolygon(p: Offset, nodes: List<SilkNode>): Boolean {
        var inside = false
        var j = nodes.size - 1
        for (i in nodes.indices) {
            if ((nodes[i].y > p.y) != (nodes[j].y > p.y) &&
                (p.x < (nodes[j].x - nodes[i].x) * (p.y - nodes[i].y) / (nodes[j].y - nodes[i].y) + nodes[i].x)) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

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
// --- 系统: 渲染 ---
class RenderSystem : System() {
    override fun draw(drawScope: DrawScope) {
        // 怪物
        world.each<EnemyTag, Transform> { _, e, t ->
            drawScope.drawCircle(if(e.isTrapped) Color.Red else Color.Gray, e.radius, t.position)
        }

        // 剑丝
        world.each<SilkComponent> { _, silk ->
            val color = silk.type.color
            val path = Path()
            if (silk.nodes.isNotEmpty()) {
                path.moveTo(silk.nodes[0].x, silk.nodes[0].y)
                for (i in 0 until silk.nodes.size - 1) {
                    val p1 = silk.nodes[i]; val p2 = silk.nodes[i+1]
                    path.quadraticTo(p1.x, p1.y, (p1.x + p2.x) / 2, (p1.y + p2.y) / 2)
                }
                path.lineTo(silk.nodes.last().x, silk.nodes.last().y)
            }
            with(drawScope) {
                drawPath(path, color.copy(0.2f), style = Stroke(20f, cap = StrokeCap.Round))
                drawPath(path, color, style = Stroke(5f, cap = StrokeCap.Round))
                drawPath(path, Color.White, style = Stroke(2f, cap = StrokeCap.Round))
            }
        }

        // 主角
        val (_, t) = world.getPair<PlayerTag, Transform>()
        drawScope.drawCircle(Color.White, 10f, t.position)
    }
}

// --- 系统: 控制 ---
class SilkControlSystem : System() {

    override fun update(dt: Float) {
        // 1. 获取输入 (System 已经能访问 world.engine.input)
        // 注意：这里假设 input 已经在 GameScope 中可用
        val input = world.input

        // 2. 找到剑丝组件 (通常玩家只有一个剑丝，或者遍历所有)
        world.each<SilkComponent> { _, silk ->
            if (input.isKeyDown(KeyCodes.One)) silk.type = WuXing.Metal
            if (input.isKeyDown(KeyCodes.Two)) silk.type = WuXing.Wood
            if (input.isKeyDown(KeyCodes.Three)) silk.type = WuXing.Water
            if (input.isKeyDown(KeyCodes.Four)) silk.type = WuXing.Fire
            if (input.isKeyDown(KeyCodes.Five)) silk.type = WuXing.Earth
        }
    }
}

// ==========================================
// PART 3: 入口 Main
// ==========================================

@Composable
fun GameDemo() {
    KGame(initialScene = "menu") {
        val textStyle = TextStyle(
            color = Color.White
        )
        // --- 场景 1: 菜单 ---
        scene("menu") {
            onRender {
                drawText(
                    textMeasurer = textMeasurer,
                    text = "=== 剑 气 朝 元 ===",
                    topLeft = Offset(300f, 300f),
                    style = textStyle
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "按 [SPACE] 开始修仙",
                    topLeft = Offset(310f, 350f),
                    style = textStyle
                )
            }
            onUpdate { dt ->
                if (input.isKeyDown(Key.Spacebar)) {
                    switchScene("battle")
                }
            }
        }

        // --- 场景 2: 战斗 ---
        scene("battle") {
            // ECS 配置
           world {
                install(SilkPhysicsSystem())
                install(CollisionSystem())
                install(RenderSystem())
                install(SilkControlSystem())

                entity { with(Transform(Offset(400f, 300f))); with(PlayerTag()) }
                entity { with(SilkComponent(WuXing.Water)) }
                repeat(50) {
                    entity {
                        with(Transform(Offset(Random.nextFloat() * 800, Random.nextFloat() * 600)))
                        with(EnemyTag())
                    }
                }
            }

            onRender {
                drawText(
                    textMeasurer = textMeasurer,
                    text = "Battle Mode | FPS: $fps",
                    topLeft = Offset(20f, 40f),
                    style = textStyle
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "[1-5] Switch Element  [ESC] Menu",
                    topLeft = Offset(20f, 70f),
                    style = textStyle
                )
            }

            onUpdate { dt ->
                if (input.isKeyDown(Key.Escape)) {
                    switchScene("menu")
                }
            }
        }
    }
}