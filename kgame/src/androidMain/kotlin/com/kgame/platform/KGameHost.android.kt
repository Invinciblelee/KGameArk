package com.kgame.platform

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.kgame.engine.asset.AssetsReader

actual open class KGameHost actual constructor(
    private val assetsReader: AssetsReader
) {

    operator fun invoke(activity: ComponentActivity, content: @Composable () -> Unit) {
        activity.enableEdgeToEdge()
        activity.setContent {
            CompositionLocalProvider(
                LocalPlatformContext provides activity,
                LocalAssetsReader provides assetsReader
            ) {
                content()
            }
        }
    }

}

