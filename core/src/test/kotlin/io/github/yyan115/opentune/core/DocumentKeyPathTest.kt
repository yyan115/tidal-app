package io.github.yyan115.opentune.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentKeyPathTest {
    @Test fun theRelativePathAndTheNameMakeTheLocationOfAFile() {
        val location = DocumentKey.fromPath("primary", "Music/OpenTune/", "Song.flac")
        assertEquals("primary", location.volume)
        assertEquals(listOf("Music", "OpenTune"), location.folders)
        assertEquals("Song.flac", location.fileName)
    }

    @Test fun aFileAtTheTopOfTheVolumeHasNoFolders() {
        val location = DocumentKey.fromPath("primary", "", "Song.flac")
        assertEquals(emptyList<String>(), location.folders)
        assertEquals("Song.flac", location.fileName)
    }

    @Test fun slashesAtBothEndsAndDoubledSlashesAreIgnored() {
        assertEquals(listOf("Music", "Lossless", "Song.flac"), DocumentKey.fromPath("primary", "/Music//Lossless/", "Song.flac").segments)
    }
}
