package com.kgame.plugins.systems

import androidx.compose.ui.geometry.Offset
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.plugins.components.Axis
import com.kgame.plugins.components.Scroller
import com.kgame.plugins.components.Transform

/**
 * The ScrollerDriveSystem drives a constant-speed scrolling background (horizontal or vertical)
 * by updating the Transform of any entity that carries a ScrollLock component.
 *
 * Typical use: attach to a background/level segment and set axis = Y with negative speed
 * to create the classic "downward scroll" seen in vertical shoot-'em-ups.
 */
class ScrollerDriveSystem : IteratingSystem(
    family = family { all(Scroller, Transform) }
) {
    override fun onTickEntity(entity: Entity) {
        val lock = entity[Scroller]
        val transform = entity[Transform]

        val dir = when (lock.axis) {
            Axis.X -> Offset(lock.speed * deltaTime, 0f)
            Axis.Y -> Offset(0f, -lock.speed * deltaTime)
        }
        transform.position += dir
    }
}