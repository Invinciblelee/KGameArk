@file:OptIn(ExperimentalResourceApi::class)

package com.kgame.engine.graphics.atlas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.kgame.plugins.components.Transform
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * This comment block illustrates the JSON data structure this module is designed to parse.
 * It includes metadata for the atlas, information for all individual frames (regions),
 * and predefined animation sequences.
 *
 * {
 *   "frames": {
 *     "frame_0_0": {
 *       "frame": {"x": 0, "y": 0, "w": 32, "h": 32},
 *       "rotated": false,
 *       "trimmed": false,
 *       "spriteSourceSize": {"x": 0, "y": 0, "w": 32, "h": 32},
 *       "sourceSize": {"w": 32, "h": 32},
 *       "customField1": "value_0_0",
 *       "customField2": 0
 *     },
 *     ...
 *   },
 *   "meta": {
 *     "app": "custom_script",
 *     "version": "1.0",
 *     "image": "atlas.png",
 *     "size": {"w": 96, "h": 64},
 *     "scale": "1"
 *   },
 *   "animations": {
 *     "run": [
 *       {"name": "frame_0_0.png", "duration": 0.1},
 *       {"name": "frame_0_1.png", "duration": 0.1},
 *       ...
 *     ]
 *   }
 * }
 */

/**
 * Encapsulates metadata from the atlas file (corresponds to the "meta" field).
 */
data class AtlasMetadata(
    val app: String,
    val version: String,
    val image: String,
    val size: IntSize,
    val scale: Float
)

/**
 * Encapsulates all information for a single static sub-image (region) within the atlas.
 * This corresponds to each child object within the "frames" field of the JSON.
 */
data class AtlasRegion(
    val frame: IntRect,
    val rotated: Boolean,
    val trimmed: Boolean,
    val sourceSize: Size,
    val spriteSourceSize: IntRect,
    val pivot: TransformOrigin,
    val order: Int
) {

    val offset: IntOffset = frame.topLeft
    val size: IntSize = frame.size

}

/**
 * Encapsulates information for a single frame within an animation sequence.
 * This corresponds to each element in the array of an "animations" entry.
 *
 * @property name The name of the frame, used to look up its AtlasRegion.
 * @property duration The duration this frame should be displayed, in seconds.
 */
data class AtlasAnimatedFrame(
    val name: String,
    val duration: Float
)

/**
 * Represents a complete, read-only sequence of frames for a single animation.
 *
 * This class acts as a specialized, high-performance list of [AtlasAnimatedFrame] objects.
 * By using class delegation (`by frames`), it inherits all the standard functionalities of a [List]
 * (like `size`, `get(index)`, `isEmpty()`), while also providing useful animation-specific helper methods.
 * @property name The name of the [AtlasAnimatedFrames]
 * @property frames The internal, private list of animation frames.
 */
data class AtlasAnimatedFrames(
    val name: String,
    private val frames: List<AtlasAnimatedFrame>
) : List<AtlasAnimatedFrame> by frames {

    /**
     * The total duration of one full animation cycle, calculated as the sum of all frame durations.
     * This property is calculated lazily on its first access and then cached.
     */
    val duration: Float by lazy {
        var accumulated = 0f
        var index = 0
        while (index < frames.size) {
            accumulated += frames[index++].duration
        }
        accumulated
    }

    /**
     * [New Method 1: Get Frame at Time]
     * Calculates and returns the specific animation frame that should be displayed at a given time within the cycle.
     * This is the core logic for determining which frame to show.
     *
     * @param timeInCycle The elapsed time within a single animation cycle (must be >= 0).
     * @return The [AtlasAnimatedFrame] corresponding to the given time.
     */
    fun getFrameAtTime(timeInCycle: Float): AtlasAnimatedFrame {
        if (isEmpty()) {
            // This should not happen with valid data, but it's a safe fallback.
            throw NoSuchElementException("Cannot get frame from an empty animation sequence.")
        }

        // Use a local variable to handle time, ensuring it loops correctly for times > duration.
        val effectiveTime = if (duration > 0f) timeInCycle % duration else 0f

        var accumulatedTime = 0f
        var index = 0
        while (index < size) {
            val frame = this[index] // 'this' refers to the list itself
            accumulatedTime += frame.duration
            if (effectiveTime < accumulatedTime) {
                return frame // Found the correct frame for the given time.
            }
            index++
        }

        // If the loop completes, it means the time is exactly at the end or beyond, so return the last frame.
        return last()
    }

    /**
     * [New Method 2: Get Frame Index at Time]
     * A performance-oriented alternative to `getFrameAtTime` that returns only the index of the frame.
     * This is useful when the caller only needs the index, not the full frame object.
     *
     * @param timeInCycle The elapsed time within a single animation cycle.
     * @return The integer index of the frame that corresponds to the given time.
     */
    fun getFrameIndexAtTime(timeInCycle: Float): Int {
        if (isEmpty()) return -1 // Return -1 for an invalid index

        val effectiveTime = if (duration > 0f) timeInCycle % duration else 0f

        var accumulatedTime = 0f
        var index = 0
        while (index < size) {
            accumulatedTime += this[index].duration
            if (effectiveTime < accumulatedTime) {
                return index
            }
            index++
        }
        return lastIndex
    }

    /**
     * [New Method 3: Get Frame by Name]
     * Provides a quick way to find a specific frame within this sequence by its name.
     *
     * @param frameName The name of the frame to find (e.g., "run__1.png").
     * @return The [AtlasAnimatedFrame] if found, otherwise `null`.
     */
    fun getFrameByName(frameName: String): AtlasAnimatedFrame {
        // Since this is not a performance-critical path, a standard find is acceptable.
        var index = 0
        while (index < size) {
            val frame = this[index++]
            if (frame.name == frameName) {
                return frame
            }
        }
        throw IllegalArgumentException("Frame '$frameName' not found in animation '$name'")
    }
}


/**
 * Represents a complete, high-performance Image Atlas (also known as a Texture Atlas or Spritesheet).
 * Its data structure is designed to be compatible with outputs from professional animation tools (like Aseprite)
 * or custom asset processing scripts.
 *
 * @property bitmap The single, large ImageBitmap for the entire atlas.
 * @property metadata The metadata for the atlas file.
 * @property regions A map from a frame's name to its detailed region information for fast static sprite lookups.
 * @property animations A map from an animation's name to its sequence of frames for fast animation lookups.
 */
data class ImageAtlas(
    val bitmap: ImageBitmap,
    val metadata: AtlasMetadata,
    val regions: Map<String, AtlasRegion>,
    val animations: Map<String, AtlasAnimatedFrames>
) {

    /**
     * Retrieves the region data for a single static sub-image by its name.
     *
     * @param name The full filename of the region (e.g., "frame_0_0").
     * @return The corresponding [AtlasRegion].
     */
    fun getRegion(name: String): AtlasRegion {
        return regions[name] ?: throw IllegalArgumentException("Region '$name' not found")
    }

    /**
     * Retrieves the predefined sequence of frames for an animation by its name.
     *
     * @param animationName The name of the animation (e.g., "run").
     * @return A list of [AtlasAnimatedFrame] objects.
     */
    fun getAnimatedFrames(animationName: String): AtlasAnimatedFrames {
        return animations[animationName]
            ?: throw IllegalArgumentException("Animation '$animationName' not found")
    }

    /**
     * Retrieves a specific frame from an animation by its name and index.
     * @param animationName The name of the animation (e.g., "run").
     * @param frameIndex The index of the frame within the animation.
     * @return The corresponding [AtlasAnimatedFrame].
     */
    fun getAnimatedFrame(animationName: String, frameIndex: Int): AtlasAnimatedFrame {
        val frames = getAnimatedFrames(animationName)
        return frames[frameIndex]
    }

    /**
     * Retrieves the index of a specific frame within an animation by its name and frame filename.
     * @param animationName The name of the animation (e.g., "run").
     * @param frameName The filename of the frame (e.g., "frame_0_0").
     * @return The index of the frame within the animation.
     */
    fun getAnimatedFrameIndex(animationName: String, frameName: String): Int {
        val frames = getAnimatedFrames(animationName)
        var index = 0
        while (index < frames.size) {
            if (frames[index].name == frameName) {
                return index
            }
            index++
        }
        throw IllegalArgumentException("Frame '$frameName' not found in animation '$animationName'")
    }

}
