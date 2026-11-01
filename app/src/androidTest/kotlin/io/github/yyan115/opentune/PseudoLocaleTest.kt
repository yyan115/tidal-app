package io.github.yyan115.opentune

import android.app.LocaleManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the app in the en-XA pseudo-language, which wraps every string from a resource in square brackets and accents its letters.
 * Text that is still readable as plain English on a screen was written in code and cannot be translated.
 * Titles of tracks and playlists the names of audio formats and the address of the source code come from the data, so the test lists them as known.
 */
@UnstableApi
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
@RunWith(AndroidJUnit4::class)
class PseudoLocaleTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity
    private lateinit var model: MainViewModel
    private lateinit var track: Track
    private val offenders = mutableListOf<String>()
    private val data = setOf("Night Walk", "Playback test", "Local audio checks", "Evening", "Night", "FLAC", "AAC", "github.com/yyan115/tidal-app")

    @Before fun startInPseudoLanguage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context as OpenTuneApp
        app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-XA")
        val deadline = System.currentTimeMillis() + 10_000
        while (app.resources.configuration.locales[0].toLanguageTag() != "en-XA" && System.currentTimeMillis() < deadline) Thread.sleep(100)
        val file = File(app.filesDir, "playback-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> file.outputStream().use(input::copyTo) }
        track = Track("local:playback-test", "Night Walk", "Playback test", "Local audio checks", durationMs = 30_000,
            localUri = Uri.fromFile(file).toString(), quality = "FLAC · 24-bit · 96 kHz")
        // Other tests leave titles in the library. They are data, so start from a library that holds only this test's data.
        app.library.localTracks().forEach { app.library.removeLocal(it.id) }
        app.library.playlists().forEach { app.library.deletePlaylist(it.id) }
        app.library.save(track)
        app.library.createPlaylist("Evening")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity = it }
        model = ViewModelProvider(activity)[MainViewModel::class.java]
        compose.runOnIdle {
            model.clearQueue()
            model.settings { it.copy(theme = "light", colorTheme = "studio", offline = false) }
        }
        await("the track in the library") { model.state.value.local.any { it.id == track.id } }
        assertEquals("The test needs the pseudo-language", "en-XA", activity.resources.configuration.locales[0].toLanguageTag())
    }

    @After fun restoreLanguage() {
        scenario.close()
        val app = ApplicationProvider.getApplicationContext<Context>() as OpenTuneApp
        app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
        app.library.playlists().filter { it.title == "Evening" }.forEach { app.library.deletePlaylist(it.id) }
    }

    private fun s(@StringRes id: Int, vararg args: Any): String = activity.getString(id, *args)

    @Test fun everyScreenTextComesFromResources() {
        assertTrue("The resource text must carry the pseudo-language marks", s(R.string.nav_listen).startsWith("["))
        sweep("Listen")
        compose.onNodeWithTag("nav-1").performClick()
        sweep("Search")
        compose.runOnIdle { model.search("Night") }
        await("the search to show the track") { model.state.value.local.any { it.id == track.id } }
        sweep("Search results")
        for (tab in listOf(R.string.label_tracks, R.string.label_albums, R.string.label_artists, R.string.label_playlists, R.string.label_videos)) {
            compose.onAllNodes(hasText(s(tab)) and hasClickAction()).onFirst().performClick()
            sweep("Search ${s(tab)}")
        }
        compose.runOnIdle { model.search("zzzz-no-match") }
        sweep("Search without matches")
        compose.runOnIdle { model.search("") }

        compose.onNodeWithTag("nav-2").performClick()
        for (filter in listOf(R.string.library_saved_tracks, R.string.label_albums, R.string.label_artists, R.string.label_playlists, R.string.library_on_device, R.string.library_mixes)) {
            compose.onNodeWithTag("library-filters").performScrollToNode(hasText(s(filter)))
            compose.onNode(hasText(s(filter)) and hasClickAction()).performClick()
            sweep("Library ${s(filter)}")
            if (filter == R.string.label_playlists) inWindow("New playlist") { compose.onNode(hasText(s(R.string.playlist_new)) and hasClickAction()).performClick() }
            if (filter == R.string.library_on_device) inWindow("Track options") {
                compose.onAllNodesWithContentDescription(s(R.string.track_options, "Night Walk")).onFirst().performClick()
            }
        }

        compose.onNodeWithTag("nav-3").performClick()
        sweep("Downloads")

        compose.runOnIdle { model.play(listOf(track)) }
        await("playback to start", 30_000) { model.state.value.playback.playing || model.state.value.playback.error != null }
        val screen = openWindow("Player") { compose.onNodeWithTag("mini-player").performClick() }
        sweep("Player")
        inWindow("Queue") { compose.onNodeWithText(s(R.string.player_queue_count, 1)).performClick() }
        inWindow("Sleep timer") { compose.onNodeWithContentDescription(s(R.string.player_timer_set)).performClick() }
        inWindow("Source details") { compose.onNodeWithContentDescription(s(R.string.player_source_details)).performClick() }
        closeWindow(screen)
        compose.runOnIdle { model.clearQueue() }

        compose.onNodeWithContentDescription(s(R.string.nav_settings)).performClick()
        val list = compose.onNodeWithTag("settings-list")
        sweep("Settings top")
        inWindow("Sign-in") { compose.onAllNodes(hasText(s(R.string.action_connect_tidal)) and hasClickAction()).onFirst().performClick() }
        list.performScrollToNode(hasText(s(R.string.settings_privacy_data)))
        sweep("Settings middle")
        list.performScrollToNode(hasText(s(R.string.settings_licenses_text)))
        sweep("Settings bottom")
        for ((row, name) in listOf(
            R.string.settings_quality_wifi to "Wi-Fi quality", R.string.settings_quality_mobile to "Mobile quality", R.string.normalization_title to "Normalization",
            R.string.eq_title to "Equalizer", R.string.output_title to "Audio output", R.string.settings_theme to "Theme", R.string.scrobble_title to "Scrobbling",
            R.string.stats_title to "Statistics", R.string.settings_clear_history to "Clear history", R.string.settings_licenses to "Licenses",
        )) {
            list.performScrollToNode(hasText(s(row)))
            // The licence texts are legal documents that stay in English, so only the title and the tabs of that dialog are checked.
            val check = if (row != R.string.settings_licenses) ({ sweep(name) }) else ({
                listOf(R.string.licenses_title, R.string.licenses_notices, R.string.licenses_gpl, R.string.licenses_apache, R.string.licenses_inter, R.string.licenses_serif)
                    .forEach { assertTrue(s(it), compose.onAllNodes(hasText(s(it))).fetchSemanticsNodes().isNotEmpty()) }
            })
            inWindow(name, check) { compose.onAllNodes(hasText(s(row)) and hasClickAction()).onFirst().performClick() }
        }

        val report = offenders.joinToString("\n")
        assertTrue("Text written in code, which cannot be translated:\n$report", offenders.isEmpty())
    }

    /** Waits for [condition]. A timeout reports what is on the screen, which a bare timeout does not show. */
    private fun await(what: String, timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        try {
            compose.waitUntil(timeoutMillis, condition)
        } catch (failure: ComposeTimeoutException) {
            val screen = runCatching { visibleTexts() }.getOrNull()
            runCatching { screenshot("pseudo-timeout") }
            throw AssertionError("Timed out waiting for $what. Windows: ${runCatching { windows() }.getOrNull()}. The screen shows: $screen", failure)
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = (output?.let(::File) ?: File(activity.getExternalFilesDir(null), "screenshots")).apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun windows() = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().size

    /** Opens a dialog, sheet or screen that has a window of its own, checks it and closes it again. */
    private fun inWindow(name: String, check: () -> Unit = { sweep(name) }, open: () -> Unit) {
        val base = openWindow(name, open)
        check()
        closeWindow(base)
    }

    /** Runs [open] and waits for the new window. Returns the number of windows from before, which [closeWindow] restores. */
    private fun openWindow(name: String, open: () -> Unit): Int {
        val base = windows()
        open()
        await("the window \"$name\" to open") { windows() > base }
        return base
    }

    /**
     * Closes windows with the back key until [base] windows remain. Back only reaches a window that has the focus,
     * which a slow device gives late, so the key is sent again until the window is gone.
     */
    private fun closeWindow(base: Int) {
        repeat(5) {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            val deadline = System.currentTimeMillis() + 2_000
            while (windows() > base && System.currentTimeMillis() < deadline) { Thread.sleep(100); compose.waitForIdle() }
            if (windows() <= base) return
        }
        throw AssertionError("The window did not close")
    }

    /** Collects the texts and descriptions that a screen shows and reports the ones that no resource wrapped. */
    private fun sweep(screen: String) {
        compose.waitForIdle()
        val systemText = systemTexts()
        val texts = visibleTexts()
        texts.filter { text -> !isPseudo(text) && text !in data && text !in systemText && letters(text, systemText) }
            .forEach { offenders += "$screen: \"$it\"" }
    }

    private fun visibleTexts(): Set<String> {
        val texts = linkedSetOf<String>()
        fun visit(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.let { texts += it }
            node.children.forEach(::visit)
        }
        compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::visit)
        return texts
    }

    private fun isPseudo(text: String) = text.startsWith("[") && text.endsWith("]")

    private fun letters(text: String, systemText: Set<String>): Boolean {
        var rest = text.replace(Regex("\\[[^\\]]*]"), "")
        (data + systemText).sortedByDescending { it.length }.forEach { rest = rest.replace(it, "") }
        return Regex("[A-Za-z]{2,}").containsMatchIn(rest)
    }

    /** Names that Android supplies, such as the audio devices and the presets of the equalizer. */
    private fun systemTexts(): Set<String> = buildSet {
        val devices = activity.getSystemService(android.media.AudioManager::class.java).getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
        devices.forEach { it.productName?.toString()?.let(::add) }
        io.github.yyan115.opentune.playback.EqualizerInfo.read()?.presets?.forEach { add(it.name) }
        add(activity.packageName)
    }
}
