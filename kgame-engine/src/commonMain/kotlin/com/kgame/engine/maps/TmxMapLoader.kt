package com.kgame.engine.maps

import androidx.compose.ui.graphics.decodeToImageBitmap
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.kgame.engine.asset.AssetsReader
import com.kgame.engine.utils.zip.Compression
import com.kgame.engine.utils.zip.decompress
import kotlin.io.encoding.Base64

/**
 * [TmxMapLoader]
 *
 * Implements the TiledMapLoader interface to parse Tiled Map Format (TMX) XML files.
 * It uses Ksoup to parse the XML structure and AssetsReader to load associated images.
 *
 * NOTE: This implementation currently only supports CSV encoding for tile layer data.
 * Base64 encoding (compressed or uncompressed) must be added for full TMX compatibility.
 */
class TmxMapLoader(
    private val assetsReader: AssetsReader
) : TiledMapLoader {

    /**
     * Loads and parses a TMX map file from the specified path into a TiledMapData object.
     *
     * @param path The relative path to the TMX file.
     * @return A fully constructed TiledMapData object.
     * @throws IllegalStateException if the TMX structure is invalid or essential tags are missing.
     * @throws UnsupportedOperationException if an unsupported tile data encoding is encountered.
     */
    override suspend fun load(path: String): TiledMapData {
        // 1. Load and parse XML
        val tmxXml = assetsReader.readBytes(path).decodeToString()
        val doc = Ksoup.parseXml(tmxXml)
        val mapElement = requireNotNull(doc.selectFirst("map")) {
            "Invalid TMX file: <map> tag not found."
        }

        // 2. Parse fundamental map properties
        val tileWidth = mapElement.attr("tilewidth").toInt()
        val tileHeight = mapElement.attr("tileheight").toInt()
        val mapWidthInTiles = mapElement.attr("width").toInt()
        val mapHeightInTiles = mapElement.attr("height").toInt()

        // 3. Parse Tilesets
        val mapSets = mapElement.select("tileset").map { element ->
            parseTiledMapSet(path, element)
        }

        // 4. Parse Layers (including groups recursively)
        val mapLayers = parseLayers(path, mapElement.children(), mapWidthInTiles, mapHeightInTiles)

        // 5. Parse Map Custom Properties
        val properties = parseProperties(mapElement.selectFirst("properties"))

        // 6. Build TiledMapData
        return TiledMapData(
            width = mapWidthInTiles * tileWidth,     // Total map width in pixels
            height = mapHeightInTiles * tileHeight,  // Total map height in pixels
            tileWidth = tileWidth,
            tileHeight = tileHeight,
            layers = mapLayers,
            sets = mapSets,
            properties = properties
        )
    }

    // --- Core Parsing Helpers ---

    /**
     * Recursively parses all layer types contained within a parent element (map or group).
     */
    private suspend fun parseLayers(
        tmxPath: String,
        children: List<Element>,
        mapWidth: Int,
        mapHeight: Int
    ): List<TiledMapLayer> {
        val layers = mutableListOf<TiledMapLayer>()
        children.forEach { child ->
            when (child.tagName()) {
                "layer" -> layers.add(parseTiledMapTileLayer(child, mapWidth, mapHeight))
                "objectgroup" -> layers.add(parseTiledMapObjectLayer(child))
                "imagelayer" -> layers.add(parseTiledMapImageLayer(tmxPath, child))
                "group" -> layers.add(parseTiledMapGroupLayer(tmxPath, child, mapWidth, mapHeight))
                // Ignore other tags at map/group level like properties, tileset, etc.
            }
        }
        return layers
    }

    // --- Tileset Parsing ---

    private suspend fun parseTiledMapSet(tmxPath: String, element: Element): TiledMapSet {
        val id = element.attr("firstgid").toInt()
        val name = element.attr("name")
        val imageElement = element.selectFirst("image")
            ?: throw IllegalStateException("Tileset $name is missing the <image> tag.")

        val imageSource = imageElement.attr("source")
        val tilesetImagePath = getRelativePath(tmxPath, imageSource)

        // Load the image bitmap using AssetsReader
        val imageBytes = assetsReader.readBytes(tilesetImagePath)
        val imageBitmap = imageBytes.decodeToImageBitmap() // Placeholder function

        // Parse special tile metadata
        val tilesMetadata = element.select("tile").mapNotNull { tileElement ->
            parseTiledMapTile(tileElement)
        }.associateBy { it.id }

        return TiledMapSet(
            id = id,
            name = name,
            image = imageBitmap,
            tiles = tilesMetadata
        )
    }

    private fun parseTiledMapTile(element: Element): TiledMapTile? {
        val localId = element.attr("id").toIntOrNull() ?: return null
        val properties = parseProperties(element.selectFirst("properties"))

        // Check for animation frames
        val animationFrames = element.select("animation frame").map { frameElement ->
            TiledMapAnimationFrame(
                id = frameElement.attr("tileid").toInt(),
                duration = frameElement.attr("duration").toInt()
            )
        }

        return if (animationFrames.isNotEmpty()) {
            AnimatedTiledMapTile(localId, properties, animationFrames)
        } else {
            StaticTiledMapTile(localId, properties)
        }
    }

    // --- Layer Parsing: Tile Layer ---

    private fun parseTiledMapTileLayer(
        element: Element,
        mapWidth: Int,
        mapHeight: Int
    ): TiledMapTileLayer {
        val name = element.attr("name")
        val visible = element.attr("visible").let { it.isEmpty() || it != "0" }
        val properties = parseProperties(element.selectFirst("properties"))

        val dataElement = requireNotNull(element.selectFirst("data") ) {
            "Tile layer $name is missing <data> tag."
        }

        val encoding = dataElement.attr("encoding")
        val compression = dataElement.attr("compression")
        val rawData = dataElement.text().trim()

        val intArrayData: IntArray

        when (encoding) {
            "csv" -> {
                check(compression.isEmpty()) { "CSV encoding does not support compression." }
                // Only CSV
                intArrayData = rawData.split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toIntArray()
            }
            "base64" -> {
                // 1. Decode Base64
                var tileBytes = Base64.decode(rawData)

                // 2. Decompress
                if (compression.isNotEmpty()) {
                    tileBytes = tileBytes.decompress(Compression.from(compression))
                }

                // 3. Read GID array (Little-Endian)
                val expectedSize = mapWidth * mapHeight
                check(tileBytes.size == expectedSize * 4) {
                    "Tile data size mismatch after decoding/decompression. Expected ${expectedSize * 4} bytes, got ${tileBytes.size}"
                }

                intArrayData = IntArray(expectedSize) { i ->
                    tileBytes.readIntLittleEndian(i * 4)
                }
            }
            else -> {
                error("Unsupported Tiled data format encoding: $encoding.")
            }
        }

        check(intArrayData.size == mapWidth * mapHeight) {
            "Tile layer data size mismatch. Expected ${mapWidth * mapHeight}, got ${intArrayData.size}"
        }

        return TiledMapTileLayer(
            name = name,
            visible = visible,
            properties = properties,
            width = mapWidth,
            height = mapHeight,
            data = intArrayData
        )
    }

    // --- Layer Parsing: Object Layer ---

    private fun parseTiledMapObjectLayer(element: Element): TiledMapObjectLayer {
        val name = element.attr("name")
        val visible = element.attr("visible").let { it.isEmpty() || it != "0" }
        val properties = parseProperties(element.selectFirst("properties"))

        val objects = element.select("object").map { objElement ->
            TiledMapObject(
                id = objElement.attr("id").toInt(),
                name = objElement.attr("name"),
                type = objElement.attr("type"),
                x = objElement.attr("x").toFloat(),
                y = objElement.attr("y").toFloat(),
                width = objElement.attr("width").toFloatOrNull() ?: 0f,
                height = objElement.attr("height").toFloatOrNull() ?: 0f,
                properties = parseProperties(objElement.selectFirst("properties"))
            )
        }

        return TiledMapObjectLayer(
            name = name,
            visible = visible,
            properties = properties,
            objects = objects
        )
    }

    // --- Layer Parsing: Image Layer ---

    private suspend fun parseTiledMapImageLayer(tmxPath: String, element: Element): TiledMapImageLayer {
        val name = element.attr("name")
        val visible = element.attr("visible").let { it.isEmpty() || it != "0" }
        val properties = parseProperties(element.selectFirst("properties"))

        val imageElement = element.selectFirst("image")
            ?: throw IllegalStateException("Image layer '$name' is missing <img> tag.")

        val imageSource = imageElement.attr("source")
        val imageBytes = assetsReader.readBytes(getRelativePath(tmxPath, imageSource))
        val imageBitmap = imageBytes.decodeToImageBitmap()

        val x = element.attr("offsetx").toFloatOrNull() ?: 0f
        val y = element.attr("offsety").toFloatOrNull() ?: 0f

        return TiledMapImageLayer(
            name = name,
            visible = visible,
            properties = properties,
            image = imageBitmap,
            x = x,
            y = y
        )
    }

    // --- Layer Parsing: Group Layer (Recursive) ---

    private suspend fun parseTiledMapGroupLayer(
        tmxPath: String,
        element: Element,
        mapWidth: Int,
        mapHeight: Int
    ): TiledMapGroupLayer {
        val name = element.attr("name")
        val visible = element.attr("visible").let { it.isEmpty() || it != "0" }
        val properties = parseProperties(element.selectFirst("properties"))

        // Recursively parse child layers
        val childLayers = parseLayers(tmxPath, element.children(), mapWidth, mapHeight)

        return TiledMapGroupLayer(
            name = name,
            visible = visible,
            properties = properties,
            layers = childLayers
        )
    }

    // --- Utility Functions ---

    /**
     * Parses all <property> tags under a parent element into a map.
     */
    private fun parseProperties(propertiesElement: Element?): Map<String, String> {
        if (propertiesElement == null) return emptyMap()

        return propertiesElement.select("property").associate { propElement ->
            val name = propElement.attr("name")
            // TMX properties can have a 'value' attribute or be empty if no value is set
            val value = propElement.attr("value")
            name to value
        }
    }

    /**
     * Simplistically resolves a relative path based on the location of the TMX file.
     * Assumes a simple file system path structure.
     */
    private fun getRelativePath(tmxPath: String, relativeToTmx: String): String {
        val lastSeparator = tmxPath.lastIndexOf('/')
        return if (lastSeparator > 0) {
            // Append relative path to the directory of the TMX file
            tmxPath.take(lastSeparator + 1) + relativeToTmx
        } else {
            // Assume TMX is in the root asset directory
            relativeToTmx
        }
    }
}

private fun ByteArray.readIntLittleEndian(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
}