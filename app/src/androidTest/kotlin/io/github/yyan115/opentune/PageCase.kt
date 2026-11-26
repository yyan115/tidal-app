package io.github.yyan115.opentune

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.core.*
import io.github.yyan115.opentune.ui.*

/** Pages of TIDAL as the screen shows them, built from data that the test makes up. */
object PageFixtures {
    fun songs(count: Int) = (1..count).map { Track("page-song-$it", "Page song $it", "Page artist", "Page album", "album1") }
    private val release = Release("page-release", "A title that is long enough to wrap onto a second line", "TIDAL", null)
    val mixHeader = MixHeader("header", "", release.copy(type = ReleaseType.MIX), "Songs inspired by your listening", "A mix that TIDAL made")
    val artistHeader = ArtistHeader("header", "", release.copy(type = ReleaseType.ARTIST), "", "", "")
    /** [machineEvery] marks every n-th song as AI-generated, from the second one on. */
    fun list(count: Int, title: String = "", total: Int = count, dataPath: String? = null, showMore: String? = null, numbered: Boolean = false, roles: Boolean = false, machineEvery: Int = 0) =
        TrackListModule("list", title, songs(count).mapIndexed { index, song -> if (machineEvery > 0 && (index + 1) % machineEvery == 0) song.copy(ai = true) else song }, numbered, showMore, dataPath, total,
            if (roles) songs(count).associate { it.id to listOf("Producer", "Composer", "Lyricist", "Piano", "Vocalist", "Mixing engineer", "Mastering engineer") } else emptyMap())
    fun page(vararg modules: PageModule) = PageData("page", "Page", modules.toList())
}

/** The rows of [page] in a list, with the same code that the page screen uses. */
@UnstableApi
@Composable
fun PageCase(page: PageData, vm: MainViewModel, settings: io.github.yyan115.opentune.data.Settings = io.github.yyan115.opentune.data.Settings()) {
    val pages: PagesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val scope = rememberCoroutineScope()
    val context = remember(vm, pages, scope, settings) { PageContext(AppState(settings = settings), vm, pages, scope, {}, "Page") }
    LazyColumn(Modifier.testTag("page-list")) { pageContent(page, PageUi(page = page), "catalog", context, viewAll = false) }
}
