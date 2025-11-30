package com.game.plugins.components

import com.game.ecs.Component
import com.game.ecs.ComponentType
import com.game.engine.asset.ImageAtlas

/**
 * A component of SpriteAnimation, used to control the animation of a sprite.
 * @param name The name of the animation sequence.
 * @param speed The speed of the animation.
 * @param loop Whether the animation should loop.
 * @param autoPlay Whether the animation should start automatically.
 */
data class SpriteAnimation(
    var name: String,
    var speed: Float = 1f,
    var loop: Boolean = true,
    var autoPlay: Boolean = true
): Component<SpriteAnimation> {
    override fun type() = SpriteAnimation
    companion object Companion : ComponentType<SpriteAnimation>()

    /**
     * Whether the animation is currently playing.
     */
    var isPlaying: Boolean = autoPlay
        private set

    /**
     * Whether the animation has been started.
     */
    var isStarted: Boolean = false
        private set

    private var escapedTime = 0f
    private var currentFrameIndex = 0

    internal fun update(atlas: ImageAtlas, deltaTime: Float): String {
        val animationSequence = atlas.getAnimatedFrames(name)

        val currentFrame = animationSequence[currentFrameIndex]
        val currentFrameDuration = currentFrame.duration * speed
        if (!isPlaying) return currentFrame.name

        escapedTime += deltaTime

        if (escapedTime >= currentFrameDuration) {
            escapedTime -= currentFrameDuration

            currentFrameIndex++

            if (currentFrameIndex >= animationSequence.size) {
                if (loop) {
                    currentFrameIndex = 0
                } else {
                    currentFrameIndex = animationSequence.size - 1
                    isPlaying = false
                    isStarted = false
                }
            }

            val nextFrame = animationSequence[currentFrameIndex]
            return nextFrame.name
        } else {
            return currentFrame.name
        }
    }

    /**
     * Starts the animation.
     */
    fun play() {
        if (!isStarted) {
            isStarted = true
            escapedTime = 0f
            currentFrameIndex = 0
        }

        isPlaying = true
    }

    /**
     * Pauses the animation.
     */
    fun pause() {
        isPlaying = false
    }

    /**
     * Stops the animation.
     */
    fun stop() {
        isPlaying = false
        isStarted = false
        escapedTime = 0f
        currentFrameIndex = 0
    }

}