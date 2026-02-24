package com.kgame.engine.message

/**
 * Cross-platform message polling hub.
 */
object MessageManager {
    // Array of deques, indexed by the assigned ID
    private var tracks = arrayOfNulls<ArrayDeque<Any>>(128)

    /**
     * Send: O(1) after the first call (map lookup).
     */
    fun <T : Message> send(message: T) {
        val id = MessageRegistry[message::class]

        if (id >= tracks.size) expand(id)
        val track = tracks[id] ?: ArrayDeque<Any>(16).also { tracks[id] = it }
        track.addLast(message)
    }

    /**
     * Poll: Pure O(1) array access using the token's index.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> poll(token: MessageToken<T>): T? {
        val track = tracks.getOrNull(token.id) ?: return null
        return if (track.isNotEmpty()) track.removeFirst() as T else null
    }

    /**
     * Check: Check if messages exist in O(1).
     */
    fun <T : Any> has(token: MessageToken<T>): Boolean {
        return tracks.getOrNull(token.id)?.isNotEmpty() ?: false
    }

    private fun expand(minSize: Int) {
        tracks = tracks.copyOf(minSize + 32)
    }
}