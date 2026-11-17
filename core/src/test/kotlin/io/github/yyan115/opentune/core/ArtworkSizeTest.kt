package io.github.yyan115.opentune.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkSizeTest {
    @Test fun aTidalCoverGetsItsLargerCopy() {
        val cover = JsonCodec.image("ab12cd34-5678-90ef-aaaa-bbbbccccdddd")
        assertEquals("https://resources.tidal.com/images/ab12cd34/5678/90ef/aaaa/bbbbccccdddd/640x640.jpg", cover)
        assertEquals("https://resources.tidal.com/images/ab12cd34/5678/90ef/aaaa/bbbbccccdddd/1280x1280.jpg", ArtworkSize.large(cover))
    }

    @Test fun aFileOnTheDeviceStaysAsItIs() {
        assertEquals("file:///data/user/0/app/files/artwork/embedded_x.jpg", ArtworkSize.large("file:///data/user/0/app/files/artwork/embedded_x.jpg"))
    }

    @Test fun otherSizesAndOtherHostsStayAsTheyAre() {
        assertEquals("https://resources.tidal.com/images/a/b/c/d/e/750x750.jpg", ArtworkSize.large("https://resources.tidal.com/images/a/b/c/d/e/750x750.jpg"))
        assertEquals("https://example.com/images/a/640x640.jpg", ArtworkSize.large("https://example.com/images/a/640x640.jpg"))
    }

    @Test fun noAddressStaysNone() { assertNull(ArtworkSize.large(null)) }
}
