@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.kgame

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import kgameark.shared.generated.resources.NotoSansSC_Black
import kgameark.shared.generated.resources.NotoSansSC_Bold
import kgameark.shared.generated.resources.NotoSansSC_ExtraBold
import kgameark.shared.generated.resources.NotoSansSC_ExtraLight
import kgameark.shared.generated.resources.NotoSansSC_Light
import kgameark.shared.generated.resources.NotoSansSC_Medium
import kgameark.shared.generated.resources.NotoSansSC_Regular
import kgameark.shared.generated.resources.NotoSansSC_SemiBold
import kgameark.shared.generated.resources.NotoSansSC_Thin
import kgameark.shared.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

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
                CircularProgressIndicator()
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

    val fonts = fontResources
        .map { (weight, resource) ->
            async(Dispatchers.Default) {
                val bytes = getFontResourceBytes(environment, resource)
                Font(identity = "NotoSansSC_${weight.weight}", data = bytes, weight = weight)
            }
        }
        .awaitAll()

    FontFamily(fonts)
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