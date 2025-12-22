@file:OptIn(ExperimentalWasmJsInterop::class)

package com.kgame.engine.utils.zip

import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array

private fun createPakoOptions(): PakoOptions = createJsObject().unsafeCast()

actual fun ByteArray.compress(compression: Compression): ByteArray {
    val options = createPakoOptions()
    when (compression) {
        Compression.Gzip -> options.gzip = true
        Compression.Zlib -> Unit
    }
    val compressed = Pako.deflate(this.toUint8Array(), options)
    return compressed.toByteArray()
}

actual fun ByteArray.decompress(compression: Compression): ByteArray {
    val options = createPakoOptions()
    val decompressed = Pako.inflate(this.toUint8Array(), options)
    return decompressed.toByteArray()
}