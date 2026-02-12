package com.kgame.engine.graphics.material

data class MaterialMetadata(
    /** The name for this shader. */
    val name: String,
    /** Contains the author name who created the shader. */
    val authorName: String = "",
    /** Contains the url to the author reference. */
    val authorUrl: String = "",
    /** Contains the url to the source of this shader. */
    val credit: String = "",
    /** Contains the name of the license for this shader. */
    val license: String = "",
    /** Contains the url to the license reference. */
    val licenseUrl: String = ""
) {

    companion object Companion {
        val Default = MaterialMetadata("Default")
    }

}

/**
 * Interface to describe shaders supported by the [com.kgame.engine.ui.MaterialCanvas] Modifier.
 */
interface Material {

    companion object Companion {
        const val RESOLUTION = "uResolution"
        const val TIME = "uTime"
        const val DURATION = "uDuration"
        const val DELTA_TIME = "uDeltaTime"

        const val COLOR = "uColor"
        const val COLORS = "uColors"
    }

    /**
     * Contains the metadata for this shader
     */
    val metadata: MaterialMetadata
        get() = MaterialMetadata.Default

    /** Default time modifier for this shader */
    val speedModifier: Float
        get() = 1f

    /** Contains the sksl shader*/
    val sksl: String

    /** Applies the uniforms required for this shader to the effect */
    fun MaterialEffect.applyUniforms() = Unit

}

private class SimpleMaterial(override val sksl: String): Material

fun Material(sksl: String): Material = SimpleMaterial(sksl)