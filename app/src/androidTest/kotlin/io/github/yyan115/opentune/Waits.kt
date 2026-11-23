package io.github.yyan115.opentune

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * A pop-up or a sheet has a window of its own, which a slow phone lays out a moment after the control that opens it was pressed. Its nodes can exist
 * before they are displayed, so a test that checks or presses them waits for this first.
 */
fun ComposeTestRule.waitUntilDisplayed(matcher: SemanticsMatcher, timeoutMillis: Long = 10_000) {
    waitUntil(timeoutMillis) { runCatching { onNode(matcher).assertIsDisplayed() }.isSuccess }
}
