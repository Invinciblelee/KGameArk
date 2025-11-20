package com.game.engine.math

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.atan2

// --- Float 扩展 ---

/** 线性插值: start + (end - start) * fraction */
fun Float.lerp(target: Float, fraction: Float): Float {
    return this + (target - this) * fraction.coerceIn(0f, 1f)
}

// --- Offset (Vector2) 扩展 ---

/** 向量线性插值 */
fun Offset.lerp(target: Offset, fraction: Float): Offset {
    return Offset(
        this.x.lerp(target.x, fraction),
        this.y.lerp(target.y, fraction)
    )
}

/** 计算当前点指向目标点的角度 (返回度数 0~360) */
fun Offset.angleTo(target: Offset): Float {
    val rad = atan2(target.y - this.y, target.x - this.x)
    return (rad * 180 / PI).toFloat()
}

/** 距离平方 (比 getDistance() 快，用于碰撞检测阈值对比) */
fun Offset.distSq(other: Offset): Float {
    val dx = this.x - other.x
    val dy = this.y - other.y
    return dx * dx + dy * dy
}

/** 归一化 (转为单位向量) */
fun Offset.normalize(): Offset {
    val len = getDistance()
    return if (len == 0f) Offset.Zero else this / len
}