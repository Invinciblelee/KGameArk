package com.game.engine.ecs

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import com.game.engine.ecs.components.Animator
import kotlin.reflect.KClass

class Entity(val id: Int) {
    // 存储组件的 Map
    val components = mutableMapOf<KClass<out Component>, Component>()

    // 链式添加组件
    inline fun <reified T : Component> with(component: T): Entity {
        components[T::class] = component
        return this
    }

    // 获取组件
    inline fun <reified T : Component> get(): T {
        return components[T::class] as T
    }

    inline fun <reified T : Component> Entity.getOrNull(): T? {
        return components[T::class] as? T
    }

    inline fun <reified T : Component> Entity.getOrPut(factory: () -> T): T {
        return getOrNull<T>() ?: run {
            val newComp = factory()
            with(newComp)
            newComp
        }
    }

    inline fun <reified T : Component> has(): Boolean {
        return components.containsKey(T::class)
    }
}

// --- 核心魔法 ---
fun Entity.animate(
    target: Offset,
    dt: Float,
    label: String,
    spec: AnimationSpec<Offset> = spring()
): Offset {
    val animComp = this.getOrPut { Animator() }
    return animComp.animate(target, dt, label, spec)
}

fun Entity.animate(
    target: Float,
    dt: Float,
    label: String,
    spec: AnimationSpec<Float> = spring()
): Float {
    val animComp = this.getOrPut { Animator() }
    return animComp.animate(target, dt, label, spec)
}