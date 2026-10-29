package io.github.yyan115.undertow.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LicensesDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val notices by produceState<String?>(null) {
        value = withContext(Dispatchers.IO) {
            listOf("THIRD_PARTY_NOTICES.md", "LICENSE", "Apache-2.0.txt").joinToString("\n\n") { name ->
                context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
            }
        }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("Open-source licenses") }, text = {
        if (notices == null) CircularProgressIndicator()
        else SelectionContainer { Column(Modifier.verticalScroll(rememberScrollState())) { Text(notices.orEmpty(), style = MaterialTheme.typography.bodySmall) } }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}
