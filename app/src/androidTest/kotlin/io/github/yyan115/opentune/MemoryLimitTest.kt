package io.github.yyan115.opentune

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The tests run in the memory of a phone and not in the 16 MB that an emulator without a limit for apps gives. */
@RunWith(AndroidJUnit4::class)
class MemoryLimitTest {
    @Test fun theTestsHaveTheMemoryOfAPhone() {
        val megabytes = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        assertTrue("The app may use $megabytes MB. A phone gives 192 MB or more, so the debug app asks for a large heap in app/src/debug/AndroidManifest.xml.", megabytes >= 192)
    }
}
