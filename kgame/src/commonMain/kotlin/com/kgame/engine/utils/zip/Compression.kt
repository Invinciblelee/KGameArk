package com.kgame.engine.utils.zip

enum class Compression {
    Gzip, Zlib;

    companion object {
        operator fun get(type: String): Compression {
            return when {
                type.equals("gzip", ignoreCase = true) -> Gzip
                type.equals("zlib", ignoreCase = true) -> Zlib
                else -> error("Unsupported compression type: $type")
            }
        }
    }
}

expect fun ByteArray.compress(compression: Compression): ByteArray

expect fun ByteArray.decompress(compression: Compression): ByteArray