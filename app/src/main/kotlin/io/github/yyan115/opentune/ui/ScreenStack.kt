package io.github.yyan115.opentune.ui

/**
 * The screens of the app as a stack, and the name of each screen. The screen that is shown is the top of the stack. Everything that a
 * screen remembers, such as how far a list is scrolled, the tab that is chosen and the text that was typed, is kept while the screen is
 * on the stack, so it is still there when a screen above it closes. The root of the app keeps it with `SaveableStateProvider`.
 */
object ScreenStack {
    fun settingsPage(page: SettingsPage) = "settings.${page.name}"

    fun detail(id: String) = "detail:$id"

    /** A page that opened over the others. [depth] is its place on the stack, so the same page twice on the stack has two places. */
    fun page(depth: Int, key: String) = "page:$depth:$key"

    /**
     * The screen that is shown. The order is the order of the choices in the root: the download queue, Settings with its page,
     * the pages that opened over the others, a detail page, and last the main screen that is chosen with the navigation.
     */
    fun top(destination: String, settingsOpen: Boolean, settingsPage: SettingsPage?, downloadsOpen: Boolean, detailId: String?, pageKeys: List<String>): String = when {
        downloadsOpen -> DOWNLOADS
        settingsOpen -> settingsPage?.let(::settingsPage) ?: SETTINGS
        pageKeys.isNotEmpty() -> page(pageKeys.lastIndex, pageKeys.last())
        detailId != null -> detail(detailId)
        else -> destination
    }

    /** Every screen that is on the stack. The four main screens stay for the whole run of the app. */
    fun onStack(destination: String, settingsOpen: Boolean, settingsPage: SettingsPage?, downloadsOpen: Boolean, detailId: String?, pageKeys: List<String>): Set<String> = buildSet {
        addAll(MAIN)
        add(destination)
        if (settingsOpen) {
            add(SETTINGS)
            var page = settingsPage
            while (page != null) { add(settingsPage(page)); page = page.parent }
        }
        if (downloadsOpen) add(DOWNLOADS)
        detailId?.let { add(detail(it)) }
        pageKeys.forEachIndexed { depth, key -> add(page(depth, key)) }
    }

    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"
    private val MAIN = listOf("home", "search", "library", "device")
}
