package io.github.yyan115.opentune

import io.github.yyan115.opentune.playback.EqualizerInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class EqualizerInfoTest {
    @Test fun aPresetNameLosesTheCharactersThatSomeDevicesAddToItsEnd() {
        assertEquals("Flat", EqualizerInfo.cleanName("Flat\u0000\u0000"))
        assertEquals("Heavy Metal", EqualizerInfo.cleanName("Heavy Metal\u0000 "))
    }

    @Test fun aNameWithoutThoseCharactersStaysAsItIs() {
        assertEquals("Hip Hop", EqualizerInfo.cleanName("Hip Hop"))
        assertEquals("", EqualizerInfo.cleanName(""))
    }
}
