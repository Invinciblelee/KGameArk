package com.kgame.engine.maps

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.kgame.engine.asset.AssetsReader
import com.kgame.engine.utils.Color
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
            columns = mapWidthInTiles,
            rows = mapHeightInTiles,
            tileWidth = tileWidth,
            tileHeight = tileHeight,
            layers = mapLayers,
            tilesets = mapSets,
            properties = properties
        ).also {
            println(it)
        }
    }

    // --- Core Parsing Helpers ---

    /**
     * Recursively parses all layer types contained within a parent element (map or group).
     */
    private suspend fun parseLayers(
        tmxPath: String,
        children: List<Element>,
        columns: Int,
        rows: Int
    ): List<TiledMapLayer> {
        val layers = mutableListOf<TiledMapLayer>()
        children.forEach { child ->
            when (child.tagName()) {
                "layer" -> layers.add(parseTiledMapTileLayer(child, columns, rows))
                "objectgroup" -> layers.add(parseTiledMapObjectLayer(child))
                "imagelayer" -> layers.add(parseTiledMapImageLayer(tmxPath, child))
                "group" -> layers.add(parseTiledMapGroupLayer(tmxPath, child, columns, rows))
                // Ignore other tags at map/group level like properties, tileset, etc.
            }
        }
        return layers
    }

    // --- Tileset Parsing ---

    private suspend fun parseTiledMapSet(tmxPath: String, element: Element): TiledMapSet {
        val id = element.attr("firstgid").toInt()
        val name = element.attr("name")

        val source = element.attr("source")
        val tilesetElement = if (source.isNotEmpty()) {
            val tsxPath = getRelativePath(tmxPath, source)
            val tsxContent = assetsReader.readBytes(tsxPath).decodeToString()
            Ksoup.parseXml(tsxContent).selectFirst("tileset")
                ?: throw IllegalStateException("Failed to find <tileset> element in the provided TSX file: $tsxPath")
        } else {
            element
        }

        val imageElement = tilesetElement.selectFirst("image")
            ?: throw IllegalStateException("Tileset $name is missing the <image> tag.")

        val imageSource = imageElement.attr("source")
        val tilesetImagePath = getRelativePath(tmxPath, imageSource)

        val imageBytes = assetsReader.readBytes(tilesetImagePath)
        val imageBitmap = imageBytes.decodeToImageBitmap()

        val tileOffsetElement = tilesetElement.selectFirst("tileoffset")
        val offset = tileOffsetElement?.let {
            IntOffset(it.attr("x").toInt(), it.attr("y").toInt())
        } ?: IntOffset.Zero

        val tileWidth = tilesetElement.attr("tilewidth").toIntOrNull() ?: 0
        val tileHeight = tilesetElement.attr("tileheight").toIntOrNull() ?: 0
        val spacing = tilesetElement.attr("spacing").toIntOrNull() ?: 0
        val margin = tilesetElement.attr("margin").toIntOrNull() ?: 0

        val terrains = tilesetElement.select("terraintypes terrain").associate {
            it.attr("name") to it.attr("tile").toInt()
        }

        val tilesMetadata = tilesetElement.select("tile").mapNotNull { tileElement ->
            parseTiledMapTile(tileElement)
        }.associateBy { it.id }

        return TiledMapSet(
            id = id,
            name = name,
            image = imageBitmap,
            tileWidth = tileWidth,
            tileHeight = tileHeight,
            spacing = spacing,
            margin = margin,
            offset = offset,
            terrains = terrains,
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
        columns: Int,
        rows: Int
    ): TiledMapTileLayer {
        val name = element.attr("name")
        val color = Color(hex = element.attr("color"), defaultColor = Color.Transparent)
        val opacity = element.attr("opacity").toFloatOrNull() ?: 1f
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
                val expectedSize = columns * rows
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

        check(intArrayData.size == columns * rows) {
            "Tile layer data size mismatch. Expected ${columns * rows}, got ${intArrayData.size}"
        }

        return TiledMapTileLayer(
            name = name,
            color = color,
            opacity = opacity,
            visible = visible,
            properties = properties,
            columns = columns,
            rows = rows,
            data = intArrayData
        )
    }

    // --- Layer Parsing: Object Layer ---

    private fun parseTiledMapObjectLayer(element: Element): TiledMapObjectLayer {
        val name = element.attr("name")
        val color = Color(hex = element.attr("color"), defaultColor = Color.Transparent)
        val opacity = element.attr("opacity").toFloatOrNull() ?: 1f
        val visible = element.attr("visible").let { it.isEmpty() || it != "0" }
        val properties = parseProperties(element.selectFirst("properties"))

        val objects = element.select("object").map { objElement ->
            val objId = objElement.attr("id").toIntOrNull() ?: 0
            val objName = objElement.attr("name")
            val objType = objElement.attr("type")
            val objVisible = objElement.attr("visible").let { it.isEmpty() || it != "0" }
            val objProperties = parseProperties(objElement.selectFirst("properties"))

            val x = objElement.attr("x").toIntOrNull() ?: 0
            val y = objElement.attr("y").toIntOrNull() ?: 0
            val width = objElement.attr("width").toIntOrNull() ?: 0
            val height = objElement.attr("height").toIntOrNull() ?: 0

            val gid = objElement.attr("gid").toIntOrNull()

            if (gid != null) {
                TiledMapTileObject(
                    id = objId,
                    name = objName,
                    type = objType,
                    visible = objVisible,
                    properties = objProperties,
                    offset = IntOffset(x, y),
                    size = IntSize(width, height),
                    gid = gid
                )
            } else {
                val shape = when {
                    objElement.selectFirst("polygon") != null -> {
                        val pointsStr = objElement.selectFirst("polygon")?.attr("points") ?: ""
                        val points = pointsStr.split(" ").filter { it.isNotBlank() }.map { pair ->
                            val coords = pair.split(",")
                            IntOffset(coords[0].toInt(), coords[1].toInt())
                        }
                        TiledMapShape.Polygon(points)
                    }
                    objElement.selectFirst("ellipse") != null -> {
                        TiledMapShape.Ellipse(IntSize(width, height))
                    }
                    objElement.selectFirst("point") != null -> {
                        TiledMapShape.Point
                    }
                    else -> {
                        TiledMapShape.Rectangle(IntSize(width, height))
                    }
                }
                TiledMapShapeObject(
                    id = objId,
                    name = objName,
                    type = objType,
                    visible = objVisible,
                    properties = objProperties,
                    offset = IntOffset(x, y),
                    shape = shape
                )
            }
        }

        return TiledMapObjectLayer(
            name = name,
            color = color,
            opacity = opacity,
            visible = visible,
            properties = properties,
            objects = objects
        )
    }

    // --- Layer Parsing: Image Layer ---

    private suspend fun parseTiledMapImageLayer(tmxPath: String, element: Element): TiledMapImageLayer {
        val name = element.attr("name")
        val color = Color(hex = element.attr("color"), defaultColor = Color.Transparent)
        val opacity = element.attr("opacity").toFloatOrNull() ?: 1f
        val visible = element.attr("visible").let { it.isEmpty() || it != "0" }
        val properties = parseProperties(element.selectFirst("properties"))

        val imageElement = element.selectFirst("image")
            ?: throw IllegalStateException("Image layer '$name' is missing <img> tag.")

        val imageSource = imageElement.attr("source")
        val imageBytes = assetsReader.readBytes(getRelativePath(tmxPath, imageSource))
        val imageBitmap = imageBytes.decodeToImageBitmap()

        val x = element.attr("offsetx").toIntOrNull() ?: 0
        val y = element.attr("offsety").toIntOrNull() ?: 0

        val width = element.attr("width").toIntOrNull() ?: imageBitmap.width
        val height = element.attr("height").toIntOrNull() ?: imageBitmap.height

        return TiledMapImageLayer(
            name = name,
            color = color,
            opacity = opacity,
            visible = visible,
            properties = properties,
            image = imageBitmap,
            offset = IntOffset(x, y),
            size = IntSize(width, height)
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
        val color = Color(hex = element.attr("color"), defaultColor = Color.Transparent)
        val opacity = element.attr("opacity").toFloatOrNull() ?: 1f
        val visible = element.attr("visible").let { it.isEmpty() || it != "0" }
        val properties = parseProperties(element.selectFirst("properties"))

        // Recursively parse child layers
        val childLayers = parseLayers(tmxPath, element.children(), mapWidth, mapHeight)

        return TiledMapGroupLayer(
            name = name,
            color = color,
            opacity = opacity,
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