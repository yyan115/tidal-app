package io.github.yyan115.opentune

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/**
 * Makes the display of the device small for the length of a test and restores it afterwards, so that a list is longer than the screen
 * on any device that runs the test. Put it around the rule that starts the activity.
 */
class SmallScreen(private val size: String = "480x854", private val density: String = "240") : ExternalResource() {
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    override fun before() { shell("wm size $size"); shell("wm density $density"); Thread.sleep(1_000) }

    override fun after() { shell("wm size reset"); shell("wm density reset"); Thread.sleep(500) }
}
