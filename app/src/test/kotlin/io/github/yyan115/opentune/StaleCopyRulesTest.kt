package io.github.yyan115.opentune

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A song that sits in the queue is a copy from the time it was queued. A download that finishes later is in the saved record and not in the copy,
 * so a screen that reads the copy shows an old state. Screens read the saved record through `playingTrack` and `current`.
 */
class StaleCopyRulesTest {
    private val sources = File("src/main/kotlin/io/github/yyan115/opentune/ui").listFiles { f -> f.extension == "kt" && f.name !in setOf("MainViewModel.kt", "CurrentTrack.kt") }.orEmpty()
    private val allowed = listOf(Regex("""playback\.track\?\.id"""), Regex("""playback\.track (!=|==) null"""))

    @Test fun screensDoNotReadTheCopyOfTheTrackInTheQueue() {
        val wrong = sources.flatMap { file -> file.readLines().mapIndexedNotNull { i, line ->
            if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) null
            else if (Regex("""\.track\b""").containsMatchIn(line) && line.contains("playback.track") && allowed.none { it.containsMatchIn(line) }) "${file.name}:${i + 1}: ${line.trim()}" else null } }
        assertTrue("Read the saved record with playingTrack or current(track), and use the copy only for its id: $wrong", wrong.isEmpty())
    }

    @Test fun thePlayerDoesNotTakeTheTrackFromThePlaybackStateByAnotherName() {
        val wrong = sources.flatMap { file -> file.readLines().mapIndexedNotNull { i, line ->
            if (Regex("""val player = state\.playback; val track = player\.track""").containsMatchIn(line)) "${file.name}:${i + 1}" else null } }
        assertTrue("These lines read the copy: $wrong", wrong.isEmpty())
    }
}
