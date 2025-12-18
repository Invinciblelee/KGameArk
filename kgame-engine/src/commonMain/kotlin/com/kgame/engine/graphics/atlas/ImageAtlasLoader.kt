package com.kgame.engine.graphics.atlas

import androidx.compose.ui.geometry.Size
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

interface ImageAtlasLoader {

    suspend fun load(path: String): ImageAtlas

}


class DefaultImageAtlasLoader(
    private val assetsReader: AssetsReader
): ImageAtlasLoader {

    override suspend fun load(path: String): ImageAtlas {
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
                AtlasAnimatedFrames(
                    name = entry.key,
                    frames = frames.map { frame ->
                        AtlasAnimatedFrame(
                            name = frame.jsonObject.getString("name"),
                            duration = frame.jsonObject.getFloat("duration")
                        )
                    }
                )
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

}