package io.github.yyan115.undertow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import coil3.compose.AsyncImage
import io.github.yyan115.undertow.core.Release
import io.github.yyan115.undertow.core.Track

@Composable
fun Artwork(uri: String?, description: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.MusicNote, null, Modifier.fillMaxSize(.36f), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
        if (uri != null) AsyncImage(uri, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun PageTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action?.invoke()
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, description: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Icon(icon, null, Modifier.padding(22.dp).size(32.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleLarge)
        Text(description, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 20.dp)) { Text(action) }
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
    Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(start = 24.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (index != null) Text("${index + 1}", Modifier.width(32.dp), style = MaterialTheme.typography.bodySmall,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        else Artwork(track.artwork, null, Modifier.size(52.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.explicit) {
                    Text("E", Modifier.padding(end = 6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)).padding(horizontal = 3.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
                if (track.isLocal) Icon(Icons.Outlined.DownloadDone, "Available offline", Modifier.padding(end = 4.dp).size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onMenu) { Icon(Icons.Outlined.MoreVert, "Options for ${track.title}") }
    }
}

@Composable
fun ReleaseCard(release: Release, onClick: () -> Unit) {
    Column(Modifier.width(152.dp).clickable(onClick = onClick).padding(4.dp)) {
        Artwork(release.artwork, null, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(release.title, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(release.artist.ifBlank { release.subtitle }, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
