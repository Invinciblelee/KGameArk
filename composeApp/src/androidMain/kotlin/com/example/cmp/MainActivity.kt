package com.example.cmp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.cmp.games.GameEnvironment
import com.example.cmp.ktor.plugins.installCallLogging
import com.example.cmp.ktor.plugins.installContentNegotiation
import com.example.cmp.ktor.plugins.installRouting
import com.example.cmp.ktor.plugins.installWebSockets
import com.example.cmp.utils.getDeviceIpAddress
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val server = GameServer()

    private val environment = GameEnvironment(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(environment)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        server.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
    }
}

class GameServer() {

    private val scope = MainScope()

    private var embeddedServer: EmbeddedServer<*, *>? = null

    fun start() = scope.launch(CoroutineExceptionHandler { _, throwable ->
        Log.e("GameServer", "Failed to start server", throwable)
    }) {
        val host = getDeviceIpAddress()
        embeddedServer = embeddedServer(
            Netty,
            configure = {
                connector {
                    this.host = host
                    this.port = 3344
                }
            },
            module = {
                installWebSockets()
                installCallLogging()
                installContentNegotiation()
                installRouting()
            },
        ).startSuspend()
    }

    fun stop() = scope.launch {
        embeddedServer?.stopSuspend()
    }

}