package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
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
 * Regression protection for the long-press 2x speed boost wired by
 * [PlayerScreen].
 *
 * The boost is applied and restored by the screen itself, on the active player.
 * A test that only re-implements the two speed assignments cannot catch a broken
 * gesture wiring, so this test drives the production long-press gesture and
 * observes the commands the screen sends to that player.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerScreenSpeedBoostTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var recordingPlayer: RecordingPlayer
    private lateinit var engine: TestPlayerEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // The screen must use the engine's player directly. A leftover session
        // token would make it connect a controller from a previous test.
        PlaybackService.currentSessionToken = null

        exoPlayer = ExoPlayer.Builder(context).build()
        exoPlayer.setPlaybackSpeed(1.5f)
        recordingPlayer = RecordingPlayer(exoPlayer)
        engine = TestPlayerEngine(recordingPlayer) { exoPlayer.release() }
        PlaybackService.publishEngine(engine)
    }

    @After
    fun tearDown() {
        PlaybackService.publishEngine(null)
        PlaybackService.currentSessionToken = null
        engine.release()
    }

    @Test
    fun aLongPressAppliesTheBoostAndRestoresThePreviousSpeed() {
        composeTestRule.setContent {
            PlayerScreen(gestureSettingsRepository = NoopGestureSettingsRepository)
        }

        composeTestRule.onNodeWithTag("player_gesture_detector").performTouchInput {
            // Keep clear of the centered HUD play/pause button and the bottom
            // timeline so the long press is handled by the gesture layer.
            longClick(centerLeft)
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "The long press must raise the active player to 2x and release must restore the previous speed",
            listOf(2.0f, 1.5f),
            recordingPlayer.playbackSpeeds
        )
        assertEquals(
            "The production player must end at the speed it had before the boost",
            1.5f,
            exoPlayer.playbackParameters.speed,
            0.01f
        )
    }

    @Test
    fun repeatedLongPressesRestoreTheConfiguredSpeedEveryTime() {
        composeTestRule.setContent {
            PlayerScreen(gestureSettingsRepository = NoopGestureSettingsRepository)
        }

        composeTestRule.onNodeWithTag("player_gesture_detector").performTouchInput {
            longClick(centerLeft)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_gesture_detector").performTouchInput {
            longClick(centerLeft)
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "Every completed boost must return to the configured speed, not to a boosted value",
            listOf(2.0f, 1.5f, 2.0f, 1.5f),
            recordingPlayer.playbackSpeeds
        )
        assertEquals(1.5f, exoPlayer.playbackParameters.speed, 0.01f)
    }
}
