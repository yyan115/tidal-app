package io.github.yyan115.opentune

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The tests run in the memory of a phone and not in the 16 MB that the emulator of Android 10 gives when its system starts before the memory setting arrives. */
@RunWith(AndroidJUnit4::class)
class MemoryLimitTest {
    @Test fun theTestsHaveTheMemoryOfAPhone() {
        val megabytes = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        assertTrue("The app may use $megabytes MB. A phone gives 192 MB or more,, so the emulator did not start with its memory setting. See scripts/prepare_emulator.sh.", megabytes >= 192)
    }
}
