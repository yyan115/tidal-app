package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yyan115.opentune.core.Contributor

/** The share of the height of the screen that the sheet takes, whether it shows the spinner, a message or the credits. */
private const val SHEET_SHARE = 0.6f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsSheet(credits: CreditsUi, onClose: () -> Unit, onPerson: (Contributor) -> Unit) {
    AppSheet(onDismissRequest = onClose) {
        // The sheet has the same height in every state, so that it does not shrink when the spinner gives way to a short message.
        Column(Modifier.fillMaxHeight(SHEET_SHARE).navigationBarsPadding().padding(horizontal = 24.dp).testTag("credits-sheet")) {
            Text(stringResource(R.string.credits_title), style = AppFonts.title(32, 38))
            Text(credits.title, Modifier.padding(top = 4.dp, bottom = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    credits.loading -> Spinner(Modifier.align(Alignment.Center))
                    credits.error != null -> Text(credits.error, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    credits.credits.isEmpty() -> Text(stringResource(R.string.credits_none), Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) { // scroll: transient (a sheet that closes with its content)
                        items(credits.credits, key = { it.role }) { credit ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                UpperLabel(credit.role, size = 12.sp)
                                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    credit.people.forEach { person ->
                                        if (person.id.isNotBlank()) Text(person.name, Modifier.clickable { onPerson(person) }.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                        else Text(person.name, Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
