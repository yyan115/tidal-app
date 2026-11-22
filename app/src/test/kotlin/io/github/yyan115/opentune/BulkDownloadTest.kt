package io.github.yyan115.opentune

import io.github.yyan115.opentune.data.BulkDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkDownloadTest {
    @Test fun aDownloadOfTenSongsOrFewerStartsAtOnce() {
        assertEquals(10, BulkDownload.CONFIRM_ABOVE)
        for (count in 0..10) assertFalse("$count songs", BulkDownload.needsConfirmation(count))
    }

    @Test fun aDownloadOfMoreThanTenSongsAsksFirst() {
        for (count in listOf(11, 14, 15, 20, 50, 500)) assertTrue("$count songs", BulkDownload.needsConfirmation(count))
    }
}
