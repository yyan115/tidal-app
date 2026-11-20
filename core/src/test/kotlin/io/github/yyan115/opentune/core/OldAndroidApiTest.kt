package io.github.yyan115.opentune.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The app runs on Android 10 and later, and some Java methods exist only on newer versions. The tests of the library run on a newer Java, so they would not notice. */
class OldAndroidApiTest {
    private val sources get() = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test fun theUrlDecoderIsNotCalledWithACharset() {
        val offenders = sources.flatMap { file -> file.readLines().mapIndexedNotNull { index, line ->
            if (Regex("""URL(De|En)coder\.(de|en)code\(.*Charsets\.""").containsMatchIn(line)) "${file.name}:${index + 1}" else null } }
        assertTrue("These calls exist only from Android 13, so they name the character set as text instead: $offenders", offenders.isEmpty())
    }
}
