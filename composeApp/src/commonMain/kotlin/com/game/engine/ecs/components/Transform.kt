package com.game.engine.ecs.components

import androidx.compose.ui.geometry.Offset
import com.game.engine.ecs.Component

// 1. 基础状态 (保持不变)
data class Transform(
    var position: Offset = Offset.Zero,
    var rotation: Float = 0f,
    var scale: Float = 1f
) : Component

// 2. 刚体动力学 (负责惯性、阻力、受力)
// 只有挂载了这个，物体才有“物理实体感”
data class Rigidbody(
    var velocity: Offset = Offset.Zero,     // 当前速度
    var acceleration: Offset = Offset.Zero, // 当前加速度
    var mass: Float = 1f,                   // 质量 (影响受力 F=ma)
    var drag: Float = 2.0f,                 // 线性阻力 (每秒衰减比例)
    var maxSpeed: Float = 1000f             // 终端速度限制
) : Component {
    // 辅助方法：施加力 (F -> a)
    fun addForce(force: Offset) {
        acceleration += force / mass
    }

    // 辅助方法：施加冲量 (直接改变速度)
    fun addImpulse(impulse: Offset) {
        velocity += impulse / mass
    }
}

// 3. 行为组件：移动到目标 (类似 RTS 游戏的移动)
// 这是一个"意图"，它不直接改坐标，而是产生力
data class MoveToTarget(
    var target: Offset,
    var speed: Float = 200f,        // 巡航速度
    var stopDistance: Float = 1f,   // 到达半径
    var arriveEnabled: Boolean = true // 是否启用"抵达减速" (Arrival)
) : Component

// 4. 行为组件：弹性吸附 (我们之前讨论的弹簧逻辑)
// 挂载这个组件，物体就会像被弹簧拉着一样动
data class SpringFollow(
    var target: Offset,
    var stiffness: Float = 300f,
    var damping: Float = 15f
) : Component