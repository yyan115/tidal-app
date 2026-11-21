package io.github.yyan115.opentune.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentKeyTreeTest {
    @Test fun aFolderOnTheMainStorageHasItsPath() {
        val location = DocumentKey.treeLocation("content://com.android.externalstorage.documents/tree/primary%3AMusic%2FLossless")
        assertEquals("primary", location?.volume)
        assertEquals(listOf("Music", "Lossless"), location?.segments)
    }

    @Test fun aFolderOnAMemoryCardHasItsVolume() {
        val location = DocumentKey.treeLocation("content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AAudio%2FDownloads")
        assertEquals("1A2B-3C4D", location?.volume)
        assertEquals(listOf("Audio", "Downloads"), location?.segments)
    }

    @Test fun theTopOfAVolumeHasNoFolders() {
        assertEquals(emptyList<String>(), DocumentKey.treeLocation("content://com.android.externalstorage.documents/tree/primary%3A")?.segments)
    }

    @Test fun aProviderWithoutPathsHasNoLocation() {
        assertNull(DocumentKey.treeLocation("content://com.android.providers.downloads.documents/tree/downloads"))
        assertNull(DocumentKey.treeLocation("content://media/external/audio/media/42"))
        assertNull(DocumentKey.treeLocation("not an address"))
    }

    @Test fun theKeyOfADocumentInAPickedFolderIsTheKeyOfTheSameFileInAScan() {
        val inTree = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FLossless/document/primary%3AMusic%2FLossless%2FSong.flac"
        val plain = "content://com.android.externalstorage.documents/document/primary%3AMusic%2FLossless%2FSong.flac"
        assertEquals("com.android.externalstorage.documents|primary:Music/Lossless/Song.flac", DocumentKey.of(inTree))
        assertEquals(DocumentKey.of(plain), DocumentKey.of(inTree))
    }
}
