@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalWasmJsInterop::class)

package com.example.kgame

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import kgameark.sample.generated.resources.NotoSansSC_Black
import kgameark.sample.generated.resources.NotoSansSC_Bold
import kgameark.sample.generated.resources.NotoSansSC_ExtraBold
import kgameark.sample.generated.resources.NotoSansSC_ExtraLight
import kgameark.sample.generated.resources.NotoSansSC_Light
import kgameark.sample.generated.resources.NotoSansSC_Medium
import kgameark.sample.generated.resources.NotoSansSC_Regular
import kgameark.sample.generated.resources.NotoSansSC_SemiBold
import kgameark.sample.generated.resources.NotoSansSC_Thin
import kgameark.sample.generated.resources.Res
import kotlinx.browser.window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsName
import kotlin.js.Promise
import kotlin.js.js

@Composable
actual fun AppTheme(content: @Composable () -> Unit) {
    var fontFamily by remember { mutableStateOf<FontFamily?>(null) }

    LaunchedEffect(Unit) {
        fontFamily = loadFontFamily()
    }

    AnimatedContent(
        targetState = fontFamily,
        transitionSpec = { fadeIn() togetherWith fadeOut() }
    ) { family ->
        if (family != null) {
            MaterialExpressiveTheme(
                typography = createTypography(family),
                content = content
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }
    }
}


private suspend fun loadFontFamily(): FontFamily = coroutineScope {
    val environment = getSystemResourceEnvironment()

    val fontResources = mapOf(
        FontWeight.Thin to Res.font.NotoSansSC_Thin,
        FontWeight.ExtraLight to Res.font.NotoSansSC_ExtraLight,
        FontWeight.Light to Res.font.NotoSansSC_Light,
        FontWeight.Normal to Res.font.NotoSansSC_Regular,
        FontWeight.Medium to Res.font.NotoSansSC_Medium,
        FontWeight.SemiBold to Res.font.NotoSansSC_SemiBold,
        FontWeight.Bold to Res.font.NotoSansSC_Bold,
        FontWeight.ExtraBold to Res.font.NotoSansSC_ExtraBold,
        FontWeight.Black to Res.font.NotoSansSC_Black
    )

    val fonts: List<Font> = fontResources
        .map { (weight, resource) ->
          async(Dispatchers.Default) {
                val bytes = getFontResourceBytes(environment, resource)
              Font(
                  identity = "System-${weight.weight}",
                  data = bytes,
                  weight = weight,
                  variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
              )
            }
        }
        .awaitAll()

    if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
}

private fun createTypography(fontFamily: FontFamily): Typography {
    val defaultTypography = Typography()
    return Typography(
        displayLarge = defaultTypography.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = defaultTypography.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = defaultTypography.displaySmall.copy(fontFamily = fontFamily),

        headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = fontFamily),

        titleLarge = defaultTypography.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = defaultTypography.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = defaultTypography.titleSmall.copy(fontFamily = fontFamily),

        bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = defaultTypography.bodySmall.copy(fontFamily = fontFamily),

        labelLarge = defaultTypography.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = defaultTypography.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = defaultTypography.labelSmall.copy(fontFamily = fontFamily)
    )
}





/**
 * Enterprise-grade external JavaScript interface mirroring the Vessel Container injection layer.
 * Stripped of the redundant 'window.' prefix to adhere to runtime signature evaluation standards.
 */
@JsName("VesselBridge")
external object VesselBridge {

    /**
     * Dispatches an asynchronous message down the unified container transport pipeline.
     *
     * @param identifier The target plugin's unique routing identity string (e.g., "logger").
     * @param payload A raw JSON/JavaScript data object containing key-value parameters.
     * @return A native JavaScript Promise that resolves or rejects based on the VesselResponder's terminal lifecycles.
     */
    fun callAsync(identifier: String, payload: JsAny): Promise<JsAny>
}

/**
 * 💡 CRITICAL FIX FOR KOTLIN/WASM:
 * Converts a regular Kotlin string into a dynamic JavaScript object wrapper inside a strict
 * top-level single-expression body function to satisfy compiler verification constraints.
 *
 * @param text The literal content to be embedded into the dynamic payload context.
 * @return A compiled JavaScript literal object frame targeting the 'payload' parameter layer.
 */
fun createLoggingPayload(text: String): JsAny = js("({ message: text })")