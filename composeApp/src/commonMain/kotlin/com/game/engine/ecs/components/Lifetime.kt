package com.game.engine.ecs.components

import com.game.engine.ecs.Component

// 寿命组件
data class Lifetime(
    var duration: Float, // 总寿命 (秒)
    var age: Float = 0f  // 当前年龄
) : Component