package io.github.yyan115.opentune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import io.github.yyan115.opentune.core.Release
import io.github.yyan115.opentune.core.Track

val LocalOfflineMode = staticCompositionLocalOf { false }

@Composable
fun Artwork(uri: String?, description: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val offline = LocalOfflineMode.current
    val request = remember(uri, offline, context) { ImageRequest.Builder(context).data(uri)
        .crossfade(true).networkCachePolicy(if (offline) CachePolicy.DISABLED else CachePolicy.ENABLED).build() }
    Box(modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(OpenTuneIcons.Album, null, Modifier.fillMaxSize(.32f), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f))
        if (uri != null) AsyncImage(request, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontFamily = EditorialFont, fontSize = 27.sp, lineHeight = 32.sp)
        if (action != null) TextButton(onClick = onAction) {
            Text(action, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(7.dp)); Icon(OpenTuneIcons.ArrowForward, null, Modifier.size(16.dp))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, description: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Icon(icon, null, Modifier.padding(24.dp).size(34.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, Modifier.padding(top = 24.dp), style = MaterialTheme.typography.headlineSmall)
        Text(description, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) TuneTonalButton(onClick = onAction, modifier = Modifier.padding(top = 24.dp)) { Text(action) }
    }
}

@Composable
fun ErrorCard(message: String, onRetry: (() -> Unit)? = null) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
fun TrackRow(track: Track, playing: Boolean = false, onPlay: () -> Unit, onMenu: () -> Unit, index: Int? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(start = 24.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (index != null) Text("%02d".format(index + 1), Modifier.width(32.dp), fontFamily = FontFamily.Monospace, fontSize = 10.sp,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        else Artwork(track.artwork, null, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (track.explicit) {
                    Text("E", Modifier.padding(end = 6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)).padding(horizontal = 3.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
                if (track.isLocal) Icon(OpenTuneIcons.DownloadDone, "Available offline", Modifier.padding(end = 4.dp).size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(track.artist, fontFamily = InterfaceFont, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onMenu) { Icon(OpenTuneIcons.MoreVert, "Options for ${track.title}", Modifier.size(20.dp)) }
    }
}

@Composable
fun ReleaseCard(release: Release, onClick: () -> Unit) {
    Column(Modifier.width(152.dp).clickable(onClick = onClick).padding(4.dp)) {
        Artwork(release.artwork, null, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(release.title, Modifier.padding(top = 10.dp), fontFamily = InterfaceFont, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(release.artist.ifBlank { release.subtitle }, Modifier.padding(top = 3.dp), fontFamily = InterfaceFont, fontSize = 10.sp, lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .8f))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) Text(subtitle, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun UpperLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, fontFamily = InterfaceFont, fontSize = 10.sp, lineHeight = 16.sp,
        letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun TuneButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.Button(onClick, modifier, enabled, shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp), content = content)
}

@Composable
fun TuneTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    FilledTonalButton(onClick, modifier, enabled, shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp), content = content)
}

@Composable
fun TuneOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier, enabled, shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp), content = content)
}
