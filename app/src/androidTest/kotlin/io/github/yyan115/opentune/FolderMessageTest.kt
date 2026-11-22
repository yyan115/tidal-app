package io.github.yyan115.opentune

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A change of the download folder is announced in Settings. It is never announced on the permissions page, however often the folder is changed there. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class FolderMessageTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OpenTuneApp
    private val model get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private val tree: Uri get() = DocumentsContract.buildTreeDocumentUri("${app.packageName}.testdocuments", "root/folder-message")
    private val directory get() = java.io.File(app.filesDir, "documents-test/folder-message")

    @Before fun useTheTestStorage() {
        directory.mkdirs()
        compose.runOnIdle { model.notify("") }
    }

    @After fun backToTheDefaultFolder() {
        app.settings.update { it.copy(downloadFolder = "") }
        directory.deleteRecursively()
    }

    private fun change(announce: Boolean) = runBlocking(Dispatchers.IO) { model.useDownloadFolder(tree, announce) }

    @Test fun aChangeInSettingsIsAnnounced() {
        compose.runOnIdle { model.notify("") }
        change(announce = true)
        assertEquals("Download folder changed.", model.state.value.message)
        assertEquals(tree.toString(), app.settings.state.value.downloadFolder)
    }

    @Test fun aChangeOnThePermissionsPageIsNeverAnnouncedHoweverOftenItHappens() {
        compose.runOnIdle { model.notify("") }
        repeat(3) { change(announce = false); assertEquals("", model.state.value.message.orEmpty()) }
        assertEquals("The folder is still saved", tree.toString(), app.settings.state.value.downloadFolder)
    }

    @Test fun aFolderThatCannotBeUsedIsReportedOnBothScreens() {
        val unusable = DocumentsContract.buildTreeDocumentUri("${app.packageName}.testdocuments", "root/does-not-exist")
        compose.runOnIdle { model.notify("") }
        runBlocking(Dispatchers.IO) { model.useDownloadFolder(unusable, announce = false) }
        assertEquals("This folder cannot be used for downloads. Choose another folder.", model.state.value.message)
    }
}
