package io.github.yyan115.opentune

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import io.github.yyan115.opentune.ui.Artwork
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** The full player asks for the larger copy of a cover. A cover without that copy shows the standard copy and does not ask for the large one again. */
@RunWith(AndroidJUnit4::class)
class LargeCoverTest {
    @get:Rule val compose = createComposeRule()
    private val requested = CopyOnWriteArrayList<String>()
    private val standard = "https://resources.tidal.com/images/aaaa/bbbb/cccc/dddd/eeee/640x640.jpg"
    private val large = "https://resources.tidal.com/images/aaaa/bbbb/cccc/dddd/eeee/1280x1280.jpg"

    /** Serves the covers from memory. The large copy is missing when [largeExists] is false. */
    private fun serveCovers(largeExists: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = object : Fetcher.Factory<Uri> {
            override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                if (!data.toString().startsWith("https://resources.tidal.com/")) return null
                return object : Fetcher {
                    override suspend fun fetch(): FetchResult {
                        requested += data.toString()
                        if (!largeExists && data.toString() == large) throw IOException("The large copy does not exist")
                        return ImageFetchResult(ColorDrawable(Color.RED).asImage(), false, DataSource.NETWORK)
                    }
                }
            }
        }
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components { add(factory) }.build())
    }

    @After fun restoreLoader() { SingletonImageLoader.reset() }

    private fun showAndWait(): List<String> {
        compose.setContent { Artwork(standard, null, Modifier.size(100.dp), large = true) }
        // The loads run on other threads, so the test waits in real time, and each round lets the retry schedule of the picture move on.
        repeat(40) { compose.waitForIdle(); Thread.sleep(250) }
        return requested.toList()
    }

    @Before fun clear() { requested.clear() }

    @Test fun theLargeCopyIsLoadedWhenItExists() {
        serveCovers(largeExists = true)
        assertEquals(listOf(large), showAndWait())
    }

    @Test fun aMissingLargeCopyIsTriedOnceAndTheStandardCopyIsKept() {
        serveCovers(largeExists = false)
        val calls = showAndWait()
        assertEquals("The large copy is requested once", 1, calls.count { it == large })
        // A slow device may load the standard copy a second time when the picture that shows the failure is replaced. A copy that is requested again and again
        // would be requested more often than that within the ten seconds of the test, so two is the limit.
        val standardRequests = calls.count { it == standard }
        org.junit.Assert.assertTrue("The standard copy is requested once or twice, and it was requested $standardRequests times", standardRequests in 1..2)
    }
}
