package com.kgame.engine.utils.zip

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.*
import kotlin.io.use

actual fun ByteArray.compress(compression: Compression): ByteArray {
    val outputStream = ByteArrayOutputStream()
    val compressor = when (compression) {
        Compression.Gzip -> GZIPOutputStream(outputStream)
        Compression.Zlib -> DeflaterOutputStream(outputStream, Deflater(Deflater.DEFAULT_COMPRESSION, true))
    }

    return compressor.use {
        it.write(this)
        it.finish()
        outputStream.toByteArray()
    }
}

actual fun ByteArray.decompress(compression: Compression): ByteArray {
    val inputStream = ByteArrayInputStream(this)
    val decompressionStream = when (compression) {
        Compression.Gzip -> GZIPInputStream(inputStream)
        Compression.Zlib -> InflaterInputStream(inputStream)
    }

    return ByteArrayOutputStream().use { output ->
        decompressionStream.use { input ->
            input.copyTo(output)
        }
        output.toByteArray()
    }
}