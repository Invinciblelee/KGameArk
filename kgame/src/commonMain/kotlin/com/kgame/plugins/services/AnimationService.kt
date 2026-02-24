package com.kgame.plugins.services // Or a new `animation` package

import androidx.collection.SparseArrayCompat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathMeasure
import com.kgame.engine.graphics.atlas.AtlasAnimatedFrame
import com.kgame.engine.graphics.atlas.ImageAtlas
import com.kgame.engine.maps.AnimatedTiledMapTile
import com.kgame.engine.maps.TiledMapAnimationFrame
import com.kgame.engine.math.degrees
import com.kgame.plugins.components.Animation
import com.kgame.ecs.Identifiable
import com.kgame.plugins.components.InfiniteRepeatable
import com.kgame.plugins.components.PathAnimation
import com.kgame.plugins.components.Spring
import com.kgame.plugins.components.SpriteAnimation
import com.kgame.plugins.components.Tween
import kotlin.math.atan2
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
        var pathLength: Float = -1f,
        var clip: String? = null,
        var status: Status,
    ) {
        enum class Status {
            Playing, Paused, Stopped
        }
    }

    private val states = SparseArrayCompat<RuntimeState>()
    private val sharedMeasure = PathMeasure()

    /** Creates a composite key using entity ID and animation name. */
    private fun getCombinedId(id: Int, name: String): Int = id * 31 + name.hashCode()

    internal fun update(deltaTime: Float) {
        var index = 0
        val size = states.size()
        while (index < size) {
            val state = states.valueAt(index++)
            if (state.status == RuntimeState.Status.Playing) {
                state.elapsedTime += deltaTime
            }
        }
    }

    /** * Handles state retrieval and detects if the animation name has changed.
     * If the name is different, it resets progress to ensure synchronized playback.
     */
    private fun getOrCreateState(id: Int, name: String? = null, clip: String? = null, autoPlay: Boolean = false): RuntimeState {
        val combinedId = name?.let { getCombinedId(id, it) } ?: id
        var state = states[combinedId]
        if (state == null) {
            state = RuntimeState(clip = clip, status = if (autoPlay) RuntimeState.Status.Playing else RuntimeState.Status.Stopped)
            states.put(combinedId, state)
        } else if (clip != null && state.clip != clip) {
            /** Reset state if the animation name in this specific slot changes. */
            state.clip = clip
            state.elapsedTime = 0f
            state.frameIndex = 0
            state.pathLength = -1f
            state.status = RuntimeState.Status.Playing
        }
        return state
    }

    internal fun getProgress(animation: Animation<*, *>): Float {
        val state = getOrCreateState(animation.id, animation.name, autoPlay = animation.autoPlay)
        val duration = animation.duration

        val totalLimit = if (animation.spec is InfiniteRepeatable) {
            if (animation.spec.iterations == Int.MAX_VALUE) {
                Float.POSITIVE_INFINITY
            } else {
                (duration * animation.spec.iterations).coerceAtLeast(duration)
            }
        } else {
            duration
        }

        if (state.status != RuntimeState.Status.Playing) {
            if (state.elapsedTime >= totalLimit) {
                return if (animation.isInfinite) 0f else 1f
            }
            if (state.elapsedTime == 0f) return 0f
        }

        val elapsedTime = state.elapsedTime
        if (elapsedTime >= totalLimit) {
            state.status = RuntimeState.Status.Stopped
        }

        return when (val spec = animation.spec) {
            is Tween -> {
                val fraction = (elapsedTime / duration).coerceIn(0f, 1f)
                spec.easing.transform(fraction)
            }
            is InfiniteRepeatable -> {
                val fraction = (elapsedTime / duration) % 1.0f
                when (spec.repeatMode) {
                    RepeatMode.Restart -> spec.animation.easing.transform(fraction)
                    RepeatMode.Reverse -> {
                        val mirrored = if (fraction <= 0.5f) fraction * 2f else (1f - fraction) * 2f
                        spec.animation.easing.transform(mirrored)
                    }
                }
            }
            is Spring -> {
                SpringRatioSimulation.getFraction(elapsedTime, spec)
            }
        }
    }

    internal fun getCurrentFrame(animation: SpriteAnimation, atlas: ImageAtlas): AtlasAnimatedFrame {
        val state = getOrCreateState(animation.id, animation.name, autoPlay = animation.autoPlay)

        if (animation.clip != state.clip && !state.clip.isNullOrBlank()) {
            animation.clip = state.clip.orEmpty()
        }

        val frames = atlas.getAnimatedFrames(animation.clip)

        if (state.status != RuntimeState.Status.Playing) {
            val safeIndex = state.frameIndex.coerceIn(0, frames.lastIndex)
            return frames[safeIndex]
        }

        val cycleTime = (state.elapsedTime * animation.speed) % frames.duration
        val currentFrameIndex = frames.getFrameIndexAtTime(cycleTime)
        state.frameIndex = currentFrameIndex

        if (!animation.loop && state.elapsedTime * animation.speed >= frames.duration) {
            state.status = RuntimeState.Status.Stopped
        }

        return frames[state.frameIndex]
    }

    internal fun getCurrentFrame(tile: AnimatedTiledMapTile): TiledMapAnimationFrame {
        /** Tiled Map Tile uses its own ID as key (usually one animation per tile). */
        val state = getOrCreateState(tile.id, autoPlay = true)
        val totalDurationSeconds = tile.duration / 1000f
        val elapsedTime = if (totalDurationSeconds > 0f) state.elapsedTime % totalDurationSeconds else 0f

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

    internal fun getPathPosition(animation: PathAnimation): Offset {
        val state = getOrCreateState(animation.id, animation.name, autoPlay = animation.autoPlay)
        if (state.pathLength < 0f) {
            sharedMeasure.setPath(animation.path, false)
            state.pathLength = sharedMeasure.length
        }
        val progress = getProgress(animation)
        sharedMeasure.setPath(animation.path, false)
        return sharedMeasure.getPosition(progress * state.pathLength)
    }

    internal fun getPathRotation(animation: PathAnimation): Float {
        val state = getOrCreateState(animation.id, animation.name, autoPlay = animation.autoPlay)
        val progress = getProgress(animation)
        sharedMeasure.setPath(animation.path, false)
        val tangent = sharedMeasure.getTangent(progress * state.pathLength)
        if (tangent.x == 0f && tangent.y == 0f) {
            return 0f + animation.rotationOffset
        }
        val angle = atan2(tangent.y.toDouble(), tangent.x.toDouble())
        return degrees(angle).toFloat() + animation.rotationOffset
    }

    /** * Control functions requiring explicit name to target the correct composite key.
     * @param id The entity ID.
     * @param name The animation name.
     * @param clip The animation clip, e.g. "idle", "walk", etc.
     */
    fun play(id: Int, name: String, clip: String? = null) {
        val state = getOrCreateState(id, name, clip, autoPlay = true)
        if (state.status == RuntimeState.Status.Stopped) {
            state.elapsedTime = 0f
        }
        state.status = RuntimeState.Status.Playing
    }

    fun pause(id: Int, name: String) {
        states[getCombinedId(id, name)]?.status = RuntimeState.Status.Paused
    }

    fun stop(id: Int, name: String) {
        states[getCombinedId(id, name)]?.apply {
            status = RuntimeState.Status.Stopped
            elapsedTime = 0f
        }
    }

    fun isPlaying(id: Int, name: String): Boolean =
        states[getCombinedId(id, name)]?.status == RuntimeState.Status.Playing

    fun isPaused(id: Int, name: String): Boolean =
        states[getCombinedId(id, name)]?.status == RuntimeState.Status.Paused

    fun isStopped(id: Int, name: String): Boolean =
        states[getCombinedId(id, name)]?.status == RuntimeState.Status.Stopped
}

/** Convenience extensions for Identifiable objects. */
fun AnimationService.play(identifiable: Identifiable, name: String, clip: String? = null) = play(identifiable.id, name, clip)
fun AnimationService.pause(identifiable: Identifiable, name: String) = pause(identifiable.id, name)
fun AnimationService.stop(identifiable: Identifiable, name: String) = stop(identifiable.id, name)

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