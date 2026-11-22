package io.github.yyan115.opentune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The system draws a bar of its own behind the three buttons, and a veil over it where the app lets it. The activity, every sheet and every pop-up
 * clear that veil, so that the strip has the colour of the screen on every version of Android.
 */
class SystemBarsRulesTest {
    private val ui = File("src/main/kotlin/io/github/yyan115/opentune")
    private val screens = ui.walkTopDown().filter { it.extension == "kt" }.toList()

    @Test fun everySheetIsMadeByTheOneHelperThatClearsTheBar() {
        val wrong = screens.filter { it.name != "AppSheet.kt" }.flatMap { file -> file.readLines().mapIndexedNotNull { i, line ->
            if (Regex("""(?<![A-Za-z])ModalBottomSheet\(""").containsMatchIn(line) && !line.trimStart().startsWith("//")) "${file.name}:${i + 1}" else null } }
        assertTrue("Use AppSheet, which clears the veil behind the buttons: $wrong", wrong.isEmpty())
    }

    @Test fun everyPopUpIsMadeByTheOneHelperThatClearsTheBar() {
        val wrong = screens.filter { it.name != "AppDialog.kt" && it.name != "GalleryPlayer.kt" }.flatMap { file -> file.readLines().mapIndexedNotNull { i, line ->
            if (Regex("""(?<![A-Za-z])(BasicAlertDialog|AlertDialog|Dialog)\(""").containsMatchIn(line) && !line.trimStart().startsWith("//") && !line.contains("fun ")) "${file.name}:${i + 1}: ${line.trim()}" else null } }
        assertTrue("Use AppDialog, which clears the veil behind the buttons: $wrong", wrong.isEmpty())
    }

    @Test fun theWindowOfTheFullPlayerClearsTheBarToo() {
        assertTrue("The full player opens its own window", File(ui, "ui/GalleryPlayer.kt").readText().let { it.contains("Dialog(onDismissRequest = onClose") && it.contains("ClearNavigationBar()") })
    }

    @Test fun theActivityAndThePreviewMakeTheNavigationBarTransparentAndTurnTheVeilOff() {
        for (path in listOf("MainActivity.kt", "preview/ThemePreviewActivity.kt")) {
            val file = if (path.startsWith("preview")) File("src/debug/kotlin/io/github/yyan115/opentune/$path") else File(ui, path)
            val text = file.readText()
            assertTrue("$path asks for a transparent navigation bar", text.contains("enableEdgeToEdge(") && Regex("""navigationBarStyle = SystemBarStyle\.auto\(android\.graphics\.Color\.TRANSPARENT, android\.graphics\.Color\.TRANSPARENT\)""").containsMatchIn(text))
            assertTrue("$path turns the contrast veil off", text.contains("isNavigationBarContrastEnforced = false"))
        }
    }

    @Test fun theWindowBehindTheAppHasTheColourOfTheTheme() {
        val text = File(ui, "MainActivity.kt").readText()
        assertEquals("The activity paints its window with the background of the theme", true, text.contains("setBackgroundDrawable"))
    }
}
