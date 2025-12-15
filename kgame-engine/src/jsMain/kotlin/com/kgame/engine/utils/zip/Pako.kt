package com.kgame.engine.utils.zip

import js.buffer.ArrayBuffer
import js.typedarrays.Uint8Array

@JsModule("pako")
@JsNonModule
external object Pako {
    fun deflate(data: Uint8Array<ArrayBuffer>, options: dynamic): Uint8Array<ArrayBuffer>
    fun inflate(data: Uint8Array<ArrayBuffer>, options: dynamic): Uint8Array<ArrayBuffer>
}