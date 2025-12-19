package com.kgame.engine.maps

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.decodeToImageBitmap
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
        val mapWidthInTiles = mapElement.attr("width").toInt()
        val mapHeightInTiles = mapElement.attr("height").toInt()
        val tileWidth = mapElement.attr("tilewidth").toFloat()
        val tileHeight = mapElement.attr("tileheight").toFloat()
        val renderOrder = mapElement.attr("renderorder")

        // 3. Parse Tilesets
        val mapSets = mapElement.select("tileset").map { element ->
            parseTiledMapSet(element, path)
        }

        // 4. Parse Layers (including groups recursively)
        val mapLayers = parseLayers(mapElement.children(), mapWidthInTiles, mapHeightInTiles, renderOrder, path)

        // 5. Parse Map Custom Properties
        val properties = parseProperties(mapElement.selectFirst("properties"))

        // 6. Build TiledMapData
        return TiledMapData(
            columns = mapWidthInTiles,
            rows = mapHeightInTiles,
            tileSize = Size(tileWidth, tileHeight),
            layers = mapLayers,
            tilesets = mapSets,
            properties = properties
        )
    }

    // --- Core Parsing Helpers ---

    /**
     * Recursively parses all layer types contained within a parent element (map or group).
     */
    private suspend fun parseLayers(
        children: List<Element>,
        columns: Int,
        rows: Int,
        order: String,
        tmxPath: String,
    ): List<TiledMapLayer> {
        val layers = mutableListOf<TiledMapLayer>()
        children.forEach { child ->
            when (child.tagName()) {
                "layer" -> layers.add(parseTiledMapTileLayer(child, columns, rows, order))
                "objectgroup" -> layers.add(parseTiledMapObjectLayer(child, order))
                "imagelayer" -> layers.add(parseTiledMapImageLayer(child, tmxPath))
                "group" -> layers.add(parseTiledMapGroupLayer(child, columns, rows, order, tmxPath))
            }
        }
        return layers
    }

    // --- Tileset Parsing ---

    private suspend fun parseTiledMapSet(element: Element, tmxPath: String): TiledMapSet {
        val id = element.attr("firstgid").toInt()
        val name = element.attr("name")

        val source = element.attr("source")
        val tilesetElement = if (source.isNotEmpty()) {
            val tsxPath = getRelativePath(tmxPath, source)
            val tsxContent = assetsReader.readBytes(tsxPath).decodeToString()
            val tsxDocument = Ksoup.parseXml(tsxContent)
            requireNotNull(tsxDocument.selectFirst("tileset")) {
                "Failed to find <tileset> element in the provided TSX file: $tsxPath"
            }
        } else {
            element
        }

        val imageElement = requireNotNull(tilesetElement.selectFirst("image")) {
            "Tileset $name is missing the <image> tag."
        }

        val imageSource = imageElement.attr("source")
        val tilesetImagePath = getRelativePath(tmxPath, imageSource)

        val imageBytes = assetsReader.readBytes(tilesetImagePath)
        val imageBitmap = imageBytes.decodeToImageBitmap()

        val imageWidth = imageElement.attr("width").toFloatOrNull() ?: imageBitmap.width.toFloat()
        val imageHeight = imageElement.attr("height").toFloatOrNull() ?: imageBitmap.height.toFloat()

        val tileOffsetElement = tilesetElement.selectFirst("tileoffset")
        val offset = tileOffsetElement?.let {
            Offset(it.attr("x").toFloat(), it.attr("y").toFloat())
        } ?: Offset.Zero

        val tileWidth = tilesetElement.attr("tilewidth").toFloatOrNull() ?: 0f
        val tileHeight = tilesetElement.attr("tileheight").toFloatOrNull() ?: 0f
        val spacing = tilesetElement.attr("spacing").toFloatOrNull() ?: 0f
        val margin = tilesetElement.attr("margin").toFloatOrNull() ?: 0f

        val terrains = tilesetElement.select("terraintypes terrain").associate {
            it.attr("name") to it.attr("tile").toInt()
        }

        val tiles = tilesetElement.select("tile").mapNotNull { tileElement ->
            parseTiledMapTile(tileElement)
        }.associateBy { it.id }

        return TiledMapSet(
            firstGid = id,
            name = name,
            tileSize = Size(tileWidth, tileHeight),
            spacing = spacing,
            margin = margin,
            offset = offset,
            size = Size(imageWidth, imageHeight),
            image = imageBitmap,
            terrains = terrains,
            tiles = tiles
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
        rows: Int,
        order: String,
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
                check(compression.isEmpty()) {
                    "CSV encoding does not support compression."
                }
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
            else -> error("Unsupported Tiled data format encoding: $encoding.")
        }

        val finalData = if (order == "right-down") {
            intArrayData
        } else {
            IntArray(columns * rows).apply {
                val isLeft = order.contains("left")
                val isUp = order.contains("up")
                for (y in 0 until rows) {
                    for (x in 0 until columns) {
                        val rawIndex = y * columns + x
                        val targetX = if (isLeft) (columns - 1 - x) else x
                        val targetY = if (isUp) (rows - 1 - y) else y
                        this[targetY * columns + targetX] = intArrayData[rawIndex]
                    }
                }
            }
        }

        check(finalData.size == columns * rows) {
            "Tile layer data size mismatch. Expected ${columns * rows}, got ${finalData.size}"
        }

        return TiledMapTileLayer(
            name = name,
            color = color,
            opacity = opacity,
            visible = visible,
            properties = properties,
            columns = columns,
            rows = rows,
            data = finalData
        )
    }

    // --- Layer Parsing: Object Layer ---

    private fun parseTiledMapObjectLayer(element: Element, order: String): TiledMapObjectLayer {
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

            val x = objElement.attr("x").toFloatOrNull() ?: 0f
            val y = objElement.attr("y").toFloatOrNull() ?: 0f
            val width = objElement.attr("width").toFloatOrNull() ?: Float.NaN
            val height = objElement.attr("height").toFloatOrNull() ?: Float.NaN

            val gid = objElement.attr("gid").toIntOrNull()

            if (gid != null) {
                TiledMapTileObject(
                    id = objId,
                    name = objName,
                    type = objType,
                    visible = objVisible,
                    properties = objProperties,
                    offset = Offset(x, y - height),
                    size = Size(width, height),
                    gid = gid
                )
            } else {
                val shape = when {
                    objElement.selectFirst("polygon") != null -> {
                        val pointsStr = objElement.selectFirst("polygon")?.attr("points") ?: ""
                        val points = pointsStr.split(" ").filter { it.isNotBlank() }.map { pair ->
                            val coords = pair.split(",")
                            Offset(coords[0].toFloat(), coords[1].toFloat())
                        }
                        TiledMapShape.Polygon(points)
                    }
                    objElement.selectFirst("ellipse") != null -> {
                        TiledMapShape.Ellipse(Size(width, height))
                    }
                    objElement.selectFirst("point") != null -> {
                        TiledMapShape.Point
                    }
                    else -> {
                        TiledMapShape.Rectangle(Size(width, height))
                    }
                }
                TiledMapShapeObject(
                    id = objId,
                    name = objName,
                    type = objType,
                    visible = objVisible,
                    properties = objProperties,
                    offset = Offset(x, y),
                    shape = shape
                )
            }
        }

        val sortedObjects = when (order) {
            "right-down", "left-down" -> objects.sortedBy { it.offset.y }
            "right-up", "left-up" -> objects.sortedByDescending { it.offset.y }
            else -> objects
        }

        return TiledMapObjectLayer(
            name = name,
            color = color,
            opacity = opacity,
            visible = visible,
            properties = properties,
            objects = sortedObjects
        )
    }

    // --- Layer Parsing: Image Layer ---

    private suspend fun parseTiledMapImageLayer(element: Element, tmxPath: String): TiledMapImageLayer {
        val name = element.attr("name")
        val color = Color(hex = element.attr("color"), defaultColor = Color.Transparent)
        val opacity = element.attr("opacity").toFloatOrNull() ?: 1f
        val visible = element.attr("visible").let { it.isEmpty() || it != "0" }
        val properties = parseProperties(element.selectFirst("properties"))

        val imageElement = requireNotNull(element.selectFirst("image")) {
            "Image layer '$name' is missing <img> tag."
        }

        val imageSource = imageElement.attr("source")
        val imageBytes = assetsReader.readBytes(getRelativePath(tmxPath, imageSource))
        val imageBitmap = imageBytes.decodeToImageBitmap()

        val x = element.attr("offsetx").toFloatOrNull() ?: 0f
        val y = element.attr("offsety").toFloatOrNull() ?: 0f

        val width = element.attr("width").toFloatOrNull() ?: imageBitmap.width.toFloat()
        val height = element.attr("height").toFloatOrNull() ?: imageBitmap.height.toFloat()

        return TiledMapImageLayer(
            name = name,
            color = color,
            opacity = opacity,
            visible = visible,
            properties = properties,
            image = imageBitmap,
            offset = Offset(x, y),
            size = Size(width, height)
        )
    }

    // --- Layer Parsing: Group Layer (Recursive) ---

    private suspend fun parseTiledMapGroupLayer(
        element: Element,
        columns: Int,
        rows: Int,
        order: String,
        tmxPath: String
    ): TiledMapGroupLayer {
        val name = element.attr("name")
        val color = Color(hex = element.attr("color"), defaultColor = Color.Transparent)
        val opacity = element.attr("opacity").toFloatOrNull() ?: 1f
        val visible = element.attr("visible").let { it.isEmpty() || it != "0" }
        val properties = parseProperties(element.selectFirst("properties"))

        // Recursively parse child layers
        val childLayers = parseLayers(element.children(), columns, rows, order, tmxPath)

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

    private fun ByteArray.readIntLittleEndian(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8) or
                ((this[offset + 2].toInt() and 0xFF) shl 16) or
                ((this[offset + 3].toInt() and 0xFF) shl 24)
    }
}

