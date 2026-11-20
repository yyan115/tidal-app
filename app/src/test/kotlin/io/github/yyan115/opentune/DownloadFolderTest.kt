package io.github.yyan115.opentune

import io.github.yyan115.opentune.data.DownloadFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFolderTest {
    private val internal = "Internal storage"
    private val card = "Memory card"

    @Test fun withoutAChosenFolderTheDefaultFolderShows() {
        assertEquals("Music/OpenTune", DownloadFolder.summary("", internal, card))
    }

    @Test fun aFolderOnTheMainStorageShowsItsPath() {
        assertEquals("Music/Lossless", DownloadFolder.summary("content://com.android.externalstorage.documents/tree/primary%3AMusic%2FLossless", internal, card))
    }

    @Test fun aFolderOnACardShowsTheCardAndTheFolder() {
        assertEquals("Memory card · Audio/Downloads", DownloadFolder.summary("content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AAudio%2FDownloads", internal, card))
    }

    @Test fun theTopOfAVolumeShowsTheNameOfTheVolume() {
        assertEquals("Internal storage", DownloadFolder.summary("content://com.android.externalstorage.documents/tree/primary%3A", internal, card))
        assertEquals("Memory card", DownloadFolder.summary("content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A", internal, card))
    }

    @Test fun aFolderOfAnotherProviderShowsItsName() {
        assertEquals("Chosen folder", DownloadFolder.summary("content://com.example.documents/tree/abc", internal, card, other = "Chosen folder"))
    }

    @Test fun aFolderIsChosenWhenTheSettingHoldsAnAddress() {
        assertEquals(true, DownloadFolder.isDefault(""))
        assertEquals(false, DownloadFolder.isDefault("content://com.android.externalstorage.documents/tree/primary%3AMusic"))
    }
}
