package com.kgame.engine.utils.zip

import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array

private fun createJsOptions(): dynamic = js("({})")

actual fun ByteArray.compress(compression: Compression): ByteArray {
    val options: dynamic = createJsOptions()

    when (compression) {
        Compression.Gzip -> options.gzip = true
        Compression.Zlib -> Unit
    }

    val compressed = Pako.deflate(this.toUint8Array(), options)
    return compressed.toByteArray()
}

actual fun ByteArray.decompress(compression: Compression): ByteArray {
    val options: dynamic = createJsOptions()
    val decompressed = Pako.inflate(this.toUint8Array(), options)
    return decompressed.toByteArray()
}