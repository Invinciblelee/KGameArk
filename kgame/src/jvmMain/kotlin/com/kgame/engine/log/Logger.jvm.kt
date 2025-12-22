package com.kgame.engine.log

actual object Logger {
    actual fun debug(tag: String, message: String) {
        println("[DEBUG] $tag: $message")
    }

    actual fun info(tag: String, message: String) {
        println("[INFO] $tag: $message")
    }

    actual fun warn(tag: String, message: String) {
        println("[WARN] $tag: $message")
    }

    actual fun error(tag: String, message: String, throwable: Throwable?) {
        System.err.println("[ERROR] $tag: $message ${throwable?.toString().orEmpty()}")
    }
}