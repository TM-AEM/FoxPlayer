package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.player.GestureFeedback
import com.example.ui.player.GestureFeedbackOverlay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerGestureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testDoubleTapForwardFeedbackRendersCorrectly() {
        composeTestRule.setContent {
            GestureFeedbackOverlay(
                feedback = GestureFeedback.DoubleTapSeek(deltaSeconds = 10, isForward = true)
            )
        }

        composeTestRule.onNodeWithTag("feedback_double_tap_forward").assertIsDisplayed()
        composeTestRule.onNodeWithText("+10s").assertIsDisplayed()
    }

    @Test
    fun testDoubleTapRewindFeedbackRendersCorrectly() {
        composeTestRule.setContent {
            GestureFeedbackOverlay(
                feedback = GestureFeedback.DoubleTapSeek(deltaSeconds = 10, isForward = false)
            )
        }

        composeTestRule.onNodeWithTag("feedback_double_tap_rewind").assertIsDisplayed()
        composeTestRule.onNodeWithText("-10s").assertIsDisplayed()
    }

    @Test
    fun testSwipeSeekFeedbackRendersCorrectly() {
        composeTestRule.setContent {
            GestureFeedbackOverlay(
                feedback = GestureFeedback.SwipeSeek(
                    targetPositionMs = 45_000L,
                    deltaMs = 15_000L,
                    durationMs = 120_000L
                )
            )
        }

        composeTestRule.onNodeWithTag("feedback_swipe_seek").assertIsDisplayed()
        composeTestRule.onNodeWithText("+15s").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:45 / 02:00").assertIsDisplayed()
    }

    @Test
    fun testVolumeFeedbackRendersCorrectly() {
        composeTestRule.setContent {
            GestureFeedbackOverlay(
                feedback = GestureFeedback.Volume(volumePercent = 75)
            )
        }

        composeTestRule.onNodeWithTag("feedback_volume_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Volume").assertIsDisplayed()
        composeTestRule.onNodeWithText("75%").assertIsDisplayed()
    }

    @Test
    fun testBrightnessFeedbackRendersCorrectly() {
        composeTestRule.setContent {
            GestureFeedbackOverlay(
                feedback = GestureFeedback.Brightness(brightnessPercent = 40)
            )
        }

        composeTestRule.onNodeWithTag("feedback_brightness_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Brightness").assertIsDisplayed()
        composeTestRule.onNodeWithText("40%").assertIsDisplayed()
    }

    @Test
    fun testFeedbackHiddenWhenNull() {
        composeTestRule.setContent {
            GestureFeedbackOverlay(feedback = null)
        }

        composeTestRule.onNodeWithTag("feedback_double_tap_forward").assertDoesNotExist()
        composeTestRule.onNodeWithTag("feedback_double_tap_rewind").assertDoesNotExist()
        composeTestRule.onNodeWithTag("feedback_swipe_seek").assertDoesNotExist()
        composeTestRule.onNodeWithTag("feedback_volume_indicator").assertDoesNotExist()
        composeTestRule.onNodeWithTag("feedback_brightness_indicator").assertDoesNotExist()
    }
}
