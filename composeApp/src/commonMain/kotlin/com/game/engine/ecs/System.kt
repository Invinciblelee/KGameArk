package com.game.engine.ecs

import androidx.compose.ui.graphics.drawscope.DrawScope

abstract class System {
    private lateinit var _world: World
    val world: World get() = _world

    fun attach(world: World) {
        this._world = world
    }

    // 逻辑更新帧
    open fun update(dt: Float) {}

    // 渲染帧
    open fun draw(drawScope: DrawScope) {}
}

inline fun <reified A : Component> System.family(): Lazy<Family> {
    return lazy(LazyThreadSafetyMode.NONE) {
        world.getFamily(A::class)
    }
}

inline fun <reified A : Component, reified B : Component> System.family(): Lazy<Family> {
    return lazy(LazyThreadSafetyMode.NONE) {
        world.getFamily(A::class, B::class)
    }
}

inline fun <reified A : Component, reified B : Component, reified C: Component> System.family(): Lazy<Family> {
    return lazy(LazyThreadSafetyMode.NONE) {
        world.getFamily(A::class, B::class, C::class)
    }
}