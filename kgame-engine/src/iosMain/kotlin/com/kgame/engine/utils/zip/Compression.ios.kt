@file:OptIn(ExperimentalForeignApi::class)

package com.kgame.engine.utils.zip

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import platform.zlib.ZLIB_VERSION
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class)
actual fun ByteArray.compress(compression: Compression): ByteArray {
    if (this.isEmpty()) return byteArrayOf()

    return memScoped {
        val strm = alloc<z_stream>()
        val windowBits = if (compression == Compression.Gzip) 31 else 15

        val res = deflateInit2(
            strm.ptr,
            Z_DEFAULT_COMPRESSION,
            Z_DEFLATED,
            windowBits,
            8,
            Z_DEFAULT_STRATEGY
        )
        check(res == Z_OK) { "deflateInit2 failed: $res" }

        val inputPinned = this@compress.pin()
        strm.next_in = inputPinned.addressOf(0).reinterpret()
        strm.avail_in = this@compress.size.toUInt()

        val chunkSize = 4096
        val output = mutableListOf<Byte>()
        val buffer = allocArray<ByteVar>(chunkSize)

        try {
            do {
                strm.next_out = buffer.reinterpret()
                strm.avail_out = chunkSize.toUInt()

                deflate(strm.ptr, Z_FINISH)

                val count = chunkSize - strm.avail_out.toInt()
                for (i in 0 until count) {
                    output.add(buffer[i])
                }
            } while (strm.avail_out == 0.toUInt())
        } finally {
            deflateEnd(strm.ptr)
        }

        output.toByteArray()
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun ByteArray.decompress(compression: Compression): ByteArray {
    if (this.isEmpty()) return byteArrayOf()

    return memScoped {
        val strm = alloc<z_stream>()
        val windowBits = if (compression == Compression.Gzip) 47 else 15

        val res = inflateInit2(strm.ptr, windowBits)
        check(res == Z_OK) { "inflateInit2 failed: $res" }

        val inputPinned = this@decompress.pin()
        strm.next_in = inputPinned.addressOf(0).reinterpret()
        strm.avail_in = this@decompress.size.toUInt()

        val chunkSize = 4096
        val output = mutableListOf<Byte>()
        val buffer = allocArray<ByteVar>(chunkSize)

        try {
            do {
                strm.next_out = buffer.reinterpret()
                strm.avail_out = chunkSize.toUInt()

                val ret = inflate(strm.ptr, Z_NO_FLUSH)
                if (ret == Z_STREAM_END) break
                check(ret == Z_OK) { "Decompression failed: $ret" }

                val count = chunkSize - strm.avail_out.toInt()
                for (i in 0 until count) {
                    output.add(buffer[i])
                }
            } while (strm.avail_out == 0.toUInt())
        } finally {
            inflateEnd(strm.ptr)
        }

        output.toByteArray()
    }
}