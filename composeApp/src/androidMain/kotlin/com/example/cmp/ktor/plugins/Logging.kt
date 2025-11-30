package com.example.cmp.ktor.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.CallLoggingConfig

fun Application.installCallLogging(block: CallLoggingConfig.() -> Unit = {}) {
    install(CallLogging, block)
}