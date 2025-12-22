package com.kgame.plugins.components

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType

/**
 * A component that defines the parameters for a smooth, lerp-based camera follow behavior.
 * Attaching this component to a camera entity signals the CameraDirector to use this follow mode.
 *
 * @property lerpSpeed The speed factor for the linear interpolation. Higher values result in a tighter, more responsive follow.
 */
data class Smooth(
    var lerpSpeed: Float = 5f
) : Component<Smooth> {
    override fun type() = Smooth
    companion object: ComponentType<Smooth>()
}