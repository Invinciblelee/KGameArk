package com.kgame.plugins.systems

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ScaleFactor
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.engine.graphics.drawscope.withLocalTransform
import com.kgame.plugins.components.Axis
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.Scroller
import com.kgame.plugins.components.Transform
import kotlin.math.floor

/**
 * Renders entities with a [Scroller] component, creating a seamless, infinitely repeating
 * background.
 *
 * This system scales the scroller's texture to fit the non-scrolling axis of the viewport
 * and then tiles it along the scrolling axis. It calculates the precise starting position and
 * the required number of tiles to completely fill the screen, adding one extra tile at each
 * end to prevent any visual gaps (tearing) during high-speed movement or frame drops.
 * The entire rendering process is allocation-free (0-GC) within the main loop to ensure
 * optimal performance.
 */
class ScrollerRenderSystem :
    IteratingSystem(family { all(Renderable, Transform, Scroller) }) {

    /**
     * A reusable [Transform] object to avoid allocations within the render loop (0-GC).
     * This transform is configured for each tile before it's drawn.
     */
    private val tileTransform = Transform()

    override fun onRenderEntity(entity: Entity, drawScope: DrawScope) {
        val renderable = entity[Renderable]
        val scrollerTransform = entity[Transform]
        val scroller = entity[Scroller]

        // --- 1. Calculate Scaling and Tile Dimensions ---

        // Determine the scale factor needed to fit the tile to the viewport's non-scrolling axis.
        val scale = when (scroller.axis) {
            Axis.X -> drawScope.size.height / renderable.size.height
            Axis.Y -> drawScope.size.width / renderable.size.width
        }

        // Calculate the actual size of a single tile in the scrolling direction after scaling.
        val scaledTileSize = when (scroller.axis) {
            Axis.X -> renderable.size.width * scale
            Axis.Y -> renderable.size.height * scale
        } - 1f

        // Avoid division by zero if the tile size is invalid.
        if (scaledTileSize <= 0f) return

        // --- 2. Calculate Drawing Start Position and Loop Bounds ---

        // Get the effective camera position along the scrolling axis from the scroller entity's transform.
        val cameraPosition = when (scroller.axis) {
            Axis.X -> scrollerTransform.position.x
            Axis.Y -> scrollerTransform.position.y
        }

        // Get the size of the viewport along the scrolling axis.
        val viewportSize = when (scroller.axis) {
            Axis.X -> drawScope.size.width
            Axis.Y -> drawScope.size.height
        }

        // Use the modulo operator to wrap the camera position within the range of a single tile.
        // This is the key to creating a seamless infinite loop.
        // The `(a % n + n) % n` formula ensures the result is always positive.
        val wrappedOffset = (cameraPosition % scaledTileSize + scaledTileSize) % scaledTileSize

        // Determine the starting position for the first tile to be drawn.
        // We start from the wrapped offset and repeatedly move backward by one tile size
        // until we find the first tile that is just off-screen, ensuring we cover the entire leading edge.
        var currentDrawPos = wrappedOffset
        while (currentDrawPos > -scaledTileSize) {
            currentDrawPos -= scaledTileSize
        }

        // Determine the end position for the loop. We draw until we have completely covered
        // the viewport plus one extra tile, to prevent any gaps on the trailing edge.
        val endDrawPos = viewportSize + scaledTileSize

        // --- 3. Configure Reusable Transform and Render Tiles ---

        // Set the scale and size on the reusable transform object once.
        tileTransform.scale = ScaleFactor(scale, scale)

        // Loop and draw tiles until the entire viewport (plus margin) is covered.
        while (currentDrawPos < endDrawPos) {
            // Set the position for the current tile.
            tileTransform.position = when (scroller.axis) {
                // For horizontal scrolling, Y is centered. For vertical, X is centered.
                Axis.X -> Offset(currentDrawPos, drawScope.size.height / 2f)
                Axis.Y -> Offset(drawScope.size.width / 2f, currentDrawPos)
            }

            // Use withLocalTransform to apply the tile's position, scale, and rotation.
            drawScope.withLocalTransform(tileTransform, renderable.size) {
                with(renderable.visual) { draw() }
            }

            // Advance to the position of the next tile.
            currentDrawPos += scaledTileSize
        }
    }
}
