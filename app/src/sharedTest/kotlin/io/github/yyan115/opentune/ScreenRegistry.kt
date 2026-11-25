package io.github.yyan115.opentune

/**
 * Every screen, sheet and pop-up of the app, so that none is forgotten. A screen is either rendered in all its states by the catalog of the device tests,
 * or it is named here with the test that reaches it instead. A new screen that is in neither list fails the build until it is listed.
 */
object ScreenRegistry {
    /** The screens that the catalog renders in each of their states, on every setting of the phone that the covering array lists. */
    val rendered = setOf(
        "AddSongsSheet", "AddToPlaylistSheet", "AppDialog", "AppearancePage", "AvoidRepeatsPage", "CreditsSheet", "DetailScreen", "DownloadQueuePage",
        "EqualizerSheet", "LibraryScreen", "LicenceDocumentPage", "LicencesPage", "ListenScreen", "MoveToFolderDialog", "NameDialog", "NewPlaylistDialog",
        "NormalizationDialog", "NowPlayingScreen", "OnDeviceScreen", "PageScreen", "QueueSheet", "ScrobbleDialog", "SearchScreen", "SettingsScreen", "SetupScreen",
        "SleepTimerSheet", "SourceDetailsSheet", "StatisticsPage", "WelcomeScreen",
    )

    /** The screens that the catalog does not render, each with what reaches them and checks them instead. */
    val elsewhere = mapOf(
        "AppSheet" to "It is the frame of the other sheets, and each of them is rendered inside it",
    )
}
