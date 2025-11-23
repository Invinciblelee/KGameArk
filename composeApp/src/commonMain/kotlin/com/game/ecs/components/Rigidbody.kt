package com.game.ecs.components

import androidx.compose.ui.geometry.Offset
import com.game.engine.math.normalize
import com.game.ecs.Component
import com.game.ecs.ComponentType
import kotlin.math.exp

// 刚体动力学 (负责惯性、阻力、受力)
// 只有挂载了这个，物体才有“物理实体感”
data class Rigidbody(
    var velocity: Offset = Offset.Zero,     // 当前速度
    var acceleration: Offset = Offset.Zero, // 当前加速度
    var mass: Float = 1f,                   // 质量 (影响受力 F=ma)
    var drag: Float = 2.0f,                 // 线性阻力 (每秒衰减比例)
    var maxSpeed: Float = 1000f,            // 终端速度限制

    var angularVelocity: Float = 0f,    // ⚡️ 角速度（rad/s）。物体当前每秒旋转的弧度/角度。
    var angularAcceleration: Float = 0f, // ⚡️ 角加速度（rad/s²）。角速度的变化率，由扭矩（Torque）产生。
    var angularDrag: Float = 5.0f,     // 旋转阻力/角阻尼系数。用于模拟摩擦，使旋转随时间平滑衰减。
    var inertia: Float = 1f            // ⚡️ 转动惯量（Moment of Inertia, I）。旋转运动中“质量”的对应物。I 越大，物体对扭矩的抵抗力越大，越难被旋转。
) : Component<Rigidbody> {
    override fun type() = Rigidbody
    companion object: ComponentType<Rigidbody>()
}

data class MovementEffect(
    var speed: Float = 200f,
    var stopDistance: Float = 1f,
    var arriveEnabled: Boolean = true
) : Component<MovementEffect> {
    override fun type() = MovementEffect
    companion object : ComponentType<MovementEffect>()
}

data class SpringEffect(
    var stiffness: Float = 300f,
    var damping: Float = 15f,
    var velocity: Offset = Offset.Zero
) : Component<SpringEffect> {
    override fun type() = SpringEffect
    companion object : ComponentType<SpringEffect>()
}

/**
 * 施加一个持续的力 (Force)。
 * F = ma => a = F/m。力会累加到 acceleration 上。
 */
fun Rigidbody.addForce(force: Offset) {
    this.acceleration += force / this.mass
}

/**
 * 施加一个瞬时的冲量 (Impulse)。
 * J = mΔv => Δv = J/m。冲量直接改变 velocity。
 */
fun Rigidbody.addImpulse(impulse: Offset) {
    this.velocity += impulse / this.mass
}

/**
 * 施加扭矩 (Torque)。
 * T = Iα => α = T/I。扭矩会累加到角加速度上。
 */
fun Rigidbody.addTorque(torque: Float) {
    this.angularAcceleration += torque / this.inertia
}

/**
 * 施加偏心力 (Force at Position)。
 * 产生线加速度和角加速度。
 * @param force 施加的力 (F)
 * @param point 力的作用点 (世界坐标)
 * @param center 刚体的质心 (Transform.position)
 */
fun Rigidbody.addForceAtPosition(force: Offset, point: Offset, center: Offset) {
    // 1. 线性力 F=ma
    this.addForce(force)

    // 2. 力矩 T = r x F (叉乘)
    val r = point - center
    val torque = r.x * force.y - r.y * force.x
    this.addTorque(torque)
}

/**
 * 根据与线段的碰撞信息，向刚体施加一个法向冲量（Impulse）。
 *
 * 该方法用于处理圆形（刚体）与线段（如剑丝）之间的碰撞反应。
 * 它根据刚体的位置计算出将刚体推离线段的精确法线方向，并施加冲量。
 *
 * @param segmentStart 线段的起点坐标 P1。
 * @param segmentEnd 线段的终点坐标 P2。
 * @param center 发生碰撞的实体的中心位置 C (即圆形刚体的圆心)。
 * @param magnitude 施加冲量的大小（标量）。
 */
fun Rigidbody.applyImpulseFromSegment(
    segmentStart: Offset,
    segmentEnd: Offset,
    center: Offset,
    magnitude: Float
) {
    // 1. 计算线段上最近点的投影系数 t (0.0 到 1.0)
    val segmentVector = segmentEnd - segmentStart
    val centerToStart = center - segmentStart
    val segmentLengthSq = segmentVector.getDistanceSquared()

    // 避免除以零
    if (segmentLengthSq == 0f) return

    var t = (centerToStart.x * segmentVector.x + centerToStart.y * segmentVector.y) / segmentLengthSq
    t = t.coerceIn(0f, 1f)

    // 2. 计算线段上的最近点 P_proj
    val closestPoint = segmentStart + segmentVector * t

    // 3. 计算碰撞法线 N (从 P_proj 指向圆心 entityCenter)
    val collisionNormal = (center - closestPoint).normalize()

    // 4. 施加冲量 (I = magnitude * N)
    val impulse = collisionNormal * magnitude

    // 实际的物理更新（简化版）
    this.addImpulse(impulse)
}

/**
 * 计算并施加转向力 到 Rigidbody。
 * @param transform 当前实体的 Transform 组件
 * @param effect MovementEffect 配置参数
 * @param targetPosition 目标的世界坐标
 */
fun Rigidbody.applyMovementForce(
    transform: Transform,
    effect: MovementEffect,
    targetPosition: Offset
) {
    val diff = targetPosition - transform.position
    val dist = diff.getDistance()

    if (dist < effect.stopDistance) {
        this.velocity = Offset.Zero
        this.acceleration = Offset.Zero
        return
    }

    // 1. 计算期望速度
    var desiredSpeed = effect.speed

    // Arrive 行为：接近目标时减速
    if (effect.arriveEnabled) {
        val slowDownRadius = 100f
        if (dist < slowDownRadius) {
            desiredSpeed = effect.speed * (dist / slowDownRadius)
        }
    }

    // 2. 转向力 (Steering Force) = 期望速度 - 当前速度
    val desiredVel = diff.normalize() * desiredSpeed
    val steerForce = (desiredVel - this.velocity) * 4f // 转向灵敏度 4f

    this.addForce(steerForce)
}

/**
 * 计算并施加弹簧力（弹性吸附行为）到 Rigidbody。
 * @param transform 当前实体的 Transform 组件
 * @param effect SpringEffect 配置参数
 * @param targetPosition 弹簧的锚点（目标的世界坐标）
 */
fun Rigidbody.applySpringForce(
    transform: Transform,
    effect: SpringEffect,
    targetPosition: Offset
) {
    val displacement = transform.position - targetPosition // 实体位置与锚点位置的位移

    // F_spring = -k * x
    val forceSpring = displacement * -effect.stiffness

    // F_damping = -c * v
    val forceDamping = this.velocity * -effect.damping

    this.addForce(forceSpring + forceDamping)
}

/**
 * 物理积分：将 Rigidbody 的速度和加速度状态集成到 Transform 上。
 * 这个函数将完全取代 PhysicsSystem 的核心循环逻辑。
 * @param transform 当前实体的 Transform 组件
 * @param deltaTime 时间间隔
 */
fun Rigidbody.integrate(transform: Transform, deltaTime: Float) {
    // --- 线性物理积分 ---

    // 1. 应用线性阻力 (Drag)
    // v = v * e^(-drag * dt)
    val dragFactor = exp(-this.drag * deltaTime)
    this.velocity *= dragFactor

    // 2. 速度更新 (v += a * dt)
    this.velocity += this.acceleration * deltaTime

    // 3. 限制最大速度 (安全网)
    val maxSpeedSq = this.maxSpeed * this.maxSpeed
    if (this.velocity.getDistanceSquared() > maxSpeedSq) {
        // 使用 getDistance() 避免重复计算平方根
        this.velocity = this.velocity.normalize() * this.maxSpeed
    }

    // 4. 位置更新 (p += v * dt)
    transform.position += this.velocity * deltaTime

    // --- 角物理积分 (Rotation) ---

    // 5. 应用角阻力 (Angular Drag)
    val angularDragFactor = exp(-this.angularDrag * deltaTime)
    this.angularVelocity *= angularDragFactor

    // 6. 旋转更新 (rotation += angularVelocity * dt)
    transform.rotation += this.angularVelocity * deltaTime

    // --- 清理 ---

    // 7. 重置加速度 (准备接收下一帧的力)
    this.acceleration = Offset.Zero
}