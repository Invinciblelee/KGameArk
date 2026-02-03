package com.example.kgame

import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
//    jvmApp()

    embeddedServer(Netty, port = 8080) {
        install(CallLogging)

        routing {
            get("/") {
                call.respondText("Hello, Ktor!")
            }
            get("/send") {
                val content = call.queryParameters["content"]
                if (content.isNullOrBlank()) {
                    call.respondText("No content")
                } else {
                    log.debug("Received: $content")

                    call.respondText("Success")
                }
            }
        }
    }.start(wait = true)
}