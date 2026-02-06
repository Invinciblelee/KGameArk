package com.kgame.platform

import android.content.Context
import android.content.ContextWrapper

actual typealias PlatformContext = Context

actual val MockPlatformContext: PlatformContext
    get() = ContextWrapper(null)