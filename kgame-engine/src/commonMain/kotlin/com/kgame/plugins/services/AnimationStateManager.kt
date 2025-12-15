package com.kgame.plugins.services // Or a new `animation` package

import androidx.collection.SimpleArrayMap

/**
 * [Universal Animation State Manager]
 * A dedicated class responsible for managing the runtime state of all animations in the world.
 * This manager is held by animation systems and allows them to be stateless. It uses
 * a highly efficient SimpleArrayMap to track the state of each animating entity.
 */
class AnimationStateManager {

    /** Holds the generic runtime state for any time-based animation. */
    private data class RuntimeState(
        var elapsedTime: Float = 0f,
        var isPlaying: Boolean = true
    )

    private val states = SimpleArrayMap<Int, RuntimeState>() // Key is the Entity ID

    /**
     * Updates the elapsed time for all currently tracked animations.
     * This should be called once per frame by the master animation system.
     */
    fun update(deltaTime: Float) {
        var index = 0
        while (index < states.size()) {
            val state = states.valueAt(index++)
            if (state.isPlaying) {
                state.elapsedTime += deltaTime
            }
        }
    }

    /**
     * Gets the current progress (0.0 to 1.0) of a property animation for a given entity.
     * @param entityId The ID of the entity.
     * @param duration The total duration of the animation.
     * @param loop Whether the animation should loop.
     * @return A float value representing the animation's progress.
     */
    fun getProgress(entityId: Int, duration: Float, loop: Boolean): Float {
        val state = getOrCreateState(entityId)
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

    fun play(entityId: Int) {
        getOrCreateState(entityId).isPlaying = true
    }

    fun pause(entityId: Int) {
        getOrCreateState(entityId).isPlaying = false
    }

    fun stop(entityId: Int) {
        val state = states[entityId]
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
