package com.kgame.plugins.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntRect
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.engine.graphics.atlas.AtlasRegion
import com.kgame.engine.graphics.atlas.ImageAtlas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

abstract class Visual {

    constructor()

    constructor(size: Size) {
        this.preferredSize = size
    }

    constructor(size: Float): this(Size(size, size))

    constructor(width: Float, height: Float): this(Size(width, height))

    private var isReady = false

    /**
     * The desired dimensions for this visual.
     * If [Size.Unspecified], the visual should compute its layout based on intrinsic content.
     */
    var preferredSize: Size = Size.Unspecified
        set(value) {
            val changed = field != value
            if (changed) field = value
            if (changed || !isReady) {
                isReady = true
                invalidateBounds()
            }
        }

    private var _bounds: Rect = InfiniteRect
    /**
     * The visual bounding box in local coordinate space.
     * Defaults to [InfiniteRect] to represent unconstrained spatial volume.
     */
    val bounds: Rect get() = _bounds

    /**
     * The resolved pixel dimensions derived from the latest geometry pass.
     * Note: Accessing this when bounds is [InfiniteRect] may yield infinite dimensions.
     */
    val size: Size get() = _bounds.size

    open var alpha: Float = 1.0f

    /**
     * Resolves the final layout bounds.
     * Defaults to [InfiniteRect] if [preferredSize] is not specified.
     */
    protected open fun onComputeBounds(): Rect {
        return if (preferredSize.isSpecified) {
            Rect(Offset.Zero, preferredSize)
        } else {
            InfiniteRect
        }
    }

    /**
     * Synchronizes the internal bounds.
     * Forces a re-calculation of all cached spatial properties.
     */
    protected fun invalidateBounds() {
        _bounds = onComputeBounds()
    }

    /**
     * Renders the visual content into the provided [DrawScope].
     */
    abstract fun DrawScope.draw()

    companion object {
        /**
         * A sentinel value representing an unbounded rectangular area.
         */
        protected val InfiniteRect = Rect(
            Float.NEGATIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY
        )
    }
}


open class CircleVisual(
    var color: Color,
    size: Float,
    val style: DrawStyle = Fill
) : Visual(size) {

    override fun DrawScope.draw() {
        drawCircle(color, style = style, alpha = alpha)
    }

}

open class RectangleVisual(
    var color: Color,
    size: Size,
    val cornerRadius: CornerRadius = CornerRadius.Zero,
    val style: DrawStyle = Fill,
) : Visual(size) {

    override fun DrawScope.draw() {
        if (cornerRadius.isZero()) {
            drawRect(color, style = style, alpha = alpha)
        } else {
            drawRoundRect(color, cornerRadius = cornerRadius, style = style, alpha = alpha)
        }
    }
}

open class PolygonVisual(
    var color: Color,
    size: Size,
    val sides: Int,
    val style: DrawStyle = Fill,
) : Visual(size) {

    constructor(
        color: Color,
        size: Float,
        sides: Int,
        style: DrawStyle = Fill
    ) : this(
        color = color,
        size = Size(size, size),
        sides = sides,
        style = style
    )

    init {
        require(sides >= 3) { "A polygon must have at least 3 sides." }
    }

    private val path = Path()

    override fun DrawScope.draw() {
        toPolygonPath(path, sides, size.width, size.height)

        drawPath(path = path, color = color, style = style, alpha = alpha)
    }

    companion object {

        /**
         * Modifies the given Path object to form a regular polygon inscribed in an ellipse.
         * This function is allocation-free regarding collections.
         *
         * @param path The Path object to modify. It should be reset before calling.
         * @param sides The number of sides for the polygon.
         * @param width The width of the imaginary ellipse a polygon is inscribed in.
         * @param height The height of the imaginary ellipse.
         */
        fun toPolygonPath(path: Path, sides: Int, width: Float, height: Float) {
            require(sides >= 3) { "A polygon must have at least 3 sides, but $sides were requested." }
            require(width > 0f && height > 0f) { "Width and height must be positive." }
            val radiusX = width / 2f
            val radiusY = height / 2f
            val angleStep = 2.0 * PI / sides
            var angle = -(PI / 2.0)

            path.reset()

            path.moveTo(
                (radiusX * cos(angle)).toFloat() + radiusX,
                (radiusY * sin(angle)).toFloat() + radiusY
            )

            var count = sides - 1
            while (count > 0) {
                angle += angleStep
                path.lineTo(
                    (radiusX * cos(angle)).toFloat() + radiusX,
                    (radiusY * sin(angle)).toFloat() + radiusY
                )
                count--
            }

            path.close()
        }

    }

}

open class ImageVisual(
    val bitmap: ImageBitmap,
    size: Size = Size.Unspecified
) : Visual() {

    init {
        preferredSize = size
    }

    override fun onComputeBounds(): Rect {
        val effectiveSize = if (preferredSize.isSpecified) preferredSize else Size(bitmap.width.toFloat(), bitmap.height.toFloat())
        return Rect(Offset.Zero, effectiveSize)
    }

    override fun DrawScope.draw() {
        drawImage(
            image = bitmap,
            dstSize = bounds.size.toIntSize(),
            alpha = alpha
        )
    }

}

open class SpriteVisual(
    val atlas: ImageAtlas,
    private var name: String,
    size: Size = Size.Unspecified
) : Visual() {

    private var region: AtlasRegion = atlas.getRegion(name)

    init {
        preferredSize = size
    }

    override fun onComputeBounds(): Rect {
        val effectiveSize = if (preferredSize.isSpecified) preferredSize else region.size.toSize()
        return Rect(Offset.Zero, effectiveSize)
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

        // 1. 计算缩放比
        val sx = size.width / source.width
        val sy = size.height / source.height

        // 2. 锚点 (Canvas 上的固定位置) 与 逻辑 Pivot (原图上的像素位置)
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
            // 物理采样：宽高互换
            srcWidth = frame.height
            srcHeight = frame.width
            // 视觉尺寸：保持逻辑宽高一致
            drawWidth = (srcWidth * sy).toInt()
            drawHeight = (srcHeight * sx).toInt()

            // --- 核心修复逻辑 ---
            // 左右对齐 (已经稳了)：(trim.left - lpX) * sx
            ty = (trim.left - lpX) * sx

            // 上下对齐：在旋转坐标系中，tx 对应逻辑 Y 轴。
            // 由于图集是顺时针旋转 90 度存放，转回来后需要补偿物理高度 (即旋转前的 frame.width)
            tx = (trim.top - lpY + frame.width) * sy
            // 如果依然上下闪，请将上面一行改为：tx = (trim.top - lpY + frame.width) * sy
        } else {
            srcWidth = frame.width
            srcHeight = frame.height
            drawWidth = (srcWidth * sx).toInt()
            drawHeight = (srcHeight * sy).toInt()

            // 正常帧的偏移
            tx = (trim.left - lpX) * sx
            ty = (trim.top - lpY) * sy
        }

        val state = if (region.rotated) "ROTATED" else "NORMAL"
        println("""
    [$state] 
    Source: (${source.width}x${source.height}), Frame: (${frame.width}x${frame.height})
    Trim: (L=${trim.left}, T=${trim.top}), Pivot: (lpX=$lpX, lpY=$lpY)
    DrawSize: (${drawWidth}x${drawHeight}), Scale: (sx=$sx, sy=$sy)
    Final Offset -> tx: $tx, ty: $ty
    ------------------------------------------
""".trimIndent())
        withTransform({
            // A. 移到锚点
            translate(anchorX, anchorY)

            // B. 如果是旋转帧，先转坐标轴
            if (region.rotated) {
                rotate(-90f, pivot = Offset.Zero)
            }

            // C. 应用逻辑偏移
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

/**
 * A component of Sprite.
 * @param visual The visual of the sprite.
 * @param zIndex The z-index of the sprite.
 * @param isVisible Whether the sprite is visible.
 */
data class Renderable(
    val visual: Visual,
    var zIndex: Int = 0,
    var isVisible: Boolean = true
) : Component<Renderable>, Comparable<Renderable> {
    override fun type() = Renderable

    companion object : ComponentType<Renderable>()

    override fun compareTo(other: Renderable): Int {
        return zIndex.compareTo(other.zIndex)
    }

    /**
     * Checks if the renderable is visible and has an alpha greater than 0.
     */
    val isShowing: Boolean
        get() = isVisible && visual.alpha > 0f

    /**
     * Checks if the renderable is not visible or has an alpha less than or equal to 0.
     */
    val isHiding: Boolean
        get() = !isVisible || visual.alpha <= 0f


    /**
     * Returns the size of [visual]
     */
    val size: Size
        get() = visual.size

    /**
     * Returns the bounds of [visual]
     */
    val bounds: Rect
        get() = visual.bounds

    /**
     * Returns the alpha of [visual]
     */
    val alpha: Float
        get() = visual.alpha

}


/**
 * Applies a new alpha to the renderable's visual.
 * @param alpha The new alpha value.
 */
fun Renderable.applyAlpha(alpha: Float) {
    visual.alpha = alpha
}

/**
 * Applies a new alpha to the renderable's visual.
 * @param fromAlpha The starting alpha value.
 * @param toAlpha The ending alpha value.
 * @param fraction The fraction of the transition.
 */
fun Renderable.applyAlpha(
    fromAlpha: Float,
    toAlpha: Float,
    fraction: Float
) {
    visual.alpha = lerp(fromAlpha, toAlpha, fraction)
}

/**
 * Applies a new size to the renderable's visual.
 */
fun Renderable.applySize(size: Size) {
    visual.preferredSize = size
}

/**
 * Applies a new size to the renderable's visual.
 */
fun Renderable.applySize(width: Float, height: Float) {
    visual.preferredSize = Size(width, height)
}