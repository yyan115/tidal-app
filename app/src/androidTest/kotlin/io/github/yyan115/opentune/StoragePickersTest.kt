package io.github.yyan115.opentune

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.ui.CreateDocumentAtRoot
import io.github.yyan115.opentune.ui.OpenDocumentAtRoot
import io.github.yyan115.opentune.ui.StorageRoot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The pickers for backups and for the download folder start at the top of the storage, and not in the folder that the app picked last. */
@RunWith(AndroidJUnit4::class)
class StoragePickersTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun start(intent: Intent): Uri? = intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI)

    @Test fun theRootIsTheTopOfTheInternalStorage() {
        assertEquals("content://com.android.externalstorage.documents/document/primary%3A", StorageRoot.uri.toString())
    }

    @Test fun saveABackupStartsAtTheRoot() {
        val intent = CreateDocumentAtRoot("application/json").createIntent(context, "opentune-backup.json")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("opentune-backup.json", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(StorageRoot.uri, start(intent))
    }

    @Test fun restoreABackupStartsAtTheRoot() {
        val intent = OpenDocumentAtRoot().createIntent(context, arrayOf("application/json", "*/*"))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals(StorageRoot.uri, start(intent))
    }

    @Test fun theDownloadFolderPickerStartsAtTheRoot() {
        val intent = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree().createIntent(context, StorageRoot.uri)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        assertEquals(StorageRoot.uri, start(intent))
    }
}
