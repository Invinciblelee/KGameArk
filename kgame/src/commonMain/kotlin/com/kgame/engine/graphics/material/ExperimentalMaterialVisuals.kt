package com.kgame.engine.graphics.material

/**
 * Visual Consistency Warning:
 * Marks a Material that will undergo significant visual degradation on Android versions
 * below 13 (API 33).
 *
 * **Degradation Behavior:**
 * 1. The material will fallback to a [androidx.compose.ui.graphics.SolidColor] rectangle based on [Material.COLOR].
 * 2. If your Shader renders a shape significantly larger than the particle's geometric
 * size (e.g., a large-scale shockwave), it will appear as a **solid, harsh rectangle** * on legacy devices, leading to a poor user experience.
 *
 * **Design Recommendations:**
 * - This material is best suited for "Texture Enhancement" particles (e.g., glowing sparks).
 * - For "Shape-Defining" materials (e.g., ring-shaped shockwaves), consider using
 * fallback textures or reducing particle size on legacy devices.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "Significant visual degradation on Android < 13. " +
            "Renders as a solid rectangle. Verify particle size vs shader area."
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalMaterialVisuals