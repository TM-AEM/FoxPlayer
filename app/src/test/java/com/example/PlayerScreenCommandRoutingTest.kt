package com.example

import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.PlaybackState
import com.example.core.playback.PlaybackService
import com.example.ui.player.PlayerScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression protection for the command path from the player gestures to the
 * active playback peer.
 *
 * The HUD play/pause command is covered by
 * [PlayerScreenPlaybackSourceTest]. The gesture seek path is separate: it reads
 * the position and duration from the observed playback state, computes a target
 * and sends `seekTo` to the active player. A future extraction of the gesture
 * layer must not break that path or route it to a stale peer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerScreenCommandRoutingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var engine: TestPlayerEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PlaybackService.currentSessionToken = null
        exoPlayer = ExoPlayer.Builder(context).build()
        engine = TestPlayerEngine(exoPlayer) { exoPlayer.release() }
        PlaybackService.publishEngine(engine)
    }

    @After
    fun tearDown() {
        PlaybackService.publishEngine(null)
        PlaybackService.currentSessionToken = null
        engine.release()
    }

    @Test
    fun aDoubleTapSeekReachesTheActivePlayer() {
        engine.publish(
            PlaybackState(
                isPlaying = true,
                currentPositionMs = 10_000L,
                durationMs = 60_000L
            )
        )

        composeTestRule.setContent {
            PlayerScreen(gestureSettingsRepository = NoopGestureSettingsRepository)
        }

        // Double-tap the right half (away from the centered HUD control) to
        // seek forward by the configured 10 seconds.
        composeTestRule.onNodeWithTag("player_gesture_detector").performTouchInput {
            doubleClick(percentOffset(0.75f, 0.5f))
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "The double-tap seek must be applied to the active player",
            20_000L,
            exoPlayer.currentPosition
        )
    }
}
