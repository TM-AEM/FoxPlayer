package com.example

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.PlaybackState
import com.example.core.playback.PlaybackService
import com.example.ui.player.PlayerScreen
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression protection for the player HUD's auto-hide contract.
 *
 * The HUD hides itself after 3.5 seconds of playback, stays visible while paused,
 * and comes back on a tap. The timer and state live inside [PlayerScreen], so a
 * test of [com.example.ui.player.PlayerHud] alone cannot protect them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerScreenHudAutoHideTest {

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
    fun theHudHidesAfterPlaybackContinuesAndComesBackOnTap() {
        engine.publish(
            PlaybackState(
                isPlaying = true,
                currentPositionMs = 15_000L,
                durationMs = 60_000L
            )
        )

        composeTestRule.setContent {
            PlayerScreen(gestureSettingsRepository = NoopGestureSettingsRepository)
        }

        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsDisplayed()

        // The HUD must still be there before the 3.5 second inactivity budget.
        composeTestRule.mainClock.advanceTimeBy(3_000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsDisplayed()

        // Cross the budget and let the fade-out animation finish.
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsNotDisplayed()

        // A tap in a non-control corner must bring the HUD back. The clock is
        // paused so the just-shown HUD is not auto-hidden again before the
        // assertion, which would hide a regression in the tap handling.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("player_gesture_detector").performTouchInput {
            click(Offset(20f, 20f))
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsDisplayed()
    }

    @Test
    fun theHudStaysVisibleWhilePlaybackIsPaused() {
        engine.publish(
            PlaybackState(
                isPlaying = false,
                currentPositionMs = 15_000L,
                durationMs = 60_000L
            )
        )

        composeTestRule.setContent {
            PlayerScreen(gestureSettingsRepository = NoopGestureSettingsRepository)
        }

        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(5_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsDisplayed()

        // The auto-hide timer must start when playback actually begins, not only
        // when the screen is first composed.
        engine.publish(
            PlaybackState(
                isPlaying = true,
                currentPositionMs = 15_000L,
                durationMs = 60_000L
            )
        )
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(4_000)
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsNotDisplayed()
    }

    @Test
    fun theHudAutoHideBudgetRestartsAfterAnInteraction() {
        engine.publish(
            PlaybackState(
                isPlaying = true,
                currentPositionMs = 15_000L,
                durationMs = 60_000L
            )
        )

        composeTestRule.setContent {
            PlayerScreen(gestureSettingsRepository = NoopGestureSettingsRepository)
        }

        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsDisplayed()

        // The first inactivity budget expires while the clock runs.
        composeTestRule.mainClock.advanceTimeBy(4_000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsNotDisplayed()

        // A tap in a non-control corner brings the HUD back; the clock is
        // paused only so the just-shown HUD cannot hide before the assertion.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("player_gesture_detector").performTouchInput {
            click(Offset(20f, 20f))
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsDisplayed()

        // The budget restarted at the tap: 500 + 2_900 = 3_400ms are still
        // inside the 3.5 second window, so the HUD must remain visible.
        composeTestRule.mainClock.advanceTimeBy(2_900)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsDisplayed()

        // Crossing the restarted budget must hide the HUD again. This is the
        // proof that a tap restarts the timeout, which the paused-clock tap
        // assertion alone cannot give.
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.mainClock.advanceTimeBy(600) // let the fade-out finish
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsNotDisplayed()
    }
}
