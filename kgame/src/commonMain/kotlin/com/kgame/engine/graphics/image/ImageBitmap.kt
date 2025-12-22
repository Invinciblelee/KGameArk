package com.kgame.engine.graphics.image

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.toArgb

/**
 * Filter out a specific color from the ImageBitmap by making it transparent.
 * Uses a BMP-encoding workaround to bypass platform-specific pixel installation limits.
 *
 * @param color The target color to be filtered (made transparent).
 * @return A new ImageBitmap with the filtered pixels, or the original if no changes were made.
 */
fun ImageBitmap.filterColor(color: Color): ImageBitmap {
    val width = this.width
    val height = this.height
    val buffer = IntArray(width * height)

    // 1. Read pixels into the buffer
    this.readPixels(
        buffer = buffer,
        startX = 0,
        startY = 0,
        width = width,
        height = height
    )

    // 2. Prepare target RGB for comparison (ignoring Alpha channel)
    val targetRGB = color.toArgb() and 0x00FFFFFF
    var modified = false

    // 3. Process pixels: replace matching RGB with full transparency
    for (index in buffer.indices) {
        if ((buffer[index] and 0x00FFFFFF) == targetRGB) {
            buffer[index] = 0x00000000 // Set to fully transparent (Alpha = 0)
            modified = true
        }
    }

    // 4. Return original if no pixels were changed to save memory
    if (!modified) return this

    // 5. Wrap processed buffer into BMP ByteArray and re-decode
    // This bypasses the lack of 'installPixels' in some CMP environments
    val bmpBytes = buffer.toBitmapByteArray(width, height)
    return bmpBytes.decodeToImageBitmap()
}


private const val FILE_HEADER_SIZE = 14
private const val INFO_HEADER_SIZE = 108 // BITMAPV4HEADER
private const val BITS_PER_PIXEL = 32
private const val TYPE_BM = 0x4D42
private const val BI_BITFIELDS = 3

fun IntArray.toBitmapByteArray(width: Int, height: Int): ByteArray {
    val pixelDataSize = size * 4
    val fileSize = FILE_HEADER_SIZE + INFO_HEADER_SIZE + pixelDataSize
    val buffer = ByteArray(fileSize)

    // File Header
    buffer.write16(0, TYPE_BM)
    buffer.write32(2, fileSize)
    buffer.write32(10, FILE_HEADER_SIZE + INFO_HEADER_SIZE)

    // Info Header
    buffer.write32(14, INFO_HEADER_SIZE)
    buffer.write32(18, width)
    buffer.write32(22, -height)
    buffer.write16(26, 1)
    buffer.write16(28, BITS_PER_PIXEL)
    buffer.write32(30, BI_BITFIELDS) // Essential for Alpha
    buffer.write32(34, pixelDataSize)

    // RGBA Masks: Defines how to read ARGB data
    buffer.write32(54, 0x00FF0000)       // Red mask
    buffer.write32(58, 0x0000FF00)       // Green mask
    buffer.write32(62, 0x000000FF)       // Blue mask
    buffer.write32(66, 0xFF000000.toInt()) // Alpha mask

    var offset = FILE_HEADER_SIZE + INFO_HEADER_SIZE
    for (color in this) {
        buffer[offset++] = (color and 0xFF).toByte()
        buffer[offset++] = (color shr 8 and 0xFF).toByte()
        buffer[offset++] = (color shr 16 and 0xFF).toByte()
        buffer[offset++] = (color shr 24 and 0xFF).toByte()
    }
    return buffer
}

private fun ByteArray.write16(offset: Int, value: Int) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = (value shr 8 and 0xFF).toByte()
}

private fun ByteArray.write32(offset: Int, value: Int) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = (value shr 8 and 0xFF).toByte()
    this[offset + 2] = (value shr 16 and 0xFF).toByte()
    this[offset + 3] = (value shr 24 and 0xFF).toByte()
}