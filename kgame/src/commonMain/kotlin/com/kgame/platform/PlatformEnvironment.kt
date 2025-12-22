package com.kgame.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.kgame.engine.asset.AssetsReader

val LocalAssetsReader = staticCompositionLocalOf<AssetsReader> {
    error("AssetsReader not provided.")
}

val LocalPlatformContext = staticCompositionLocalOf<PlatformContext> {
    error("PlatformContext not provided.")
}