package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.core.model.AppTheme
import com.example.ui.player.PlayerHud
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "${RobolectricDeviceQualifiers.Pixel8} +night", sdk = [36])
class PlayerHudScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun playerHud_screenshot() {
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.setContent {
      MyApplicationTheme(
        appTheme = AppTheme.DARK,
        darkTheme = true,
        dynamicColor = false
      ) {
        PlayerHud(
          isVisible = true,
          title = "FoxPlayer",
          isPlaying = true,
          currentPositionMs = 65_000L,
          durationMs = 180_000L,
          bufferedPositionMs = 120_000L,
          onPlayPauseClick = {},
          onSeek = {},
          onBack = {},
          onUserInteraction = {}
        )
      }
    }
    composeTestRule.mainClock.advanceTimeBy(1_000)
    composeTestRule.waitForIdle()

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/player_hud.png")
  }
}
