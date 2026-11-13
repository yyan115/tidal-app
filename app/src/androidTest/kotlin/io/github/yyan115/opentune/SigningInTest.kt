package io.github.yyan115.opentune

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.ui.SigningIn
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** While the sign-in finishes, a spinner with its caption covers the screen and takes every touch. */
@RunWith(AndroidJUnit4::class)
class SigningInTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theSpinnerShowsWithItsCaptionAndNothingBelowItCanBeTouched() {
        var clicked by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box { Button(onClick = { clicked = true }) { Text("Below") }; SigningIn() } } }
        compose.onNodeWithTag("signing-in").assertIsDisplayed()
        compose.onNodeWithText("Signing in").assertIsDisplayed()
        compose.onNodeWithText("Below").performClick()
        compose.waitForIdle()
        assertFalse("A touch must not pass through the spinner", clicked)
    }
}
