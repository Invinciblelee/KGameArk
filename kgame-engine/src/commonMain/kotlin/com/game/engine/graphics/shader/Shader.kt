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
 * Interface to describe shaders supported by the [shaderBackground] Modifier.
 */
interface Shader {

    /**
     * Contains the metadata for this shader
     */
    val metadata: ShaderMetadata
        get() = ShaderMetadata.Default

    /** Defaut time modifier for this shader */
    val speedModifier: Float
        get() = 0.5f

    /** Contains the sksl shader*/
    val sksl: String

    /** Applies the uniforms required for this shader to the effect */
    fun applyUniforms(runtimeEffect: ShaderEffect, time: Float, width: Float, height: Float) {
        runtimeEffect.uniform("uResolution", width, height, width / height)
        runtimeEffect.uniform("uTime", time)
    }

}
