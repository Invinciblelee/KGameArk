@file:OptIn(ExperimentalWasmJsInterop::class)

package com.kgame.engine.utils.zip

import js.buffer.ArrayBuffer
import js.typedarrays.Uint8Array

external interface PakoOptions {
    var raw: Boolean?
    var gzip: Boolean?
    var level: Int?
}

@JsName("Object")
external fun createJsObject(): JsAny

@JsModule("pako")
external object Pako {
    fun deflate(data: Uint8Array<ArrayBuffer>, options: PakoOptions): Uint8Array<ArrayBuffer>
    fun inflate(data: Uint8Array<ArrayBuffer>, options: PakoOptions): Uint8Array<ArrayBuffer>
}