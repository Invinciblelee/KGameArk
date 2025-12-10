package com.game.plugins.components

import androidx.compose.ui.geometry.Rect
import com.game.ecs.Component
import com.game.ecs.ComponentType

/**
 * A component that defines the rectangular boundaries of the world.
 * When attached to a camera entity, it restricts the camera's movement
 * to within these bounds.
 */
data class WorldBounds(
    val rect: Rect = Rect.Zero
) : Component<WorldBounds> {
    override fun type() = WorldBounds
    companion object : ComponentType<WorldBounds>()
}
