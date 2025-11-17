@file:OptIn(ExperimentalComposeUiApi::class)

package com.example.cmp

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import platform.UIKit.UIColor
import platform.UIKit.UILabel

@Composable
actual fun PlatformView(modifier: Modifier) {
    UIKitView(
        factory = {
            UILabel().apply {
                backgroundColor = UIColor.greenColor
                text = "Hello World!"
                textColor = UIColor.redColor
            }
        },
        modifier = modifier,
        properties = UIKitInteropProperties(
            placedAsOverlay = true
        )
    )
}