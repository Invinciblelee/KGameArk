package com.kgame.plugins.visuals.images

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kgame.engine.graphics.atlas.AtlasRegion
import com.kgame.engine.graphics.atlas.ImageAtlas
import com.kgame.plugins.visuals.Visual

open class SpriteVisual(
    val atlas: ImageAtlas,
    private var name: String,
    size: Size = Size.Unspecified,
    val aspectMode: AspectMode = AspectMode.Fit
) : Visual() {

    private var region: AtlasRegion = atlas.getRegion(name)

    init {
        preferredSize = size
    }

    override fun onComputeBounds(): Rect {
        val sourceSize = region.sourceSize

        if (!preferredSize.isSpecified) {
            return Rect(Offset.Zero, sourceSize)
        }

        return when (aspectMode) {
            AspectMode.Fill -> {
                Rect(Offset.Zero, preferredSize)
            }
            AspectMode.Fit -> {
                val scaleX = preferredSize.width / sourceSize.width
                val scaleY = preferredSize.height / sourceSize.height
                val scale = minOf(scaleX, scaleY)

                Rect(Offset.Zero, sourceSize * scale)
            }
        }
    }

    fun setFrame(name: String) {
        if (this.name == name) return
        this.name = name
        this.region = atlas.getRegion(name)
    }

    override fun DrawScope.draw() {
        val frame = region.frame
        val source = region.sourceSize
        val trim = region.spriteSourceSize
        val pivot = region.pivot

        val sx = size.width / source.width
        val sy = size.height / source.height

        val anchorX = size.width * pivot.pivotFractionX
        val anchorY = size.height * pivot.pivotFractionY
        val lpX = source.width * pivot.pivotFractionX
        val lpY = source.height * pivot.pivotFractionY

        val srcWidth: Int
        val srcHeight: Int
        val drawWidth: Int
        val drawHeight: Int
        val tx: Float
        val ty: Float

        if (region.rotated) {
            srcWidth = frame.height
            srcHeight = frame.width
            drawWidth = (srcWidth * sy).toInt()
            drawHeight = (srcHeight * sx).toInt()

            ty = (trim.left - lpX) * sx
            tx = -((trim.top - lpY) * sy) - drawWidth
        } else {
            srcWidth = frame.width
            srcHeight = frame.height
            drawWidth = (srcWidth * sx).toInt()
            drawHeight = (srcHeight * sy).toInt()

            tx = (trim.left - lpX) * sx
            ty = (trim.top - lpY) * sy
        }

        withTransform({
            translate(anchorX, anchorY)

            if (region.rotated) {
                rotate(-90f, pivot = Offset.Zero)
            }

            translate(tx, ty)
        }) {
            drawImage(
                image = atlas.bitmap,
                srcOffset = IntOffset(frame.left, frame.top),
                srcSize = IntSize(srcWidth, srcHeight),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(drawWidth, drawHeight),
                alpha = alpha
            )
        }
    }
}
