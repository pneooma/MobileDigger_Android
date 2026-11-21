package com.example.mobiledigger.audio

/**
 * The playlists as the car / Bluetooth sees them. The ViewModel publishes here; MusicService turns
 * it into the session's "Now playing" queue and a browsable To Do / Liked / Rejected library.
 * Same process, so a plain object is enough.
 */
object MediaLibrary {
    data class Track(val uri: String, val name: String)

    /** Tab ids match PlaylistTab names. */
    const val TODO = "TODO"
    const val LIKED = "LIKED"
    const val REJECTED = "REJECTED"
    val TABS = listOf(TODO to "To Do", LIKED to "Liked", REJECTED to "Rejected")

    @Volatile var lists: Map<String, List<Track>> = emptyMap(); private set
    @Volatile var currentTab: String = TODO; private set
    @Volatile var currentUri: String? = null; private set

    /** Called on the main thread after every change. */
    @Volatile var listener: (() -> Unit)? = null

    fun update(lists: Map<String, List<Track>>, currentTab: String, currentUri: String?) {
        this.lists = lists
        this.currentTab = currentTab
        this.currentUri = currentUri
        listener?.invoke()
    }

    /** "TAB|uri" — what the car sends back when you pick a track. */
    fun mediaId(tab: String, uri: String) = "$tab|$uri"
    fun parse(mediaId: String): Pair<String, String>? {
        val i = mediaId.indexOf('|')
        if (i <= 0) return null
        return mediaId.substring(0, i) to mediaId.substring(i + 1)
    }
}
