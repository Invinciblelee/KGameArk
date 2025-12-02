package com.game.engine.graphics.shader

data class ShaderMetadata(
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

    companion object {
        val Default = ShaderMetadata("Default")
    }

}

/**
 * Interface to describe shaders supported by the [com.game.engine.ui.ShaderCanvas] Modifier.
 */
interface Shader {

    companion object {
        const val RESOLUTION = "uResolution"
        const val TIME = "uTime"

        const val COLOR = "uColor"
        const val COLORS = "uColors"
    }

    /**
     * Contains the metadata for this shader
     */
    val metadata: ShaderMetadata
        get() = ShaderMetadata.Default

    /** Default time modifier for this shader */
    val speedModifier: Float
        get() = 1f

    /** Contains the sksl shader*/
    val sksl: String

    /** Applies the uniforms required for this shader to the effect */
    fun ShaderEffect.applyUniforms() = Unit

}
