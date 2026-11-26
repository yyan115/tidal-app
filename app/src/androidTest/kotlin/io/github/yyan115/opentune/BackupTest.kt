package io.github.yyan115.opentune

import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.data.BackupManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp

    @Test fun playlistsHistoryAndSettingsSurviveABackupAndRestore() {
        val title = "Backup check"
        app.library.playlists().filter { it.title == title }.forEach { app.library.deletePlaylist(it.id) }
        val tidalTrack = Track("424242", "Streamed Song", "Some Artist", "Some Album", "9")
        val localTrack = Track("local:backup-check", "Imported", "Me", localUri = "content://example/imported.flac")
        val playlist = app.library.createPlaylist(title)
        app.library.addToPlaylist(playlist.id, tidalTrack); app.library.addToPlaylist(playlist.id, localTrack)
        app.library.recordPlay(tidalTrack, 90_000, 1_700_000_000_000)
        app.settings.update { it.copy(normalization = "album", theme = "ember") }

        val backup = BackupManager(app).toJson()
        assertFalse("The backup must not contain tokens", backup.toString().contains("access", ignoreCase = true) || backup.toString().contains("refresh", ignoreCase = true))
        val text = backup.toString()

        app.library.deletePlaylist(playlist.id)
        app.settings.update { it.copy(normalization = "off", theme = "gold") }
        val summary = BackupManager(app).restore(JSONObject(text))

        assertTrue(summary.playlists >= 1)
        val restored = app.library.playlists().single { it.title == title }
        assertEquals(listOf("424242"), restored.trackIds)
        assertEquals("album", app.settings.state.value.normalization)
        assertEquals("ember", app.settings.state.value.theme)
        assertEquals(0, BackupManager(app).restore(JSONObject(text)).plays)
        assertEquals(1, app.library.playlists().count { it.title == title })
        app.library.deletePlaylist(restored.id)
        app.settings.update { it.copy(normalization = "off", theme = "gold") }
    }

    @Test fun theSettingsOfPlaybackAndTheEqualizerSurviveABackupAndRestore() {
        val before = app.settings.state.value
        try {
            app.settings.update { it.copy(autoplay = false, avoidRepeatMinutes = 2160, visualizer = true, checkUpdates = false, hideAiMusic = true,
                equalizerCustom = listOf(300, -200, 0, 150, 450), equalizerUsesCustom = true) }
            val text = BackupManager(app).toJson().toString()
            app.settings.update { it.copy(autoplay = true, avoidRepeatMinutes = 0, visualizer = false, checkUpdates = true, hideAiMusic = false, equalizerCustom = emptyList(), equalizerUsesCustom = false) }
            BackupManager(app).restore(JSONObject(text))
            val restored = app.settings.state.value
            assertTrue("The choice to hide AI-generated music is in the backup", restored.hideAiMusic)
            assertFalse(restored.autoplay)
            assertEquals(36 * 60, restored.avoidRepeatMinutes)
            assertTrue(restored.visualizer)
            assertFalse(restored.checkUpdates)
            assertEquals(listOf(300, -200, 0, 150, 450), restored.equalizerCustom)
            assertTrue(restored.equalizerUsesCustom)
        } finally { app.settings.update { before } }
    }

    @Test fun aBackupOfAnEarlierVersionLeavesTheNewerSettingsAsTheyAre() {
        val before = app.settings.state.value
        try {
            app.settings.update { it.copy(autoplay = false, avoidRepeatMinutes = 360, checkUpdates = false) }
            BackupManager(app).restore(JSONObject("""{"format":"opentune-backup","version":1,"settings":{"theme":"dark"}}"""))
            val restored = app.settings.state.value
            assertFalse(restored.autoplay)
            assertEquals(6 * 60, restored.avoidRepeatMinutes)
            assertFalse(restored.checkUpdates)
        } finally { app.settings.update { before } }
    }

    @Test fun otherFilesAreRefused() {
        try { BackupManager(app).restore(JSONObject("""{"format":"something-else"}""")); fail() } catch (_: IllegalArgumentException) { }
        try { BackupManager(app).restore(JSONObject("""{"format":"opentune-backup","version":9}""")); fail() } catch (_: IllegalArgumentException) { }
    }
}
