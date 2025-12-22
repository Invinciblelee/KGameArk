package com.kgame.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.application
import com.kgame.engine.asset.AssetsReader

actual open class KGameHost actual constructor(
    private val assetsReader: AssetsReader
) {

    operator fun invoke(
        exitProcessOnExit: Boolean = true,
        content: @Composable ApplicationScope.() -> Unit
    ) {
        application(exitProcessOnExit = exitProcessOnExit) {
            CompositionLocalProvider(
                LocalPlatformContext provides PlatformContext,
                LocalAssetsReader provides assetsReader
            ) {
                content()
            }
        }
    }

}
