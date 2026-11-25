package io.github.yyan115.opentune.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Where the system pickers for backups and for the download folder start: the top of the internal storage, which lists every folder,
 * such as Alarms, DCIM, Download and Music. Without it the picker opens in the folder that the app picked last, which is the music folder.
 */
object StorageRoot {
    private const val AUTHORITY = "com.android.externalstorage.documents"

    val uri: Uri = DocumentsContract.buildDocumentUri(AUTHORITY, "primary:")
}

/** Saves a document, and the picker opens at the top of the storage. */
class CreateDocumentAtRoot(mimeType: String) : ActivityResultContracts.CreateDocument(mimeType) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, StorageRoot.uri)
}

/** Opens a document, and the picker opens at the top of the storage. */
class OpenDocumentAtRoot : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, StorageRoot.uri)
}
