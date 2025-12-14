@file:OptIn(ExperimentalResourceApi::class)

package com.kgame.engine.image

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.kgame.engine.asset.AssetsReader
import com.kgame.engine.utils.getBoolean
import com.kgame.engine.utils.getFloat
import com.kgame.engine.utils.getInt
import com.kgame.engine.utils.getObject
import com.kgame.engine.utils.getString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
    val animations: Map<String, List<AtlasAnimatedFrame>>
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
    fun getAnimatedFrames(animationName: String): List<AtlasAnimatedFrame> {
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

internal suspend fun loadImageAtlas(assetsReader: AssetsReader, path: String): ImageAtlas {
    val json = assetsReader.readBytes(path).decodeToString()
    val jsonObject = Json.decodeFromString<JsonObject>(json)

    val metadata = jsonObject.getValue("meta").let {
        AtlasMetadata(
            app = it.jsonObject.getString("app"),
            version = it.jsonObject.getString("version"),
            image = it.jsonObject.getString("image"),
            size = it.jsonObject.getObject("size").let { size ->
                IntSize(size.getInt("w"), size.getInt("h"))
            },
            scale = it.jsonObject.getFloat("scale")
        )
    }

    val frames = jsonObject.getValue("frames").let {
        it.jsonObject.mapValues { entry ->
            val region = entry.value.jsonObject
            AtlasRegion(
                frame = region.getObject("frame").let { frame ->
                    IntRect(
                        IntOffset(frame.getInt("x"), frame.getInt("y")),
                        IntSize(frame.getInt("w"), frame.getInt("h"))
                    )
                },
                rotated = region.getBoolean("rotated"),
                trimmed = region.getBoolean("trimmed"),
                sourceSize = region.getObject("sourceSize").let { size ->
                    Size(size.getFloat("w"), size.getFloat("h"))
                },
                order = region.getInt("order")
            )
        }
    }

    val animations = jsonObject.getValue("animations").let {
        it.jsonObject.mapValues { entry ->
            val frames = entry.value.jsonArray
            frames.map { frame ->
                AtlasAnimatedFrame(
                    name = frame.jsonObject.getString("name"),
                    duration = frame.jsonObject.getFloat("duration")
                )
            }
        }
    }

    val imageData = assetsReader.readBytes(path.substringBeforeLast("/") + "/" + metadata.image)
    val bitmap = imageData.decodeToImageBitmap()

    return ImageAtlas(
        bitmap = bitmap,
        metadata = metadata,
        regions = frames,
        animations = animations
    )
}
