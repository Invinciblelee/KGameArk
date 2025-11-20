package com.game.engine.ecs.components

import com.game.engine.ecs.Component

data class Tween(
    var current: Float,
    var target: Float,
    var speed: Float = 5f, // 变化速度
    val onComplete: (() -> Unit)? = null
) : Component