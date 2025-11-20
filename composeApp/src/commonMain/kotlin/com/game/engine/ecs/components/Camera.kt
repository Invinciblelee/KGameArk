package com.game.engine.ecs.components

import androidx.compose.ui.geometry.Rect
import com.game.engine.ecs.Component

// 只有挂了这个组件的 Entity 才是摄像机
data class Camera(
    val name: String,
    var zoom: Float = 1f,
    val viewport: Rect = Rect(0f, 0f, 1f, 1f),
    var isActive: Boolean = true // 支持多机位切换
) : Component