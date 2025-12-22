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
        try {
            strm.next_in = inputPinned.addressOf(0).reinterpret()
            strm.avail_in = this@compress.size.toUInt()

            val chunkSize = 4096
            val buffer = allocArray<ByteVar>(chunkSize)
            val output = mutableListOf<Byte>()

            var ret: Int
            do {
                strm.next_out = buffer.reinterpret()
                strm.avail_out = chunkSize.toUInt()

                // 使用 Z_FINISH 告诉 zlib 这是最后的数据块
                ret = deflate(strm.ptr, Z_FINISH)

                val count = chunkSize - strm.avail_out.toInt()
                if (count > 0) {
                    for (i in 0 until count) {
                        output.add(buffer[i])
                    }
                }
            } while (ret != Z_STREAM_END)

            output.toByteArray()
        } finally {
            inputPinned.unpin()
            deflateEnd(strm.ptr)
        }
    }
}

actual fun ByteArray.decompress(compression: Compression): ByteArray {
    if (this.isEmpty()) return byteArrayOf()

    return memScoped {
        val strm = alloc<z_stream>()
        val windowBits = if (compression == Compression.Gzip) 47 else 15

        if (inflateInit2(strm.ptr, windowBits) != Z_OK) return byteArrayOf()

        val inputPinned = this@decompress.pin()
        try {
            strm.next_in = inputPinned.addressOf(0).reinterpret()
            strm.avail_in = this@decompress.size.toUInt()

            val chunkSize = 4096
            val buffer = allocArray<ByteVar>(chunkSize)
            val output = mutableListOf<Byte>()

            while (true) {
                strm.next_out = buffer.reinterpret()
                strm.avail_out = chunkSize.toUInt()

                val ret = inflate(strm.ptr, Z_NO_FLUSH)

                val decompressedInThisRound = chunkSize - strm.avail_out.toInt()
                if (decompressedInThisRound > 0) {
                    for (i in 0 until decompressedInThisRound) {
                        output.add(buffer[i])
                    }
                }

                if (ret == Z_STREAM_END) break
                if (ret != Z_OK) break
            }
            output.toByteArray()
        } finally {
            inputPinned.unpin()
            inflateEnd(strm.ptr)
        }
    }
}