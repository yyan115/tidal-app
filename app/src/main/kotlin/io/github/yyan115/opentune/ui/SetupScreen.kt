package io.github.yyan115.opentune.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.data.DownloadFolder

/**
 * The page that follows the first sign-in. It lists what the app needs to save downloads: the folder for them, which is required, and the
 * notification of their progress, which is optional and applies from Android 13. Each one can be left as it is.
 */
@Composable
fun SetupScreen(folder: String, onFolderPicked: (Uri) -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(onFolderPicked) }
    Surface(Modifier.fillMaxSize().testTag("setup"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) {
            Spacer(Modifier.height(48.dp))
            Text(stringResource(R.string.setup_title), Modifier.padding(horizontal = 24.dp), style = AppFonts.title(44, 50))
            Text(stringResource(R.string.setup_intro), Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle(stringResource(R.string.setup_required_downloads))
            SettingRow(OpenTuneIcons.Folder, stringResource(R.string.settings_download_folder),
                if (DownloadFolder.isDefault(folder)) stringResource(R.string.setup_folder_text) else DownloadFolder.summary(folder, stringResource(R.string.volume_internal), stringResource(R.string.volume_card), stringResource(R.string.setup_folder_chosen)),
                trailing = { TextButton(onClick = { picker.launch(null) }, modifier = Modifier.testTag("setup-folder")) { Text(stringResource(R.string.setup_choose)) } })
            if (Permissions.notificationsApply()) {
                SectionTitle(stringResource(R.string.setup_optional))
                val allowed = remember(refresh) { Permissions.granted(context, Manifest.permission.POST_NOTIFICATIONS) }
                SettingRow(OpenTuneIcons.Notifications, stringResource(R.string.setup_notifications), stringResource(R.string.setup_notifications_text), trailing = {
                    if (allowed) Row(verticalAlignment = Alignment.CenterVertically) { Icon(OpenTuneIcons.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.setup_allowed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    else TextButton(onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.testTag("setup-allow")) { Text(stringResource(R.string.setup_allow)) }
                })
            }
            Spacer(Modifier.weight(1f))
            TuneButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 52.dp).testTag("setup-continue")) { Text(stringResource(R.string.setup_continue)) }
        }
    }
}
