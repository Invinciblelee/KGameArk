package com.kgame.plugins.visuals.shapes

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import com.kgame.plugins.visuals.Visual
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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