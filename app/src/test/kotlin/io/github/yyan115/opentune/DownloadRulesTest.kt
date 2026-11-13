package io.github.yyan115.opentune

import androidx.media3.common.util.UnstableApi
import androidx.work.NetworkType
import io.github.yyan115.opentune.data.DownloadRequests
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class DownloadRulesTest {
    @Test fun wifiOnlyNeedsAnUnmeteredNetworkAndMobileDataAllowsAnyConnection() {
        assertEquals(NetworkType.UNMETERED, DownloadRequests.networkTypeFor(true))
        assertEquals(NetworkType.CONNECTED, DownloadRequests.networkTypeFor(false))
    }
}
