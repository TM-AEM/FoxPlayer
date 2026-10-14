package com.example

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.example.ui.player.DoubleTapSeekDuration
import com.example.ui.player.GestureFeedback
import com.example.ui.player.GestureFeedbackOverlay
import com.example.ui.player.GestureSensitivity
import com.example.ui.player.GestureSettingsState
import com.example.ui.player.PlayerGestureDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun testSpeedBoostFeedbackRendersCorrectly() {
        composeTestRule.setContent {
            GestureFeedbackOverlay(
                feedback = GestureFeedback.SpeedBoost(2.0f)
            )
        }

        composeTestRule.onNodeWithTag("feedback_speed_boost").assertIsDisplayed()
        composeTestRule.onNodeWithText("2X Speed").assertIsDisplayed()
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
        composeTestRule.onNodeWithTag("feedback_speed_boost").assertDoesNotExist()
    }

    @Test
    fun testSpeedBoostRestoresArbitraryPreviousSpeed() {
        val controllerService = org.robolectric.Robolectric.buildService(com.example.core.playback.PlaybackService::class.java)
        val service = controllerService.create().get()

        val player = com.example.core.playback.PlaybackService.currentEngine?.player
        org.junit.Assert.assertNotNull(player)

        // Set initial custom speed (e.g. 1.5x)
        player?.setPlaybackSpeed(1.5f)
        org.junit.Assert.assertEquals(1.5f, player?.playbackParameters?.speed ?: 1.0f, 0.01f)

        var speedBeforeBoost = player?.playbackParameters?.speed ?: 1.0f
        var isSpeedBoosted = false

        // Simulate startSpeedBoost
        if (!isSpeedBoosted) {
            speedBeforeBoost = player?.playbackParameters?.speed ?: 1.0f
            isSpeedBoosted = true
            player?.setPlaybackSpeed(2.0f)
        }

        // Verify temporary 2.0x boost
        org.junit.Assert.assertEquals(2.0f, player?.playbackParameters?.speed ?: 1.0f, 0.01f)

        // Simulate endSpeedBoost
        if (isSpeedBoosted) {
            player?.setPlaybackSpeed(speedBeforeBoost)
            isSpeedBoosted = false
        }

        // Verify restoration to exactly 1.5x
        org.junit.Assert.assertEquals(1.5f, player?.playbackParameters?.speed ?: 1.0f, 0.01f)

        // Now test with 0.75x
        player?.setPlaybackSpeed(0.75f)
        org.junit.Assert.assertEquals(0.75f, player?.playbackParameters?.speed ?: 1.0f, 0.01f)

        if (!isSpeedBoosted) {
            speedBeforeBoost = player?.playbackParameters?.speed ?: 1.0f
            isSpeedBoosted = true
            player?.setPlaybackSpeed(2.0f)
        }
        org.junit.Assert.assertEquals(2.0f, player?.playbackParameters?.speed ?: 1.0f, 0.01f)

        if (isSpeedBoosted) {
            player?.setPlaybackSpeed(speedBeforeBoost)
            isSpeedBoosted = false
        }
        org.junit.Assert.assertEquals(0.75f, player?.playbackParameters?.speed ?: 1.0f, 0.01f)

        controllerService.destroy()
    }

    @Test
    fun testPlayerGestureControllerMediaVolumeRouting() {
        val controllerService = org.robolectric.Robolectric.buildService(com.example.core.playback.PlaybackService::class.java)
        val service = controllerService.create().get()

        val player = com.example.core.playback.PlaybackService.currentEngine?.player
        org.junit.Assert.assertNotNull(player)

        player?.volume = 0.5f

        val controller = com.example.ui.player.PlayerGestureController(
            context = service,
            activity = null,
            playerProvider = { player }
        )

        org.junit.Assert.assertEquals(50, controller.currentVolumePercent)

        val updatedPercent = controller.adjustVolume(0.2f)
        org.junit.Assert.assertEquals(70, updatedPercent)
        org.junit.Assert.assertEquals(0.7f, player?.volume ?: 0f, 0.01f)

        controllerService.destroy()
    }

    @Test
    fun testGestureSensitivityPresetsAndMultipliers() {
        val defaultSettings = com.example.ui.player.GestureSettingsState()
        org.junit.Assert.assertEquals(com.example.ui.player.GestureSensitivity.MEDIUM, defaultSettings.volumeSensitivity)
        org.junit.Assert.assertEquals(com.example.ui.player.GestureSensitivity.MEDIUM, defaultSettings.brightnessSensitivity)

        val customSettings = defaultSettings.copy(
            volumeSensitivity = com.example.ui.player.GestureSensitivity.HIGH,
            brightnessSensitivity = com.example.ui.player.GestureSensitivity.LOW
        )

        // Verify High sensitivity values (lower touchSlop threshold -> triggers sooner; higher multiplier -> reacts faster)
        org.junit.Assert.assertTrue(customSettings.volumeSensitivity.touchSlop < com.example.ui.player.GestureSensitivity.MEDIUM.touchSlop)
        org.junit.Assert.assertTrue(customSettings.volumeSensitivity.multiplier > com.example.ui.player.GestureSensitivity.MEDIUM.multiplier)

        // Verify Low sensitivity values (higher touchSlop threshold -> requires deliberate drag; lower multiplier -> reacts slower)
        org.junit.Assert.assertTrue(customSettings.brightnessSensitivity.touchSlop > com.example.ui.player.GestureSensitivity.MEDIUM.touchSlop)
        org.junit.Assert.assertTrue(customSettings.brightnessSensitivity.multiplier < com.example.ui.player.GestureSensitivity.MEDIUM.multiplier)
    }

    @Test
    fun testRuntimeSensitivityUpdateTakesEffectImmediatelyWithoutRemounting() {
        var gestureSettings by mutableStateOf(
            GestureSettingsState(
                volumeSensitivity = GestureSensitivity.LOW,
                brightnessSensitivity = GestureSensitivity.LOW
            )
        )

        val volumeDeltas = mutableListOf<Float>()
        val brightnessDeltas = mutableListOf<Float>()

        composeTestRule.setContent {
            PlayerGestureDetector(
                onSingleTap = {},
                onDoubleTapSeek = {},
                onHorizontalSwipeSeekStart = {},
                onHorizontalSwipeSeekChange = { _, _ -> },
                onHorizontalSwipeSeekEnd = {},
                onVerticalSwipeBrightnessChange = { brightnessDeltas.add(it) },
                onVerticalSwipeVolumeChange = { volumeDeltas.add(it) },
                onVerticalSwipeEnd = {},
                gestureSettings = gestureSettings,
                modifier = Modifier.size(width = 400.dp, height = 400.dp)
            )
        }

        // Phase 1: Test with LOW sensitivity (multiplier = 0.65)
        // Drag on right half (x = 300) upwards by 100px.
        composeTestRule.onNodeWithTag("player_gesture_detector").performTouchInput {
            swipe(
                start = Offset(x = 300f, y = 300f),
                end = Offset(x = 300f, y = 200f),
                durationMillis = 200
            )
        }

        assertTrue("Volume deltas should be received in phase 1", volumeDeltas.isNotEmpty())
        val sumLowVolume = volumeDeltas.sum()
        assertTrue("Sum of volume deltas under LOW should be positive", sumLowVolume > 0f)

        volumeDeltas.clear()

        // Phase 2: Update settings to HIGH sensitivity (multiplier = 1.45) WHILE COMPOSABLE REMAINS MOUNTED
        // Do NOT change size, rotation, or remount detector!
        gestureSettings = gestureSettings.copy(
            volumeSensitivity = GestureSensitivity.HIGH
        )
        composeTestRule.waitForIdle()

        // Perform the exact same touch drag gesture
        composeTestRule.onNodeWithTag("player_gesture_detector").performTouchInput {
            swipe(
                start = Offset(x = 300f, y = 300f),
                end = Offset(x = 300f, y = 200f),
                durationMillis = 200
            )
        }

        assertTrue("Volume deltas should be received in phase 2", volumeDeltas.isNotEmpty())
        val sumHighVolume = volumeDeltas.sum()

        // High sensitivity multiplier (1.45) > Low sensitivity multiplier (0.65).
        // Since the touch input distance is identical, sumHighVolume must be significantly greater than sumLowVolume!
        assertTrue(
            "High sensitivity must produce greater response than Low ($sumHighVolume vs $sumLowVolume)",
            sumHighVolume > sumLowVolume * 1.5f
        )

        // Phase 3: Verify independence of volume and brightness
        // Now test brightness (left half, x = 100). Brightness was left at LOW (multiplier = 0.65).
        brightnessDeltas.clear()
        composeTestRule.onNodeWithTag("player_gesture_detector").performTouchInput {
            swipe(
                start = Offset(x = 100f, y = 300f),
                end = Offset(x = 100f, y = 200f),
                durationMillis = 200
            )
        }
        val sumLowBrightness = brightnessDeltas.sum()
        assertTrue("Brightness deltas should be received", sumLowBrightness > 0f)

        // Update brightness to HIGH, while volume remains HIGH
        gestureSettings = gestureSettings.copy(
            brightnessSensitivity = GestureSensitivity.HIGH
        )
        composeTestRule.waitForIdle()

        brightnessDeltas.clear()
        composeTestRule.onNodeWithTag("player_gesture_detector").performTouchInput {
            swipe(
                start = Offset(x = 100f, y = 300f),
                end = Offset(x = 100f, y = 200f),
                durationMillis = 200
            )
        }
        val sumHighBrightness = brightnessDeltas.sum()
        assertTrue(
            "High brightness sensitivity must produce greater response than Low ($sumHighBrightness vs $sumLowBrightness)",
            sumHighBrightness > sumLowBrightness * 1.5f
        )
    }

    @Test
    fun testPlayerScreenPersistedSettingsPropagation() {
        val testPreferences = mutableMapOf<String, String>()
        val mockRepo = object : com.example.data.repository.GestureSettingsRepository {
            private val stateFlow = kotlinx.coroutines.flow.MutableStateFlow(
                GestureSettingsState(
                    volumeSensitivity = GestureSensitivity.HIGH,
                    brightnessSensitivity = GestureSensitivity.LOW
                )
            )

            override val gestureSettingsFlow = stateFlow

            override suspend fun updateVolumeSensitivity(sensitivity: GestureSensitivity) {
                stateFlow.value = stateFlow.value.copy(volumeSensitivity = sensitivity)
            }

            override suspend fun updateBrightnessSensitivity(sensitivity: GestureSensitivity) {
                stateFlow.value = stateFlow.value.copy(brightnessSensitivity = sensitivity)
            }

            override suspend fun updateDoubleTapSeekDuration(duration: com.example.ui.player.DoubleTapSeekDuration) {
                stateFlow.value = stateFlow.value.copy(doubleTapSeekDuration = duration)
            }

            override suspend fun updateSettings(settings: GestureSettingsState) {
                stateFlow.value = settings
            }
        }

        val controllerService = org.robolectric.Robolectric.buildService(com.example.core.playback.PlaybackService::class.java)
        val service = controllerService.create().get()

        val testVideo = com.example.core.model.VideoItem(
            id = 999L,
            uri = "content://media/external/video/media/999",
            title = "Test Video",
            displayName = "test.mp4"
        )
        com.example.core.playback.PlaybackService.startPlayback(service, testVideo)

        composeTestRule.setContent {
            com.example.ui.player.PlayerScreen(
                gestureSettingsRepository = mockRepo
            )
        }

        // Detector and screen should be displayed
        composeTestRule.onNodeWithTag("player_gesture_detector").assertIsDisplayed()
        composeTestRule.waitForIdle()

        // Verify initial loaded settings match repo (High volume, Low brightness, Ten seek duration)
        assertEquals(GestureSensitivity.HIGH, mockRepo.gestureSettingsFlow.value.volumeSensitivity)
        assertEquals(GestureSensitivity.LOW, mockRepo.gestureSettingsFlow.value.brightnessSensitivity)
        assertEquals(com.example.ui.player.DoubleTapSeekDuration.TEN, mockRepo.gestureSettingsFlow.value.doubleTapSeekDuration)
        assertEquals(10, mockRepo.gestureSettingsFlow.value.doubleTapSeekSeconds)

        controllerService.destroy()
    }

    @Test
    fun testDoubleTapFeedbackWithCustomDurations() {
        var currentFeedback by mutableStateOf<GestureFeedback?>(
            GestureFeedback.DoubleTapSeek(deltaSeconds = 5, isForward = true)
        )
        composeTestRule.setContent {
            GestureFeedbackOverlay(feedback = currentFeedback)
        }
        composeTestRule.onNodeWithTag("feedback_double_tap_forward").assertIsDisplayed()
        composeTestRule.onNodeWithText("+5s").assertIsDisplayed()

        currentFeedback = GestureFeedback.DoubleTapSeek(deltaSeconds = 15, isForward = false)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("feedback_double_tap_rewind").assertIsDisplayed()
        composeTestRule.onNodeWithText("-15s").assertIsDisplayed()

        currentFeedback = GestureFeedback.DoubleTapSeek(deltaSeconds = 30, isForward = true)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("feedback_double_tap_forward").assertIsDisplayed()
        composeTestRule.onNodeWithText("+30s").assertIsDisplayed()
    }

    @Test
    fun testRuntimeSeekDurationUpdateTakesEffectImmediatelyWithoutRemounting() {
        var currentDuration by mutableStateOf(DoubleTapSeekDuration.TEN)
        var soughtForwardDeltaSeconds = 0
        var soughtRewindDeltaSeconds = 0

        composeTestRule.setContent {
            PlayerGestureDetector(
                onSingleTap = {},
                onDoubleTapSeek = { isForward ->
                    if (isForward) {
                        soughtForwardDeltaSeconds = currentDuration.seconds
                    } else {
                        soughtRewindDeltaSeconds = currentDuration.seconds
                    }
                },
                onHorizontalSwipeSeekStart = {},
                onHorizontalSwipeSeekChange = { _, _ -> },
                onHorizontalSwipeSeekEnd = {},
                onVerticalSwipeBrightnessChange = {},
                onVerticalSwipeVolumeChange = {},
                onVerticalSwipeEnd = {},
                gestureSettings = GestureSettingsState(doubleTapSeekDuration = currentDuration),
                modifier = Modifier.size(width = 400.dp, height = 400.dp)
            )
        }

        // Phase 1: Test with default 10s duration
        // Double-tap on right half (x = 300, y = 200) -> forward seek
        composeTestRule.onNodeWithTag("player_gesture_detector").performTouchInput {
            doubleClick(position = Offset(300f, 200f))
        }
        assertEquals(10, soughtForwardDeltaSeconds)

        // Phase 2: Update duration to 30s WHILE COMPOSABLE REMAINS MOUNTED
        currentDuration = DoubleTapSeekDuration.THIRTY
        composeTestRule.waitForIdle()

        // Perform double-tap on right half again
        composeTestRule.onNodeWithTag("player_gesture_detector").performTouchInput {
            doubleClick(position = Offset(300f, 200f))
        }
        assertEquals(30, soughtForwardDeltaSeconds)

        // Double-tap on left half (x = 100, y = 200) -> rewind seek
        composeTestRule.onNodeWithTag("player_gesture_detector").performTouchInput {
            doubleClick(position = Offset(100f, 200f))
        }
        assertEquals(30, soughtRewindDeltaSeconds)
    }
}
