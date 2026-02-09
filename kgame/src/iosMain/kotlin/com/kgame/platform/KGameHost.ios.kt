package com.kgame.platform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.compose.ui.uikit.EndEdgePanGestureBehavior
import androidx.compose.ui.window.ComposeUIViewController
import com.kgame.engine.asset.AssetsReader

actual open class KGameHost actual constructor(
    private val assetsReader: AssetsReader
) {

    operator fun invoke(
        configure: ComposeUIViewControllerConfiguration.() -> Unit = {},
        content: @Composable () -> Unit
    ) = ComposeUIViewController(configure = {
        configure()
    }) {
        CompositionLocalProvider(
            LocalPlatformContext provides MockPlatformContext,
            LocalAssetsReader provides assetsReader
        ) {
            content()
        }

        Canvas(modifier = Modifier.fillMaxSize()) {

            val c = this.drawContext.canvas.drawVertices()
            c.drawVertices()
        }
    }

}