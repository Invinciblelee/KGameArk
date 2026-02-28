package com.kgame.plugins.components

import androidx.compose.ui.geometry.Rect
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType

/**
 * Hitbox component using a local coordinate system.
 * By default, [rect] is relative to the center (0,0).
 * @param rect The hitbox area in local space.
 */
data class Hitbox(
    var rect: Rect,
    var enabled: Boolean = true
): Component<Hitbox> {
    override fun type() = Hitbox

    companion object Companion : ComponentType<Hitbox>()
}