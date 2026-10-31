package io.github.yyan115.opentune.ui

/** A TIDAL page that can be opened: an API path plus query parameters. */
data class PageRoute(val path: String, val query: Map<String, String> = emptyMap(), val title: String = "") {
    val key: String get() = path + query.entries.sortedBy { it.key }.joinToString("&", if (query.isEmpty()) "" else "?") { "${it.key}=${it.value}" }

    companion object {
        fun artist(id: String, title: String = "") = PageRoute("pages/artist", mapOf("artistId" to id), title)
        fun album(id: String, title: String = "") = PageRoute("pages/album", mapOf("albumId" to id), title)
        fun mix(id: String, title: String = "") = PageRoute("pages/mix", mapOf("mixId" to id), title)
        fun path(path: String, title: String = "") = PageRoute(path, emptyMap(), title)

        val ForYou = path("pages/for_you", "For you")
        val Home = path("pages/home", "Home")
        val Explore = path("pages/explore", "Explore")
    }
}
