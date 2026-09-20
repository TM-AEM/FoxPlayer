package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.player.PlayerHud
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerHudTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testHudDisplaysAllControlsWhenVisible() {
        composeTestRule.setContent {
            PlayerHud(
                isVisible = true,
                title = "Nature Documentary",
                isPlaying = false,
                currentPositionMs = 65000L, // 01:05
                durationMs = 180000L,       // 03:00
                bufferedPositionMs = 90000L,
                onPlayPauseClick = {},
                onSeek = {},
                onBack = {},
                onUserInteraction = {}
            )
        }

        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_hud_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_hud_title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nature Documentary").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_hud_play_pause_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_hud_timeline_slider").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_hud_current_position").assertIsDisplayed()
        composeTestRule.onNodeWithText("01:05").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_hud_duration").assertIsDisplayed()
        composeTestRule.onNodeWithText("03:00").assertIsDisplayed()
    }

    @Test
    fun testHudHiddenWhenNotVisible() {
        composeTestRule.setContent {
            PlayerHud(
                isVisible = false,
                title = "Nature Documentary",
                isPlaying = true,
                currentPositionMs = 1000L,
                durationMs = 50000L,
                bufferedPositionMs = 2000L,
                onPlayPauseClick = {},
                onSeek = {},
                onBack = {},
                onUserInteraction = {}
            )
        }

        composeTestRule.onNodeWithTag("player_hud_overlay").assertDoesNotExist()
    }

    @Test
    fun testPlayPauseClickDispatched() {
        var clicked = false
        composeTestRule.setContent {
            PlayerHud(
                isVisible = true,
                title = "Test Video",
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = 10000L,
                bufferedPositionMs = 0L,
                onPlayPauseClick = { clicked = true },
                onSeek = {},
                onBack = {},
                onUserInteraction = {}
            )
        }

        composeTestRule.onNodeWithTag("player_hud_play_pause_button").performClick()
        assertTrue("Click event must be dispatched", clicked)
    }

    @Test
    fun testBackButtonClickDispatched() {
        var backClicked = false
        composeTestRule.setContent {
            PlayerHud(
                isVisible = true,
                title = "Test Video",
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = 10000L,
                bufferedPositionMs = 0L,
                onPlayPauseClick = {},
                onSeek = {},
                onBack = { backClicked = true },
                onUserInteraction = {}
            )
        }

        composeTestRule.onNodeWithTag("player_hud_back_button").performClick()
        assertTrue("Back click event must be dispatched", backClicked)
    }

    @Test
    fun testZeroAndNegativeDurationHandling() {
        composeTestRule.setContent {
            PlayerHud(
                isVisible = true,
                title = "",
                isPlaying = false,
                currentPositionMs = -100L,
                durationMs = 0L,
                bufferedPositionMs = 0L,
                onPlayPauseClick = {},
                onSeek = {},
                onBack = {},
                onUserInteraction = {}
            )
        }

        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_hud_current_position").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_hud_duration").assertIsDisplayed()
        // Video default title
        composeTestRule.onNodeWithText("Video").assertIsDisplayed()
    }
}
