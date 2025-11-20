package com.game.engine.ecs.components

import com.game.engine.ecs.Component

// 标记该实体飞出屏幕会被销毁
data class Boundary(val padding: Float = 100f) : Component