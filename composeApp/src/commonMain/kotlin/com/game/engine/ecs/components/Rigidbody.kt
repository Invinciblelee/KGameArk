package com.game.engine.ecs.components

import androidx.compose.ui.geometry.Offset
import com.game.engine.ecs.Component
import com.game.engine.math.normalize
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
) : Component

// 行为组件：移动到目标 (类似 RTS 游戏的移动)
// 这是一个"意图"，它不直接改坐标，而是产生力
data class Movement(
    var target: Offset,
    var speed: Float = 200f,        // 巡航速度
    var stopDistance: Float = 1f,   // 到达半径
    var arriveEnabled: Boolean = true // 是否启用"抵达减速" (Arrival)
) : Component

// 行为组件：弹性吸附 (我们之前讨论的弹簧逻辑)
// 挂载这个组件，物体就会像被弹簧拉着一样动
data class SpringFollow(
    var target: Offset,
    var stiffness: Float = 300f,
    var damping: Float = 15f
) : Component

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
 * 计算一个垂直于给定线段的冲量，并将其应用到刚体上。
 * 极大地简化了 CollisionSystem 中的碰撞反馈逻辑。
 * @param p1 碰撞线段的起点
 * @param p2 碰撞线段的终点
 * @param magnitude 冲量的强度 (J)
 */
fun Rigidbody.addImpulsePerpendicularToSegment(
    p1: Offset,
    p2: Offset,
    magnitude: Float
) {
    // 几何计算：获取线段向量
    val segmentVector = p2 - p1

    // 法向量（垂直方向）并归一化
    val normalVector = Offset(-segmentVector.y, segmentVector.x).normalize()

    // 计算总冲量向量：J_vector = Normal * Magnitude
    val impulseVector = normalVector * magnitude

    // 委托给 Rigidbody 的核心物理方法：(J_vector / mass) -> velocity
    this.addImpulse(impulseVector)
}


/**
 * 将 Seek/Arrive 行为的转向力计算逻辑应用于刚体。
 * @param transform 当前实体的 Transform 组件
 * @param move 移动意图 Movement 组件
 */
fun Rigidbody.applyMovement(transform: Transform, move: Movement) {
    val diff = move.target - transform.position
    val dist = diff.getDistance()

    if (dist < move.stopDistance) {
        // 到达目标：清空速度，退出
        velocity = Offset.Zero
        acceleration = Offset.Zero
        return
    }

    // 1. 计算期望速度
    var desiredSpeed = move.speed

    // Arrive 行为：接近目标时减速
    if (move.arriveEnabled) {
        val slowDownRadius = 100f
        if (dist < slowDownRadius) {
            desiredSpeed = move.speed * (dist / slowDownRadius)
        }
    }

    // 2. 转向力 (Steering Force) = 期望速度 - 当前速度
    val desiredVel = diff.normalize() * desiredSpeed
    // 转向灵敏度 4f
    val steerForce = (desiredVel - velocity) * 4f

    this.addForce(steerForce)
}

/**
 * 将弹簧吸附行为的弹性力和阻尼力计算逻辑应用于刚体。
 * @param transform 当前实体的 Transform 组件
 * @param spring 弹簧跟随 SpringFollow 组件
 */
fun Rigidbody.applySpringFollow(transform: Transform, spring: SpringFollow) {
    val displacement = transform.position - spring.target
    // F_spring = -k * x
    val forceSpring = displacement * -spring.stiffness
    // F_damping = -c * v
    val forceDamping = velocity * -spring.damping

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