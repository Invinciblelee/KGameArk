package com.game.engine.ecs

import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.jvm.JvmName

abstract class System {
    private lateinit var _world: World
    val world: World get() = _world

    fun attach(world: World) {
        this._world = world
    }

    // 逻辑更新帧
    open fun update(deltaTime: Float) {}

    // 渲染帧
    open fun DrawScope.draw() {}
}

@JvmName("inject1")
inline fun <reified A : Component> System.inject(): Lazy<Family> {
    return lazy { world.getFamily(A::class) }
}

@JvmName("inject2")
inline fun <reified A : Component, reified B : Component> System.inject(): Lazy<Family> {
    return lazy { world.getFamily(A::class, B::class) }
}

@JvmName("inject3")
inline fun <reified A : Component, reified B : Component, reified C: Component> System.inject(): Lazy<Family> {
    return lazy { world.getFamily(A::class, B::class, C::class) }
}