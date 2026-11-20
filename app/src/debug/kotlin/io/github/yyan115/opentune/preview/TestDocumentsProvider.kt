package io.github.yyan115.opentune.preview

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * A folder tree for the tests of the folder scan and of the download folder. It serves the files in the folder `documents-test` of the private storage of the app,
 * and it lets a test create, write and delete files, as the storage of a device does.
 * A file named `.offline` in that folder makes the provider fail, as a memory card that was taken out does.
 * The debug build carries it. A release build does not.
 */
class TestDocumentsProvider : DocumentsProvider() {
    private val base get() = File(context!!.filesDir, "documents-test")

    private fun fileOf(id: String): File {
        if (File(base, ".offline").exists()) throw FileNotFoundException("The storage is not available")
        val file = File(base, id.removePrefix(ROOT).trimStart('/'))
        if (!file.canonicalPath.startsWith(base.canonicalPath)) throw FileNotFoundException(id)
        return file
    }

    private fun idOf(file: File) = ROOT + file.canonicalPath.removePrefix(base.canonicalPath)

    private fun mimeOf(file: File) = when {
        file.isDirectory -> Document.MIME_TYPE_DIR
        file.extension.equals("flac", true) -> "audio/flac"
        file.extension.equals("jpg", true) -> "image/jpeg"
        else -> "application/octet-stream"
    }

    private fun flagsOf(file: File) = if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_DELETE else Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE

    private fun MatrixCursor.add(file: File) {
        newRow().add(Document.COLUMN_DOCUMENT_ID, idOf(file)).add(Document.COLUMN_DISPLAY_NAME, if (file == base) "Test library" else file.name)
            .add(Document.COLUMN_MIME_TYPE, mimeOf(file)).add(Document.COLUMN_FLAGS, flagsOf(file)).add(Document.COLUMN_SIZE, file.length()).add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
    }

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID)).apply {
        newRow().add(Root.COLUMN_ROOT_ID, ROOT).add(Root.COLUMN_DOCUMENT_ID, ROOT).add(Root.COLUMN_TITLE, "Test library").add(Root.COLUMN_FLAGS, 0)
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DEFAULT_COLUMNS).apply { add(fileOf(documentId)) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: DEFAULT_COLUMNS).apply { fileOf(parentDocumentId).listFiles().orEmpty().forEach { add(it) } }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileOf(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = fileOf(parentDocumentId)
        var file = File(parent, displayName)
        var number = 1
        // A name that is taken gets a number, as on a device.
        while (file.exists()) file = File(parent, displayName.substringBeforeLast('.') + " (${number++})" + displayName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" })
        if (mimeType == Document.MIME_TYPE_DIR) file.mkdirs() else file.createNewFile()
        return idOf(file)
    }

    override fun deleteDocument(documentId: String) { fileOf(documentId).deleteRecursively() }

    override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId == parentDocumentId || documentId.startsWith("$parentDocumentId/")

    companion object {
        const val ROOT = "root"
        private val DEFAULT_COLUMNS = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
    }
}
