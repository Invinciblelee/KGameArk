package com.game.engine.core

import androidx.compose.runtime.Immutable
import com.game.engine.asset.AssetsReader


@Immutable
class GameEnvironment(
    val context: PlatformContext,
    val assetsReader: AssetsReader
)

/**
 * The context of the platform.
 */
expect abstract class PlatformContext()

