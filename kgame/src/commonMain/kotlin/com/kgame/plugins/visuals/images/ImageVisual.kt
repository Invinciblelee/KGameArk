package com.kgame.plugins.visuals.images

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.toIntSize
import com.kgame.plugins.visuals.Visual

open class ImageVisual(
    val bitmap: ImageBitmap,
    size: Size = Size.Unspecified,
    val aspectMode: AspectMode = AspectMode.Fit
) : Visual() {

    init {
        preferredSize = size
    }

    override fun onComputeBounds(): Rect {
        val rawSize = Size(bitmap.width.toFloat(), bitmap.height.toFloat())

        if (!preferredSize.isSpecified) {
            return Rect(Offset.Zero, rawSize)
        }

        val finalSize = when (aspectMode) {
            AspectMode.Fill -> {
                preferredSize
            }
            AspectMode.Fit -> {
                val scaleX = preferredSize.width / rawSize.width
                val scaleY = preferredSize.height / rawSize.height
                val scale = minOf(scaleX, scaleY)

                rawSize * scale
            }
        }

        return Rect(Offset.Zero, finalSize)
    }

    override fun DrawScope.draw() {
        drawImage(
            image = bitmap,
            dstSize = bounds.size.toIntSize(),
            alpha = alpha
        )
    }

}