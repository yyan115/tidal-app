package io.github.yyan115.opentune.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.data.BulkDownload
import kotlinx.coroutines.launch

/**
 * The button of a page that downloads all its songs. It is on the page from the first frame and disabled while there is nothing to download.
 * [tracks] lists the songs of the page, and the songs that are already on the device are left out. A download of more than [BulkDownload.CONFIRM_ABOVE]
 * songs asks first and names how many there are.
 */
@Composable
fun DownloadAllButton(tracks: suspend () -> List<Track>, onDownload: (Track) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, compact: Boolean = false) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<Track>?>(null) }
    val start = {
        scope.launch {
            val missing = tracks().filterNot(Track::onDevice)
            if (BulkDownload.needsConfirmation(missing.size)) pending = missing else missing.forEach(onDownload)
        }
        Unit
    }
    // A compact button is the icon alone, for the header of a list. The button of a page header has an outline.
    if (compact) IconButton(onClick = start, modifier = modifier.testTag("download-all"), enabled = enabled) { Icon(OpenTuneIcons.Download, stringResource(R.string.album_download_all)) }
    else TuneOutlinedButton(onClick = start, modifier = modifier.testTag("download-all"), enabled = enabled) { Icon(OpenTuneIcons.Download, stringResource(R.string.album_download_all)) }
    pending?.let { list ->
        AppDialog(onDismissRequest = { pending = null }, title = { Text(pluralStringResource(R.plurals.download_all_title, list.size, list.size)) },
            text = { Text(stringResource(R.string.download_all_text)) },
            confirmButton = { DialogConfirm(onClick = { pending = null; list.forEach(onDownload) }) { Text(stringResource(R.string.action_download)) } },
            dismissButton = { DialogDismiss(onClick = { pending = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}
