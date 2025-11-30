package com.example.cmp.ktor.routes

import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.ButtonType
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.onClick
import kotlinx.html.script
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe

internal fun Route.indexRoute() {
    staticResources("/static", basePackage = "static")
    staticResources("/", basePackage = "productionExecutable")

    get("/welcome") {
        call.respondHtml {
            buildWelcomeHtml()
        }
    }

    get("/test") {
        call.respondHtml {
            buildIndexHtml()
        }
    }
}

/**
 * Builds a welcome page containing a large "Enter Game" button.
 */
private fun HTML.buildWelcomeHtml() {
    head {
        title("Welcome")
        // Add viewport meta tag to ensure proper scaling on mobile devices
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")

        // Inline CSS to center the button and style it
        style {
            unsafe {
                +"""
                    html, body {
                        height: 100%;
                        margin: 0;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        background-color: #282c34;
                    }
                    .game-button {
                        padding: 20px 40px;
                        font-size: 24px;
                        font-weight: bold;
                        color: white;
                        background-color: #61dafb;
                        border: none;
                        border-radius: 10px;
                        cursor: pointer;
                        transition: background-color 0.3s ease;
                    }
                    .game-button:hover {
                        background-color: #21a1f1;
                    }
                """
            }
        }
    }
    body {
        // Create the button with the CSS class and the onClick event
        button(classes = "game-button", type = ButtonType.button) {
            // Set the onClick event to redirect the page using JavaScript
            onClick = "window.location.href='/'"
            +"Enter Game"
        }
    }
}

private fun HTML.buildIndexHtml() {
    head {
        link(rel = "stylesheet", href = "static/css/index_style.css", type = "text/css")
        link(rel = "icon", href = "static/image/favicon.ico", type = "image/x-icon")
        script {
            type = "text/javascript"
            src = "static/script/index_script.js"
        }
    }
    body {
        div(classes = "title") {
            h1 {
                +"展之辰"
            }
        }

        div(classes = "container") {
            input(classes = "box", type = InputType.text) {
                id = "host"
                placeholder = "IP地址"
            }
            input(classes = "box", type = InputType.number) {
                id = "port"
                placeholder = "端口"
            }
            input(classes = "box", type = InputType.text) {
                id = "content"
                placeholder = "内容"
            }
            input(classes = "box", type = InputType.button) {
                id = "submit"
                value = "发送"
                onClick = "submit()"
            }
        }
    }
}

