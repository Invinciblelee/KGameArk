package com.kgame.plugins.services // Or a new `animation` package

import androidx.collection.SimpleArrayMap
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.RepeatMode
import com.kgame.plugins.components.Animation
import com.kgame.plugins.components.InfiniteRepeatable
import com.kgame.plugins.components.Spring
import com.kgame.plugins.components.Tween
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [Universal Animation State Manager]
 * A dedicated class responsible for managing the runtime state of all animations in the world.
 * This manager is held by animation systems and allows them to be stateless. It uses
 * a highly efficient SimpleArrayMap to track the state of each animating entity.
 */
class AnimationService {


    /** Holds the generic runtime state for any time-based animation. */
    private data class RuntimeState(
        var elapsedTime: Float = 0f,
        var frameIndex: Int = 0,
        var isPlaying: Boolean = true
    )

    private val states = SimpleArrayMap<Int, RuntimeState>()


    fun update(deltaTime: Float) {
        var index = 0
        while (index < states.size()) {
            val state = states.valueAt(index++)
            if (state.isPlaying) {
                state.elapsedTime += deltaTime
            }
        }
    }


    fun getProgress(id: Int, duration: Float, loop: Boolean): Float {
        val state = getOrCreateState(id)

        if (!state.isPlaying) return if (state.elapsedTime >= duration) 1f else 0f // Return end or start state if paused/stopped

        if (loop) {
            state.elapsedTime %= duration
        } else if (state.elapsedTime >= duration) {
            state.isPlaying = false // Stop the animation
            return 1f
        }
        
        return state.elapsedTime / duration
    }

    // You can add more complex methods for sprite animations, e.g., getCurrentFrameIndex
    fun play(id: Int) {
        getOrCreateState(id).isPlaying = true
    }

    fun pause(id: Int) {
        getOrCreateState(id).isPlaying = false
    }

    fun stop(id: Int) {
        val state = states[id]
        if (state != null) {
            state.isPlaying = false
            state.elapsedTime = 0f
        }
    }

    private fun getOrCreateState(key: Int): RuntimeState {
        var state = states[key]
        if (state == null) {
            state = RuntimeState()
            states.put(key, state)
        }
        return state
    }
}


/**
 * Updates the animation's progress based on the given delta time.
 * @param deltaTime The time elapsed since the last frame in seconds.
 * @return Whether the animation has updated its loop.
 */
fun Animation<*>.update(deltaTime: Float): Boolean {
    if (this.state != AnimationState.Playing) return false
    this.elapsedTime += deltaTime
    if (!this.isInfinite && this.elapsedTime >= this.duration()) {
        this.elapsedTime = this.duration()
        this.state = AnimationState.Stopped
    }
    return true
}



/**
 * Whether the animation is currently in an infinite loop.
 */
val Animation<*>.isInfinite: Boolean
    get() = spec is InfiniteRepeatable

/**
 * Whether the animation has completed its loop.
 */
val Animation<*>.isFinished: Boolean
    get() = !isInfinite && elapsedTime >= duration()

/**
 * The current progress of the animation, ranging from 0.0 to 1.0.
 */
val Animation<*>.progress: Float
    get() {
        val duration = duration()
        if (duration <= 0f) return if (elapsedTime > 0f) 1f else 0f

        val fraction = if (isInfinite) {
            (elapsedTime / duration) % 1.0f
        } else {
            (elapsedTime / duration).coerceIn(0f, 1f)
        }
        return when (spec) {
            is Tween -> spec.easing.transform(fraction)
            is InfiniteRepeatable -> {
                val linear = (elapsedTime / spec.animation.duration) % 1.0f
                when (spec.repeatMode) {
                    RepeatMode.Restart -> spec.animation.easing.transform(linear)
                    RepeatMode.Reverse -> {
                        val mirrored = if (linear <= 0.5f) linear * 2f else (1f - linear) * 2f
                        spec.animation.easing.transform(mirrored)
                    }
                }
            }
            is Spring -> SpringRatioSimulation.getFraction(elapsedTime, spec)
        }
    }


private object SpringRatioSimulation {
    fun getFraction(elapsedTime: Float, spring: Spring): Float {
        val natFreq = sqrt(spring.stiffness.toDouble())
        val dampingRatio = (spring.damping / (2f * sqrt(spring.stiffness))).toDouble()
        val t = elapsedTime.toDouble()
        val newDisp: Double = when {
            dampingRatio > 1.0 -> overDamped(t, natFreq, dampingRatio)
            dampingRatio == 1.0 -> criticallyDamped(t, natFreq)
            else -> underDamped(t, natFreq, dampingRatio)
        }
        return (1.0 - newDisp).toFloat()
    }

    private fun overDamped(t: Double, natFreq: Double, dampingRatio: Double): Double {
        val r = -dampingRatio * natFreq; val s = natFreq * sqrt(dampingRatio * dampingRatio - 1); val gp = r + s; val gm = r - s; val b = (gm - 0.0) / (gm - gp); val a = 1.0 - b; return a * exp(gm * t) + b * exp(gp * t)
    }

    private fun criticallyDamped(t: Double, natFreq: Double): Double {
        val a = 1.0; val b = natFreq; return (a + b * t) * exp(-natFreq * t)
    }

    private fun underDamped(t: Double, natFreq: Double, dampingRatio: Double): Double {
        val dampedFreq = natFreq * sqrt(1 - dampingRatio * dampingRatio); val r = -dampingRatio * natFreq; val sinCoeff = r / dampedFreq; return exp(r * t) * (cos(dampedFreq * t) + sinCoeff * sin(dampedFreq * t))
    }
}