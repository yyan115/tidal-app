package io.github.yyan115.opentune.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The dialog of the app. It takes its colours, corner shape and title typeface from the theme, so each theme has its own dialog.
 * The parameters follow the dialog of Material, so a dialog changes over without changes to its content.
 * The main action is [DialogConfirm] and the other actions are [DialogDismiss]. The actions sit on the right.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        ClearNavigationBar()
        Surface(Modifier.widthIn(min = 280.dp, max = 560.dp).testTag("app-dialog"), shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 0.dp) {
            Column(Modifier.padding(24.dp)) {
                // The title has the size of the headline of the theme, which is the size that Material gives to the title of a dialog.
                if (title != null) CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) { Box(Modifier.padding(bottom = 16.dp)) { title() } }
                }
                // The text slot scrolls when it is taller than the screen, as for the settings of the scrobbling dialog.
                if (text != null) Box(Modifier.weight(1f, fill = false).heightIn(min = 0.dp)) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                    }
                }
                // With two actions the second one is drawn with an outline, so that the two read as a choice. A single action is a plain button.
                if (confirmButton != null || dismissButton != null) CompositionLocalProvider(LocalDialogChoice provides (confirmButton != null && dismissButton != null)) {
                    FlowRow(Modifier.padding(top = 24.dp).align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dismissButton?.invoke()
                        confirmButton?.invoke()
                    }
                }
            }
        }
    }
}

/**
 * The main action of a dialog, as a filled button. A [destructive] action, such as a deletion, uses the error colour of the theme,
 * and its label names the action, for example "Delete".
 */
@Composable
fun DialogConfirm(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
        else ButtonDefaults.buttonColors()
    Button(onClick, modifier.heightIn(min = 40.dp), enabled, shape = MaterialTheme.shapes.small, colors = colors, content = content)
}

/** Whether the dialog offers a choice between two actions. */
private val LocalDialogChoice = compositionLocalOf { false }

/** An action of a dialog that is not the main one, such as Cancel. Next to a main action it has an outline, and on its own it is a plain text button. */
@Composable
fun DialogDismiss(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    if (LocalDialogChoice.current) OutlinedButton(onClick, modifier.heightIn(min = 40.dp).testTag("dialog-outlined-action"), enabled, shape = MaterialTheme.shapes.small, content = content)
    else TextButton(onClick, modifier.heightIn(min = 40.dp), enabled, shape = MaterialTheme.shapes.small, content = content)
}
