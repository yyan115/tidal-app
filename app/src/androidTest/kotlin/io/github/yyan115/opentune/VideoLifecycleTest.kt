package io.github.yyan115.opentune

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yyan115.opentune.ui.PauseWhileStopped
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A video pauses when its activity stops being visible, which is how a closed picture-in-picture window arrives. */
@RunWith(AndroidJUnit4::class)
class VideoLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun thePlayerPausesWhenTheActivityStopsAndPlaysOnWhileItIsOnlyPaused() {
        lateinit var player: ExoPlayer
        compose.runOnUiThread { player = ExoPlayer.Builder(compose.activity).build().apply { playWhenReady = true } }
        try {
            compose.setContent { PauseWhileStopped(player) }
            compose.waitForIdle()
            // An activity in picture-in-picture is paused and still visible, so the video plays on.
            compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            compose.runOnUiThread { assertTrue(player.playWhenReady) }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.runOnUiThread { assertFalse(player.playWhenReady) }
        } finally { compose.runOnUiThread { player.release() } }
    }
}
