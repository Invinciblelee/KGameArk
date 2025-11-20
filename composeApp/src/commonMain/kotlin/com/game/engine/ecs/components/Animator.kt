package com.game.engine.ecs.components

import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import com.game.engine.ecs.Component

// 1. 定义一个基类，专门用于处理带动画的组件
class Animator : Component {

    // 缓存：Key = Label, Value = 动画对象
    private val animatables = HashMap<String, GameAnimatable<*, *>>()

    // --- 核心 API：根据 Label 获取并更新动画 ---

    // 1. Float 版本
    @Suppress("UNCHECKED_CAST")
    fun animate(
        target: Float,
        dt: Float,
        label: String,
        spec: AnimationSpec<Float> = spring(),
    ): Float {
        // 查找或创建
        val anim = animatables.getOrPut(label) {
            GameAnimatable(target, Float.VectorConverter)
        } as GameAnimatable<Float, *>

        // 更新并返回
        return anim.update(target, spec, dt)
    }

    // 2. Offset 版本
    @Suppress("UNCHECKED_CAST")
    fun animate(
        target: Offset,
        dt: Float,
        label: String,
        spec: AnimationSpec<Offset> = spring(),
    ): Offset {
        val anim = animatables.getOrPut(label) {
            GameAnimatable(target, Offset.VectorConverter)
        } as GameAnimatable<Offset, *>

        return anim.update(target, spec, dt)
    }

}

// 2. 核心动画代理类
// 它的工作原理：你 set 它的值 = 设置目标值；你 get 它的值 = 获取当前帧的动画值
class GameAnimatable<T, V : AnimationVector>(
    initialValue: T,
    private val typeConverter: TwoWayConverter<T, V>,
) {
    // 当前动画产生的渲染值
    var value: T = initialValue
        private set

    // 记录上一次的目标，用于检测变化
    private var currentTarget: T = initialValue

    // 内部动画核心
    private var anim: TargetBasedAnimation<T, V>? = null
    private var playTimeNanos: Long = 0L
    private var startValue: T = initialValue

    // 速度向量 (用于丝滑打断)
    private var velocityVector: V? = null

    fun update(targetValue: T, spec: AnimationSpec<T>, dt: Float): T {
        // 1. 检测目标是否变化 (Retargeting)
        if (targetValue != currentTarget) {
            // 创建新动画：起点是【当前动画值得】，终点是【新目标】
            // 关键：继承之前的速度，实现弹性惯性
            anim = TargetBasedAnimation(
                animationSpec = spec,
                typeConverter = typeConverter,
                initialValue = value, // 从当前位置开始
                targetValue = targetValue,
                initialVelocityVector = velocityVector // 继承速度
            )
            startValue = value
            currentTarget = targetValue
            playTimeNanos = 0L
        }

        val currentAnim = anim ?: return targetValue

        // 2. 推进时间
        playTimeNanos += (dt * 1_000_000_000).toLong()

        // 3. 计算数值
        value = currentAnim.getValueFromNanos(playTimeNanos)
        velocityVector = currentAnim.getVelocityVectorFromNanos(playTimeNanos)

        // 4. 检查结束
        if (currentAnim.isFinishedFromNanos(playTimeNanos)) {
            value = targetValue // 修正误差
            anim = null // 动画结束，清理
            velocityVector = null
        }

        return value
    }
}