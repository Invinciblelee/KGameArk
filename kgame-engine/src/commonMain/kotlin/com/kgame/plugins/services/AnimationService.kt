package com.kgame.plugins.services // Or a new `animation` package

import androidx.collection.SimpleArrayMap
import androidx.compose.animation.core.RepeatMode
import com.kgame.engine.graphics.atlas.AtlasAnimatedFrame
import com.kgame.engine.graphics.atlas.ImageAtlas
import com.kgame.engine.maps.AnimatedTiledMapTile
import com.kgame.engine.maps.TiledMapAnimationFrame
import com.kgame.plugins.components.Animation
import com.kgame.plugins.components.Identifiable
import com.kgame.plugins.components.InfiniteRepeatable
import com.kgame.plugins.components.Spring
import com.kgame.plugins.components.SpriteAnimation
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
        var status: Status = Status.Playing
    ) {

        enum class Status {
            Playing, Paused, Stopped
        }

    }

    private val states = SimpleArrayMap<Int, RuntimeState>()


    internal fun update(deltaTime: Float) {
        var index = 0
        while (index < states.size()) {
            val state = states.valueAt(index++)
            if (state.status == RuntimeState.Status.Playing) {
                state.elapsedTime += deltaTime
            }
        }
    }

    /**
     * [Get Animation Progress]
     * Calculates the current playback progress of a given [Animation].
     *
     * This function is central to the animation system. It translates the linear `elapsedTime`
     * of an animation into a non-linear progress value, typically between `0.0f` and `1.0f`.
     * This final value is transformed by the animation's specification (e.g., an Easing curve for a [Tween],
     * or a physics simulation for a [Spring]).
     *
     * @param animation The definition of the animation, containing its duration, spec, and repeat mode.
     * @return A [Float] representing the current completion progress of the animation.
     */
    internal fun getProgress(animation: Animation<*, *>): Float {
        // Step 1: Get or create the runtime state for this animation.
        val state = getOrCreateState(animation.id)
        val duration = animation.duration

        // Step 2: Handle cases where the animation is not currently playing.
        if (state.status != RuntimeState.Status.Playing) {
            // If it stopped because it completed, its progress should be locked at 100%.
            if (state.elapsedTime >= duration) return 1f
            // If it was stopped manually (e.g., via `stop(id)`), its progress should be 0%.
            if (state.elapsedTime == 0f) return 0f
        }

        val elapsedTime = state.elapsedTime

        // Step 3: Handle finite animations that have just completed.
        if (!animation.isInfinite && elapsedTime >= duration) {
            // For finite animations, if the time has exceeded the duration,
            // mark its state as stopped and return the final progress of 1.0.
            state.status = RuntimeState.Status.Stopped
            return 1f
        }

        // Step 4: Calculate the final progress value based on the animation's specification.
        return when (val spec = animation.spec) {
            // Case A: A 'Tween' (in-between) animation.
            is Tween -> {
                // a. First, calculate the linear time fraction (0.0 to 1.0).
                val fraction = (elapsedTime / duration).coerceIn(0f, 1f)
                // b. Then, transform this linear fraction into a non-linear progress
                //    value using the specified Easing curve.
                spec.easing.transform(fraction)
            }
            // Case B: An infinitely repeating animation.
            is InfiniteRepeatable -> {
                // a. Calculate the linear time fraction within the current repetition cycle.
                val fraction = (elapsedTime / duration) % 1.0f
                when (spec.repeatMode) {
                    // b. Restart mode: The animation simply plays from the beginning.
                    RepeatMode.Restart -> spec.animation.easing.transform(fraction)
                    // c. Reverse mode: The animation plays forwards, then backwards.
                    RepeatMode.Reverse -> {
                        // This maps the linear time [0, 1] to a "ping-pong" effect of [0, 1, 0].
                        val mirrored = if (fraction <= 0.5f) fraction * 2f else (1f - fraction) * 2f
                        spec.animation.easing.transform(mirrored)
                    }
                }
            }
            // Case C: A 'Spring' physics-based animation.
            is Spring -> {
                // Delegate to a separate physics simulator to calculate the current value
                // based on spring parameters and elapsed time. The "progress" of a spring
                // is the result of its physical simulation.
                SpringRatioSimulation.getFraction(elapsedTime, spec)
            }
        }
    }

    /**
     * Calculates and returns the name of the current frame for a given [SpriteAnimation].
     *
     * This function uses the animation's runtime state (managed by this service) to determine
     * which frame of the animation sequence should be displayed at the current moment.
     * It also handles advancing the frame index and looping the animation.
     *
     * @param animation The [SpriteAnimation] component containing the definition of the animation (name, speed, loop).
     * @param atlas The [ImageAtlas] from which to retrieve the animation frame sequence.
     * @return The name of the frame to be rendered (e.g., "run_1", "idle_0").
     */
    internal fun getCurrentFrame(animation: SpriteAnimation, atlas: ImageAtlas): AtlasAnimatedFrame {
        // Step 1: Retrieve the runtime state for this animation. The state is managed by this service.
        val state = getOrCreateState(animation.id)

        // Step 2: Retrieve the current animation frames.
        val frames = atlas.getAnimatedFrames(animation.name)

        // Step 3: If the animation is not playing, simply return the last known frame.
        if (state.status != RuntimeState.Status.Playing) {
            val safeIndex = state.frameIndex.coerceIn(0, frames.lastIndex)
            return frames[safeIndex]
        }

        // Step 4: Determine the current time within the animation cycle.
        val cycleTime = (state.elapsedTime * animation.speed) % frames.duration

        // Step 5: Use a single loop to find the correct frame for the calculated `cycleTime`.
        val currentFrameIndex = frames.getFrameIndexAtTime(cycleTime)

        state.frameIndex = currentFrameIndex

        // Step 6: Handle the end of a non-looping animation.
        if (!animation.loop && state.elapsedTime * animation.speed >= frames.duration) {
            state.status = RuntimeState.Status.Stopped
        }

        // Step 7: Return the name of the new current frame.
        return frames[state.frameIndex]
    }

    internal fun getCurrentFrame(tile: AnimatedTiledMapTile): TiledMapAnimationFrame {
        val state = getOrCreateState(tile.id)

        val totalDurationSeconds = tile.duration / 1000f

        val elapsedTime = if (totalDurationSeconds > 0f) {
            state.elapsedTime % totalDurationSeconds
        } else {
            0f
        }

        var currentFrame = tile.frames.last()
        var accumulatedTime = 0f
        var index = 0
        while (index < tile.frames.size) {
            val frame = tile.frames[index++]
            accumulatedTime += frame.duration / 1000f
            if (elapsedTime <= accumulatedTime) {
                currentFrame = frame
                break
            }
        }

        return currentFrame
    }

    fun play(id: Int) {
        val state = getOrCreateState(id)
        if (state.status == RuntimeState.Status.Stopped) {
            state.elapsedTime = 0f
        }
        state.status = RuntimeState.Status.Playing
    }

    fun pause(id: Int) {
        states[id]?.status = RuntimeState.Status.Paused
    }

    fun stop(id: Int) {
        val state = states[id]
        if (state != null) {
            state.status = RuntimeState.Status.Stopped
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

fun AnimationService.play(identifiable: Identifiable) = play(identifiable.id)

fun AnimationService.pause(identifiable: Identifiable) = pause(identifiable.id)

fun AnimationService.stop(identifiable: Identifiable) = stop(identifiable.id)