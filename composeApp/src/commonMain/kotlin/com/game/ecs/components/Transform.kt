package com.game.ecs.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import com.game.ecs.Component
import com.game.ecs.ComponentType
import kotlin.math.sqrt

// 基础状态
data class Transform(
    var position: Offset = Offset.Zero,
    var rotation: Float = 0f,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f
) : Component<Transform> {
    override fun type() = Transform
    companion object : ComponentType<Transform>()
}

/**
 * 封装阻尼弹簧振子 (DHO) 逻辑，用于平滑地移动 Transform 到目标位置。
 * 该方法会更新 Transform 的位置，并直接更新 SpringEffect 内部的 smoothVelocity 状态。
 * @param deltaTime 时间步长
 * @param effect SpringEffect 配置参数 (k 和 c, 及其速度状态)
 * @param targetPosition 目标的世界坐标
 * @param mass 物体质量
 */
fun Transform.applySpringFollow(
    deltaTime: Float,
    effect: SpringEffect,
    targetPosition: Offset,
    mass: Float = 1f
) {
    // 1. 读取当前状态
    var currentVelocity = effect.velocity
    val currentPosition = this.position

    val displacement = currentPosition - targetPosition

    // 2. 计算力: F = -k*x - c*v
    val forceSpring = displacement * -effect.stiffness
    val forceDamping = currentVelocity * -effect.damping
    val totalForce = forceSpring + forceDamping

    // 3. 欧拉积分更新
    val acceleration = totalForce / mass

    // Velocity (v = v + a*dt)
    currentVelocity += acceleration * deltaTime
    effect.velocity = currentVelocity

    // Position (x = x + v*dt)
    val newX = currentPosition.x + currentVelocity.x * deltaTime
    val newY = currentPosition.y + currentVelocity.y * deltaTime

    this.position = Offset(newX, newY)
}

/**
 * 封装 Lerp 逻辑，用于 Transform 基于时间和平滑因子移动到目标位置。
 * @param deltaTime 时间步长
 * @param targetPosition 目标的世界坐标
 * @param lerpSpeed 决定 Lerp 因子的速度乘数
 */
fun Transform.applyLerpFollow(
    deltaTime: Float,
    targetPosition: Offset,
    lerpSpeed: Float
) {
    // 计算 t = (speed * dt)，并限制在 [0, 1]
    val t = (lerpSpeed * deltaTime).coerceIn(0f, 1f)

    // 应用 Lerp 插值
    val newX = lerp(this.position.x, targetPosition.x, t)
    val newY = lerp(this.position.y, targetPosition.y, t)

    // 更新 Transform 的位置
    this.position = Offset(newX, newY)
}

/**
 * 根据原始输入方向 (delta) 和速度，计算并更新 Transform 的位置。
 * 包含归一化处理，确保斜向移动不会更快。
 * @param deltaTime 时间步长 (dt)
 * @param rawDeltaX X轴原始方向输入 (-1f, 0f, 1f)
 * @param rawDeltaY Y轴原始方向输入 (-1f, 0f, 1f)
 * @param speed 移动速度 (例如 20f)
 */
fun Transform.applyMovement(
    deltaTime: Float,
    rawDeltaX: Float,
    rawDeltaY: Float,
    speed: Float
) {
    // 1. 获取原始的位移向量
    var deltaX = rawDeltaX
    var deltaY = rawDeltaY

    // 2. 归一化对角线移动
    val length = sqrt(deltaX * deltaX + deltaY * deltaY)
    if (length > 0) {
        deltaX /= length
        deltaY /= length
    }

    // 3. 应用速度和时间 (dt)
    val movementX = deltaX * speed * deltaTime
    val movementY = deltaY * speed * deltaTime

    // 4. 更新 Transform 组件的位置
    this.position = Offset(
        x = this.position.x + movementX,
        y = this.position.y + movementY
    )
}