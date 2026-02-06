package com.kgame.platform

actual abstract class PlatformContext actual constructor()

actual val MockPlatformContext: PlatformContext = object : PlatformContext() {}