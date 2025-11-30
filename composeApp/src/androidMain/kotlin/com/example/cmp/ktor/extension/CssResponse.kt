package com.example.cmp.ktor.extension

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.css.CssBuilder
import kotlinx.css.RuleContainer

suspend inline fun ApplicationCall.respondCss(
    indent: String = "",
    allowClasses: Boolean = true,
    parent: RuleContainer? = null,
    isHolder: Boolean = false,
    isStyledComponent: Boolean = false,
    builder: CssBuilder.() -> Unit
) {
    this.respondText(CssBuilder(indent, allowClasses, parent, isHolder, isStyledComponent).apply(builder).toString(), ContentType.Text.CSS)
}