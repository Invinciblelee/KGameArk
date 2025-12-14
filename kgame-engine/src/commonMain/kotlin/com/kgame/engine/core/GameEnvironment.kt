package com.kgame.engine.core

import androidx.compose.runtime.Immutable
import com.kgame.engine.asset.AssetsReader


@Immutable
class GameEnvironment(
    val context: PlatformContext,
    val assetsReader: AssetsReader
)

/**
 * The context of the platform.
 */
expect abstract class PlatformContext()

