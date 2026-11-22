package io.github.yyan115.opentune.ui

import android.os.Build
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Clears the bar that the system draws behind the three buttons in the window of a sheet or a pop-up. Each of these windows has a bar of its own,
 * and the system draws a veil over it unless the window says that it needs none. The strip then has the colour of what lies under it.
 */
@Composable
fun ClearNavigationBar() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        @Suppress("DEPRECATION") window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }
}

/** A sheet that rises from the bottom. It always opens at its full size, and its bottom edge reaches the edge of the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ClearNavigationBar()
        content()
    }
}
