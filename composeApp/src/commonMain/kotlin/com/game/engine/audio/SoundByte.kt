package com.game.engine.audio

/**
 * Used to associate a name with a file resource in preparation for [SoundBoard]
 * @param name the title of the audio content
 * @param path the location of the audio file, which can also be a Composable Resource
 */
data class SoundByte(val name: String, val path: String) {

    constructor(path: String): this(path, path)

}