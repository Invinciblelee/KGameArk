package com.kgame.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.compose.ui.window.ComposeUIViewController
import com.kgame.engine.asset.AssetsReader

actual open class KGameHost actual constructor(
    private val assetsReader: AssetsReader
) {

    operator fun invoke(
        configure: ComposeUIViewControllerConfiguration.() -> Unit = {},
        content: @Composable () -> Unit
    ) = ComposeUIViewController(configure = configure) {
        CompositionLocalProvider(
            LocalPlatformContext provides PlatformContext,
            LocalAssetsReader provides assetsReader
        ) {
            content()
        }
    }

}