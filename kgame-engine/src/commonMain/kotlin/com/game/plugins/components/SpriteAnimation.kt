package com.game.plugins.components

import com.game.ecs.Component
import com.game.ecs.ComponentType
import com.game.engine.image.ImageAtlas

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

    internal var elapsedTime: Float = 0f
    internal var state: AnimationState = if (autoPlay) AnimationState.Playing else AnimationState.Stopped
    internal var currentFrameIndex = 0

}

/**
 * Updates the animation state of the sprite.
 * @param atlas The image atlas of the sprite.
 * @param deltaTime The time elapsed since the last frame.
 */
fun SpriteAnimation.update(atlas: ImageAtlas, deltaTime: Float) {
    if (state != AnimationState.Playing) return

    val animationSequence = atlas.getAnimatedFrames(name)

    val frameIndex = currentFrameIndex.coerceIn(0, animationSequence.lastIndex)
    val currentFrame = animationSequence[frameIndex]
    val currentFrameDuration = currentFrame.duration

    if (currentFrameDuration <= 0f) {
        currentFrameIndex++
        elapsedTime = 0f
        return
    }

    elapsedTime += deltaTime * speed

    while (elapsedTime >= currentFrameDuration) {
        elapsedTime -= currentFrameDuration
        currentFrameIndex++

        if (currentFrameIndex >= animationSequence.size) {
            if (loop) {
                currentFrameIndex = 0
            } else {
                currentFrameIndex = animationSequence.size - 1
                elapsedTime = currentFrameDuration
                state = AnimationState.Stopped
                break
            }
        }
    }
}

/**
 * Gets the current frame name of the animation.
 * @param atlas The image atlas of the sprite.
 * @return The name of the current frame.
 */
fun SpriteAnimation.getCurrentFrameName(atlas: ImageAtlas): String {
    val animationSequence = atlas.getAnimatedFrames(name)
    val safeIndex = currentFrameIndex.coerceIn(0, animationSequence.lastIndex)
    return animationSequence[safeIndex].name
}

/**
 * Starts the animation.
 */
fun SpriteAnimation.play() {
    if (state == AnimationState.Stopped) {
        elapsedTime = 0f
        currentFrameIndex = 0
    }
    state = AnimationState.Playing
}

/**
 * Pauses the animation.
 */
fun SpriteAnimation.pause() {
    state = AnimationState.Paused
}

/**
 * Stops the animation.
 */
fun SpriteAnimation.stop() {
    state = AnimationState.Stopped
    elapsedTime = 0f
    currentFrameIndex = 0
}