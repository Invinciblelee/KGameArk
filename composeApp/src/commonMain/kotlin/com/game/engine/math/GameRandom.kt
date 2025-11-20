package com.game.engine.math

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random

object GameRandom {
    
    /** 在指定范围内生成随机坐标 */
    fun offset(maxX: Float, maxY: Float): Offset {
        return Offset(
            Random.nextFloat() * maxX,
            Random.nextFloat() * maxY
        )
    }

    /** 在以 center 为中心，radius 为半径的圆内生成随机点 */
    fun insideCircle(center: Offset, radius: Float): Offset {
        val angle = Random.nextFloat() * 2 * PI
        val r = sqrt(Random.nextFloat()) * radius // sqrt 保证分布均匀
        return center + Offset(
            (r * kotlin.math.cos(angle)).toFloat(),
            (r * kotlin.math.sin(angle)).toFloat()
        )
    }
    
    /** 随机范围 Float */
    fun range(min: Float, max: Float): Float {
        return min + Random.nextFloat() * (max - min)
    }
}