package com.kgame.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeViewport
import androidx.compose.ui.window.ComposeViewportConfiguration
import com.kgame.engine.asset.AssetsReader

actual open class KGameHost actual constructor(
    private val assetsReader: AssetsReader
) {

    operator fun invoke(
        viewportContainerId: String? = null,
        configure: ComposeViewportConfiguration.() -> Unit = {},
        content: @Composable () -> Unit = { }
    ) {
        ComposeViewport(
            viewportContainerId = viewportContainerId,
            configure = configure
        ) {
            CompositionLocalProvider(
                LocalPlatformContext provides PlatformContext,
                LocalAssetsReader provides assetsReader
            ) {
                content()
            }
        }
    }

}
