package io.github.yyan115.undertow.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LicensesDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableIntStateOf(0) }
    val documents by produceState<List<String>?>(null) {
        value = withContext(Dispatchers.IO) {
            listOf("THIRD_PARTY_NOTICES.md", "LICENSE", "Apache-2.0.txt", "Inter-OFL.txt", "Instrument-Serif-OFL.txt").map { name ->
                context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
            }
        }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("Open-source licenses") }, text = {
        if (documents == null) CircularProgressIndicator()
        else Column {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Notices", "GPL 3", "Apache 2", "Inter", "Instrument Serif").forEachIndexed { index, title ->
                    FilterChip(selected == index, { selected = index }, label = { Text(title) })
                }
            }
            key(selected) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (selected != 0) SelectionContainer { Text(requireNotNull(documents)[selected], style = MaterialTheme.typography.bodySmall) }
                    else {
                        val links = remember { Regex("\\[([^]]+)]\\(([^)]+)\\)") }
                        requireNotNull(documents)[0].split("\n\n").forEach { paragraph ->
                            when {
                                paragraph.startsWith("# ") -> Unit // The dialog already supplies the title.
                                paragraph.startsWith("| ") -> paragraph.lines().drop(2).forEach { row ->
                                    val cells = row.trim().trim('|').split('|').map(String::trim)
                                    if (cells.size == 3) Column {
                                        Text(cells[0], style = MaterialTheme.typography.titleSmall)
                                        Text(cells[2], style = MaterialTheme.typography.bodySmall)
                                        links.find(cells[1])?.let { link ->
                                            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.groupValues[2]))) } }) {
                                                Text(link.groupValues[1])
                                            }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                                else -> Text(links.replace(paragraph) { it.groupValues[1] }, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}
