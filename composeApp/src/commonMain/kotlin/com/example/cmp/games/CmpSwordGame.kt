//@file:OptIn(ExperimentalTime::class)
//
//package com.example.cmp.games
//
//import androidx.compose.foundation.Canvas
//import androidx.compose.foundation.background
//import androidx.compose.foundation.gestures.detectDragGestures
//import androidx.compose.foundation.gestures.detectTapGestures
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.BoxWithConstraints
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.padding
//import androidx.compose.material3.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clipToBounds
//import androidx.compose.ui.geometry.Offset
//import androidx.compose.ui.geometry.Size
//import androidx.compose.ui.graphics.*
//import androidx.compose.ui.graphics.drawscope.Stroke
//import androidx.compose.ui.graphics.drawscope.rotate
//import androidx.compose.ui.input.pointer.pointerInput
//import androidx.compose.ui.platform.LocalDensity
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import kotlinx.coroutines.isActive
//import kotlin.math.*
//import kotlin.random.Random
//import kotlin.time.ExperimentalTime
//
//// --- 游戏引擎 ---
//
//private class SilkGameEngine {
//    val enemies = ArrayList<Enemy>()
//    val swords = ArrayList<SilkSword>()
//    val particles = ArrayList<Particle>()
//
//    var playerPos = Offset(0f, 0f)
//    var score by mutableIntStateOf(0)
//
//    private val random = Random.Default
//
//    // [修改 1] 定义霓虹色板，用于随机怪物颜色
//    private val enemyPalette = listOf(
//        Color(0xFFEF5350), // 红
//        Color(0xFFAB47BC), // 紫
//        Color(0xFF42A5F5), // 蓝
//        Color(0xFF26C6DA), // 青
//        Color(0xFF66BB6A), // 绿
//        Color(0xFFFFCA28), // 黄
//        Color(0xFFFF7043)  // 橙
//    )
//
//    fun update(dt: Float, timeSec: Float, constraints: Size) {
//        // 1. 生成敌人
//        if (enemies.size < 60 && random.nextFloat() < 0.05f) {
//            spawnEnemy(constraints)
//        }
//
//        // 2. 自动发射
//        if (swords.size < 60 && random.nextFloat() < 0.3f) {
//            spawnSwordSilk()
//        }
//
//        // 3. 更新剑丝
//        val swordIter = swords.iterator()
//        while (swordIter.hasNext()) {
//            val sword = swordIter.next()
//            if (!sword.isDead) {
//                trackEnemy(sword, dt)
//
//                sword.position += sword.velocity * dt
//                sword.life -= dt
//
//                if (sword.life <= 0) sword.isDead = true
//                if (sword.position.x !in -200f..constraints.width + 200f ||
//                    sword.position.y !in -200f..constraints.height + 200f) {
//                    sword.isDead = true
//                }
//            }
//            if (sword.isDead) swordIter.remove()
//        }
//
//        // 4. 更新粒子
//        val pIter = particles.iterator()
//        while (pIter.hasNext()) {
//            val p = pIter.next()
//            p.position += p.velocity * dt
//            p.life -= dt
//            if (p.life <= 0) pIter.remove()
//        }
//
//        // 5. 碰撞检测
//        val enemyIter = enemies.iterator()
//        while (enemyIter.hasNext()) {
//            val enemy = enemyIter.next()
//
//            val diff = playerPos - enemy.position
//            val dist = diff.getDistance()
//            if (dist > 1f) enemy.position += (diff / dist) * enemy.speed * dt
//
//            for (i in swords.indices.reversed()) {
//                val sword = swords[i]
//                if (sword.isDead) continue
//
//                val dx = sword.position.x - enemy.position.x
//                val dy = sword.position.y - enemy.position.y
//                if (dx*dx + dy*dy < 1000) { // 碰撞范围稍作调整
//                    sword.isDead = true
//                    enemy.hp--
//                    // [修改 3] 击中时产生对应怪物颜色的火花
//                    spawnHitSpark(sword.position, enemy.color)
//
//                    if (enemy.hp <= 0) {
//                        enemy.isDead = true
//                        score++
//                        // 死亡时爆出更多同色粒子
//                        spawnHitSpark(enemy.position, enemy.color, 8)
//                        break
//                    }
//                }
//            }
//            if (enemy.isDead) enemyIter.remove()
//        }
//    }
//
//    private fun trackEnemy(sword: SilkSword, dt: Float) {
//        var target: Enemy? = null
//        var minDistSq = 1000000f
//        for (e in enemies) {
//            val dSq = (e.position.x - sword.position.x).pow(2) + (e.position.y - sword.position.y).pow(2)
//            if (dSq < minDistSq) {
//                minDistSq = dSq
//                target = e
//                if (dSq < 40000f) break
//            }
//        }
//
//        if (target != null) {
//            val desired = atan2(target.position.y - sword.position.y, target.position.x - sword.position.x)
//            var diff = desired - sword.angle
//            while (diff <= -PI) diff += 2*PI.toFloat()
//            while (diff > PI) diff -= 2*PI.toFloat()
//
//            sword.angle += diff * 4f * dt
//            val speed = sword.velocity.getDistance()
//            sword.velocity = Offset(cos(sword.angle)*speed, sin(sword.angle)*speed)
//        }
//    }
//
//    private fun spawnEnemy(area: Size) {
//        val edge = random.nextInt(4)
//        val pos = when(edge) {
//            0 -> Offset(random.nextFloat()*area.width, -50f)
//            1 -> Offset(random.nextFloat()*area.width, area.height+50f)
//            2 -> Offset(-50f, random.nextFloat()*area.height)
//            else -> Offset(area.width+50f, random.nextFloat()*area.height)
//        }
//        // [修改 4] 随机颜色 + 增加血量 (2 -> 5)
//        val randomColor = enemyPalette.random()
//        enemies.add(Enemy(pos, hp = 5, color = randomColor))
//    }
//
//    private fun spawnSwordSilk() {
//        val baseAngle = random.nextFloat() * 2 * PI.toFloat()
//        swords.add(SilkSword(
//            position = playerPos,
//            velocity = Offset(cos(baseAngle)*600f, sin(baseAngle)*600f),
//            angle = baseAngle,
//            phaseOffset = random.nextFloat() * 10f
//        ))
//    }
//
//    fun triggerUltimate() {
//        val count = 60 // 稍微减少数量以保持性能
//        for (i in 0 until count) {
//            val angle = (i.toFloat()/count) * 2 * PI.toFloat()
//            swords.add(SilkSword(
//                position = playerPos,
//                velocity = Offset(cos(angle)*900f, sin(angle)*900f),
//                angle = angle,
//                isUltimate = true,
//                life = 4f,
//                phaseOffset = i.toFloat()
//            ))
//        }
//    }
//
//    private fun spawnHitSpark(pos: Offset, color: Color, count: Int = 3) {
//        for(i in 0 until count) {
//            val angle = random.nextFloat() * 6.28f
//            val speed = random.nextFloat() * 150f
//            particles.add(Particle(pos, Offset(cos(angle)*speed, sin(angle)*speed), 0.4f, color, random.nextFloat()*3f + 2f))
//        }
//    }
//}
//
//// --- 数据结构 ---
//private data class Enemy(
//    var position: Offset,
//    var hp: Int = 5, // 默认血量提升
//    var isDead: Boolean = false,
//    val speed: Float = 80f,
//    val color: Color // 必须有颜色
//)
//
//private class SilkSword(
//    var position: Offset,
//    var velocity: Offset,
//    var angle: Float,
//    var life: Float = 2.0f,
//    val isUltimate: Boolean = false,
//    var isDead: Boolean = false,
//    val phaseOffset: Float
//)
//
//private data class Particle(
//    var position: Offset,
//    var velocity: Offset,
//    var life: Float,
//    val color: Color,
//    val size: Float
//)
//
//// --- 界面与绘制 ---
//
//@Composable
//fun CmpSwordGame(modifier: Modifier = Modifier) {
//    val engine = remember { SilkGameEngine() }
//    var frameState by remember { mutableStateOf(0f) }
//    var touchPos by remember { mutableStateOf(Offset.Zero) }
//    val sharedPath = remember { Path() }
//
//    val density = LocalDensity.current
//
//    BoxWithConstraints(
//        modifier = modifier
//            .background(Color(0xFF101018))
//    ) {
//        val constraintsSize = remember(maxWidth, maxHeight) {
//            with(density) {
//                Size(maxWidth.toPx(), maxHeight.toPx())
//            }
//        }
//
//        LaunchedEffect(Unit) {
//            var startTime = 0L
//            var lastTime = 0L
//
//            while (isActive) {
//                withFrameNanos { now ->
//                    if (startTime == 0L) startTime = now
//                    if (lastTime == 0L) lastTime = now
//
//                    val dt = ((now - lastTime) / 1_000_000_000f).coerceAtMost(0.05f)
//                    val timeSec = (now - startTime) / 1_000_000_000f
//                    lastTime = now
//
//                    if (touchPos != Offset.Zero) {
//                        val diff = touchPos - engine.playerPos
//                        engine.playerPos += diff * 8f * dt
//                    } else if (engine.playerPos == Offset.Zero) {
//                        engine.playerPos = Offset(500f, 800f)
//                    }
//
//                    engine.update(dt, timeSec, constraintsSize)
//                    frameState = timeSec
//                }
//            }
//        }
//
//        Canvas(
//            modifier = Modifier
//                .fillMaxSize()
//                .clipToBounds()
//                .pointerInput(Unit) {
//                    detectTapGestures(onDoubleTap = { engine.triggerUltimate() })
//                }
//                .pointerInput(Unit) {
//                    detectDragGestures(
//                        onDragStart = { touchPos = it },
//                        onDrag = { change, _ -> change.consume(); touchPos = change.position }
//                    )
//                }
//        ) {
//            val time = frameState
//
//            // 1. 绘制剑丝
//            engine.swords.forEach { sword ->
//                drawSilkThread(this, sharedPath, sword, time)
//            }
//
//            // 2. 绘制敌人 (使用随机颜色)
//            engine.enemies.forEach { e ->
//                // 实体中心
//                drawCircle(e.color, 15f, e.position)
//                // 光晕 (半透明，同色)
//                drawCircle(e.color.copy(alpha = 0.3f), 25f, e.position)
//                // 高光
//                drawCircle(Color.White.copy(alpha = 0.5f), 5f, e.position - Offset(5f, 5f))
//            }
//
//            // 3. 粒子
//            engine.particles.forEach { p ->
//                val alpha = (p.life / 0.4f).coerceIn(0f, 1f)
//                drawCircle(p.color.copy(alpha = alpha), p.size, p.position)
//            }
//
//            // 4. 玩家
//            drawCultivationPlayer(
//                drawScope = this,
//                center = engine.playerPos,
//                time = time // 传入时间用于驱动旋转和呼吸动画
//            )
//        }
//
//        // UI
//        Box(modifier = Modifier.fillMaxSize().padding(30.dp)) {
//            Text("击杀: ${engine.score}", color = Color.White.copy(0.7f), fontSize = 20.sp, fontWeight = FontWeight.Light,
//                modifier = Modifier.align(Alignment.BottomStart))
//            Text("万剑归宗 - 双击爆发", color = Color(0xFFE0F7FA), modifier = Modifier.align(Alignment.TopCenter))
//        }
//    }
//}
//
//// --- 核心绘图：保持原样 ---
//private fun drawSilkThread(drawScope: androidx.compose.ui.graphics.drawscope.DrawScope, path: Path, sword: SilkSword, time: Float) {
//    val head = sword.position
//    val length = if (sword.isUltimate) 120f else 80f
//
//    val dirX = cos(sword.angle)
//    val dirY = sin(sword.angle)
//    val tailX = head.x - dirX * length
//    val tailY = head.y - dirY * length
//
//    val midX = (head.x + tailX) / 2
//    val midY = (head.y + tailY) / 2
//
//    val normX = -dirY
//    val normY = dirX
//
//    val wave = sin(time * 15f + sword.phaseOffset)
//    val amplitude = if (sword.isUltimate) 15f else 8f
//
//    val controlX = midX + normX * wave * amplitude
//    val controlY = midY + normY * wave * amplitude
//
//    path.reset()
//    path.moveTo(tailX, tailY)
//    path.quadraticTo(controlX, controlY, head.x, head.y)
//
//    val colorPrimary = if (sword.isUltimate) Color(0xFFFFD54F) else Color(0xFF40C4FF)
//    val brush = Brush.linearGradient(
//        0.0f to Color.Transparent,
//        0.5f to colorPrimary.copy(alpha = 0.5f),
//        1.0f to Color.White,
//        start = Offset(tailX, tailY),
//        end = Offset(head.x, head.y)
//    )
//
//    drawScope.drawPath(
//        path = path,
//        brush = brush,
//        style = Stroke(
//            width = if (sword.isUltimate) 4f else 2.5f,
//            cap = StrokeCap.Round,
//            join = StrokeJoin.Round
//        )
//    )
//}
//
//// --- 酷炫玩家绘制函数 ---
//private fun drawCultivationPlayer(
//    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
//    center: Offset,
//    time: Float
//) {
//    val baseColor = Color(0xFF40C4FF) // 核心青色
//    val secondaryColor = Color(0xFFE0F7FA) // 辅助亮白
//
//    // 1. 呼吸动画参数 (0.9 ~ 1.1 倍大小)
//    val breathingScale = 1f + sin(time * 3f) * 0.05f
//
//    // 2. 绘制最底层的光晕 (大范围柔光)
//    drawScope.drawCircle(
//        brush = Brush.radialGradient(
//            colors = listOf(baseColor.copy(alpha = 0.3f), Color.Transparent),
//            center = center,
//            radius = 80f * breathingScale
//        ),
//        radius = 80f * breathingScale,
//        center = center
//    )
//
//    // 3. 绘制外层护盾 (虚线圆环，顺时针旋转)
//    // 使用 rotate 旋转画布
//    drawScope.rotate(degrees = time * 30f, pivot = center) {
//        drawScope.drawCircle(
//            color = baseColor.copy(alpha = 0.6f),
//            radius = 55f,
//            center = center,
//            style = Stroke(
//                width = 3f,
//                pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 20f), 0f), // 虚线效果：30实，20空
//                cap = StrokeCap.Round
//            )
//        )
//        // 在虚线圆环上加两个装饰点
//        drawScope.drawCircle(Color.White, 4f, center + Offset(0f, -55f))
//        drawScope.drawCircle(Color.White, 4f, center + Offset(0f, 55f))
//    }
//
//    // 4. 绘制内层聚灵阵 (三角形，逆时针旋转，速度较快)
//    drawScope.rotate(degrees = -time * 90f, pivot = center) {
//        val trianglePath = Path().apply {
//            val r = 35f
//            // 计算等边三角形顶点
//            moveTo(center.x, center.y - r) // 顶点
//            lineTo(center.x + r * 0.866f, center.y + r * 0.5f) // 右下
//            lineTo(center.x - r * 0.866f, center.y + r * 0.5f) // 左下
//            close()
//        }
//
//        drawScope.drawPath(
//            path = trianglePath,
//            color = secondaryColor.copy(alpha = 0.4f), // 半透明填充
//        )
//        drawScope.drawPath(
//            path = trianglePath,
//            color = baseColor, // 实线边框
//            style = Stroke(width = 2f)
//        )
//    }
//
//    // 5. 核心丹田 (高亮实体)
//    drawScope.drawCircle(
//        color = Color.White,
//        radius = 12f,
//        center = center
//    )
//    // 核心发光晕
//    drawScope.drawCircle(
//        color = baseColor.copy(alpha = 0.8f),
//        radius = 16f,
//        center = center,
//        style = Stroke(width = 2f)
//    )
//}