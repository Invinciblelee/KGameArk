package com.example.cmp.ktor.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets

fun Application.installWebSockets(block: WebSockets.WebSocketOptions.() -> Unit = {}) {
    install(WebSockets, block)
}