package com.game.engine.ecs.components

import androidx.compose.ui.geometry.Offset
import com.game.engine.ecs.Component

// 基础变换 (位置/旋转)
data class Transform(
    var position: Offset = Offset.Zero,
    var rotation: Float = 0f,
    var scale: Float = 1f
) : Component

// 速度与阻力
data class Velocity(
    var vector: Offset = Offset.Zero, // 速度向量
    var drag: Float = 0f           // 空气阻力 (0~1，越大停得越快)
) : Component