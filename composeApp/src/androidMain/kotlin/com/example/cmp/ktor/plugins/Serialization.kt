package com.example.cmp.ktor.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiationConfig

fun Application.installContentNegotiation(block: ContentNegotiationConfig.() -> Unit = {}) {
    install(ContentNegotiation) {
        json()
        block()
    }
}