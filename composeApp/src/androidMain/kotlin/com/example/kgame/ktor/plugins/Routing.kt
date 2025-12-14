package com.example.kgame.ktor.plugins

import com.example.kgame.ktor.routes.indexRoute
import io.ktor.server.application.Application
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing

fun Application.installRouting(block: Route.() -> Unit = {}) {
    routing {
        indexRoute()
        block()
    }
}