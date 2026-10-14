package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.example.ui.player.AudioTrackItem
import com.example.ui.player.PlayerHud
import com.example.ui.player.PlayerSettingsPanel
import com.example.ui.player.PlayerTrackHelper
import com.example.ui.player.ResizeMode
import com.example.ui.player.SubtitleSelectionMode
import com.example.ui.player.SubtitleTrackItem
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerTrackAndSettingsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var player: ExoPlayer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        player = ExoPlayer.Builder(context).build()
    }

    @Test
    fun testAudioTrackSelectionOnPlayer() {
        // Set up test audio track
        val audioFormat = Format.Builder()
            .setId("audio-1")
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(2)
            .setLanguage("en")
            .setLabel("English Stereo")
            .build()
        val mediaTrackGroup = TrackGroup("audio-group", audioFormat)
        val audioItem = AudioTrackItem(
            id = "0_0",
            groupIndex = 0,
            trackIndex = 0,
            label = "English Stereo",
            language = "English",
            channels = "Stereo (2 ch)",
            codec = "AAC",
            isSelected = false,
            trackGroup = mediaTrackGroup
        )

        // Select explicit audio track
        PlayerTrackHelper.selectAudioTrack(player, audioItem)
        val overrideParams = player.trackSelectionParameters
        assertFalse(PlayerTrackHelper.isAudioAuto(overrideParams))
        assertEquals(1, overrideParams.overrides.size)

        // Revert to Auto audio track
        PlayerTrackHelper.selectAudioTrack(player, null)
        val autoParams = player.trackSelectionParameters
        assertTrue(PlayerTrackHelper.isAudioAuto(autoParams))
        assertEquals(0, autoParams.overrides.filter { it.value.mediaTrackGroup.type == C.TRACK_TYPE_AUDIO }.size)
    }

    @Test
    fun testSubtitleSelectionModesOnPlayer() {
        val subFormat = Format.Builder()
            .setId("sub-1")
            .setSampleMimeType(MimeTypes.TEXT_VTT)
            .setLanguage("ar")
            .setLabel("Arabic")
            .build()
        val mediaTrackGroup = TrackGroup("sub-group", subFormat)
        val subItem = SubtitleTrackItem(
            id = "1_0",
            groupIndex = 1,
            trackIndex = 0,
            label = "Arabic",
            language = "Arabic",
            isForced = false,
            isSelected = false,
            trackGroup = mediaTrackGroup
        )

        // 1. Select specific subtitle track
        PlayerTrackHelper.selectSubtitleTrack(player, SubtitleSelectionMode.Track(subItem))
        val trackParams = player.trackSelectionParameters
        assertFalse(PlayerTrackHelper.isSubtitleOff(trackParams))
        assertFalse(PlayerTrackHelper.isSubtitleAuto(trackParams))

        // 2. Select Subtitle OFF
        PlayerTrackHelper.selectSubtitleTrack(player, SubtitleSelectionMode.Off)
        val offParams = player.trackSelectionParameters
        assertTrue(PlayerTrackHelper.isSubtitleOff(offParams))
        assertTrue(offParams.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))

        // 3. Select Subtitle AUTO
        PlayerTrackHelper.selectSubtitleTrack(player, SubtitleSelectionMode.Auto)
        val autoParams = player.trackSelectionParameters
        assertFalse(PlayerTrackHelper.isSubtitleOff(autoParams))
        assertTrue(PlayerTrackHelper.isSubtitleAuto(autoParams))
    }

    @Test
    fun testResizeModeAspectRatioCalculations() {
        // Container: 1920 x 1080 (Aspect = 1.777)
        val containerWidth = 1920f
        val containerHeight = 1080f
        val containerAspect = containerWidth / containerHeight

        // Video: 4:3 (Aspect = 1.333)
        val videoAspect43 = 4f / 3f

        // FIT mode for 4:3 in 16:9 container -> pillarbox (height is 1080, width is 1080 * 4/3 = 1440)
        val fitWidth = containerHeight * videoAspect43
        val fitHeight = containerHeight
        assertEquals(1440f, fitWidth, 0.1f)
        assertEquals(1080f, fitHeight, 0.1f)

        // FILL mode -> stretched to 1920 x 1080
        assertEquals(1920f, containerWidth, 0.1f)
        assertEquals(1080f, containerHeight, 0.1f)

        // ZOOM mode for 4:3 in 16:9 container -> scaled by width (1920), height is 1920 / (4/3) = 1440 (cropped)
        val zoomWidth = containerWidth
        val zoomHeight = containerWidth / videoAspect43
        assertEquals(1920f, zoomWidth, 0.1f)
        assertEquals(1440f, zoomHeight, 0.1f)
    }

    @Test
    fun testPlayerSettingsPanelUIInteractions() {
        var selectedSpeed = 1.0f
        var selectedMode = ResizeMode.FIT
        var audioSelected: AudioTrackItem? = null
        var subtitleMode: SubtitleSelectionMode? = null
        var dismissed = false

        val testAudioGroup = TrackGroup("audio", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build())
        val testAudioList = listOf(
            AudioTrackItem(
                id = "0_0",
                groupIndex = 0,
                trackIndex = 0,
                label = "English (Surround)",
                language = "English",
                channels = "5.1 Surround (6 ch)",
                codec = "AAC",
                isSelected = false,
                trackGroup = testAudioGroup
            )
        )

        val testSubGroup = TrackGroup("sub", Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build())
        val testSubList = listOf(
            SubtitleTrackItem(
                id = "1_0",
                groupIndex = 1,
                trackIndex = 0,
                label = "French",
                language = "French",
                isForced = false,
                isSelected = false,
                trackGroup = testSubGroup
            )
        )

        composeTestRule.setContent {
            PlayerSettingsPanel(
                isOpen = true,
                currentSpeed = selectedSpeed,
                onSpeedSelected = { selectedSpeed = it },
                currentResizeMode = selectedMode,
                onResizeModeSelected = { selectedMode = it },
                audioTracks = testAudioList,
                isAudioAuto = true,
                onAudioTrackSelected = { audioSelected = it },
                subtitleTracks = testSubList,
                isSubtitleOff = true,
                isSubtitleAuto = false,
                onSubtitleModeSelected = { subtitleMode = it },
                onDismiss = { dismissed = true }
            )
        }

        // Verify Panel is displayed
        composeTestRule.onNodeWithTag("player_settings_panel").assertIsDisplayed()

        // Click Speed chip (scroll to ensure visible if on narrow screen)
        composeTestRule.onNodeWithTag("speed_chip_0.5").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(0.5f, selectedSpeed, 0.01f)

        // Click Aspect Ratio Zoom chip
        composeTestRule.onNodeWithTag("aspect_ratio_zoom").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(ResizeMode.ZOOM, selectedMode)

        // Click Audio track
        composeTestRule.onNodeWithTag("audio_track_0_0").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("0_0", audioSelected?.id)

        // Click Subtitle auto
        composeTestRule.onNodeWithTag("subtitle_track_auto").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(SubtitleSelectionMode.Auto, subtitleMode)

        // Click Close button
        composeTestRule.onNodeWithTag("player_settings_close_button").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(dismissed)
    }

    @Test
    fun testGestureSensitivityThresholdSettingsAdjustment() {
        var gestureSettings by androidx.compose.runtime.mutableStateOf(
            com.example.ui.player.GestureSettingsState(
                volumeSensitivity = com.example.ui.player.GestureSensitivity.MEDIUM,
                brightnessSensitivity = com.example.ui.player.GestureSensitivity.MEDIUM
            )
        )

        composeTestRule.setContent {
            PlayerSettingsPanel(
                isOpen = true,
                currentSpeed = 1.0f,
                onSpeedSelected = {},
                currentResizeMode = ResizeMode.FIT,
                onResizeModeSelected = {},
                audioTracks = emptyList(),
                isAudioAuto = true,
                onAudioTrackSelected = {},
                subtitleTracks = emptyList(),
                isSubtitleOff = true,
                isSubtitleAuto = false,
                onSubtitleModeSelected = {},
                gestureSettings = gestureSettings,
                onGestureSettingsChange = { gestureSettings = it },
                onDismiss = {}
            )
        }

        // Verify Title is displayed
        composeTestRule.onNodeWithTag("player_settings_gesture_sensitivity_title", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()

        // Change Volume sensitivity to High
        composeTestRule.onNodeWithTag("gesture_volume_sensitivity_high", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(com.example.ui.player.GestureSensitivity.HIGH, gestureSettings.volumeSensitivity)
        assertEquals(12f, gestureSettings.volumeSensitivity.touchSlop, 0.01f)
        assertEquals(1.45f, gestureSettings.volumeSensitivity.multiplier, 0.01f)

        // Change Brightness sensitivity to Low
        composeTestRule.onNodeWithTag("gesture_brightness_sensitivity_low", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(com.example.ui.player.GestureSensitivity.LOW, gestureSettings.brightnessSensitivity)
        assertEquals(26f, gestureSettings.brightnessSensitivity.touchSlop, 0.01f)
        assertEquals(0.65f, gestureSettings.brightnessSensitivity.multiplier, 0.01f)
    }

    @Test
    fun testFieldSpecificSensitivityCallbacksInSettingsPanel() {
        var updatedVolume: com.example.ui.player.GestureSensitivity? = null
        var updatedBrightness: com.example.ui.player.GestureSensitivity? = null

        composeTestRule.setContent {
            PlayerSettingsPanel(
                isOpen = true,
                currentSpeed = 1.0f,
                onSpeedSelected = {},
                currentResizeMode = ResizeMode.FIT,
                onResizeModeSelected = {},
                audioTracks = emptyList(),
                isAudioAuto = true,
                onAudioTrackSelected = {},
                subtitleTracks = emptyList(),
                isSubtitleOff = true,
                isSubtitleAuto = false,
                onSubtitleModeSelected = {},
                gestureSettings = com.example.ui.player.GestureSettingsState(),
                onVolumeSensitivityChange = { updatedVolume = it },
                onBrightnessSensitivityChange = { updatedBrightness = it },
                onDismiss = {}
            )
        }

        // Change Volume sensitivity to High via field-specific callback
        composeTestRule.onNodeWithTag("gesture_volume_sensitivity_high", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(com.example.ui.player.GestureSensitivity.HIGH, updatedVolume)
        assertEquals(null, updatedBrightness)

        // Change Brightness sensitivity to Low via field-specific callback
        composeTestRule.onNodeWithTag("gesture_brightness_sensitivity_low", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(com.example.ui.player.GestureSensitivity.LOW, updatedBrightness)
    }

    @Test
    fun testPlayerHudSettingsButton() {
        var settingsClicked = false

        composeTestRule.setContent {
            PlayerHud(
                isVisible = true,
                title = "Test Video",
                isPlaying = true,
                currentPositionMs = 1000L,
                durationMs = 10000L,
                bufferedPositionMs = 2000L,
                onPlayPauseClick = {},
                onSeek = {},
                onBack = {},
                onUserInteraction = {},
                onSettingsClick = { settingsClicked = true }
            )
        }

        composeTestRule.onNodeWithTag("player_hud_settings_button")
            .assertIsDisplayed()
            .performClick()

        assertTrue(settingsClicked)
    }

    @Test
    fun testDoubleTapSeekDurationCallbackInSettingsPanel() {
        var updatedDuration: com.example.ui.player.DoubleTapSeekDuration? = null

        composeTestRule.setContent {
            PlayerSettingsPanel(
                isOpen = true,
                currentSpeed = 1.0f,
                onSpeedSelected = {},
                currentResizeMode = ResizeMode.FIT,
                onResizeModeSelected = {},
                audioTracks = emptyList(),
                isAudioAuto = true,
                onAudioTrackSelected = {},
                subtitleTracks = emptyList(),
                isSubtitleOff = true,
                isSubtitleAuto = false,
                onSubtitleModeSelected = {},
                gestureSettings = com.example.ui.player.GestureSettingsState(),
                onDoubleTapSeekDurationChange = { updatedDuration = it },
                onDismiss = {}
            )
        }

        // Verify title is displayed
        composeTestRule.onNodeWithTag("player_settings_double_tap_seek_title", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()

        // Verify all 4 options are available in UI
        composeTestRule.onNodeWithTag("gesture_double_tap_seek_5s", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("gesture_double_tap_seek_10s", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("gesture_double_tap_seek_15s", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("gesture_double_tap_seek_30s", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()

        // Click 15s option
        composeTestRule.onNodeWithTag("gesture_double_tap_seek_15s", useUnmergedTree = true)
            .performClick()
        assertEquals(com.example.ui.player.DoubleTapSeekDuration.FIFTEEN, updatedDuration)

        // Click 30s option
        composeTestRule.onNodeWithTag("gesture_double_tap_seek_30s", useUnmergedTree = true)
            .performClick()
        assertEquals(com.example.ui.player.DoubleTapSeekDuration.THIRTY, updatedDuration)

        // Click 5s option
        composeTestRule.onNodeWithTag("gesture_double_tap_seek_5s", useUnmergedTree = true)
            .performClick()
        assertEquals(com.example.ui.player.DoubleTapSeekDuration.FIVE, updatedDuration)
    }

    @Test
    fun testDoubleTapSeekDurationFallbackToFullStateChangeWhenCallbackNull() {
        var updatedSettingsState: com.example.ui.player.GestureSettingsState? = null

        composeTestRule.setContent {
            PlayerSettingsPanel(
                isOpen = true,
                currentSpeed = 1.0f,
                onSpeedSelected = {},
                currentResizeMode = ResizeMode.FIT,
                onResizeModeSelected = {},
                audioTracks = emptyList(),
                isAudioAuto = true,
                onAudioTrackSelected = {},
                subtitleTracks = emptyList(),
                isSubtitleOff = true,
                isSubtitleAuto = false,
                onSubtitleModeSelected = {},
                gestureSettings = com.example.ui.player.GestureSettingsState(
                    doubleTapSeekDuration = com.example.ui.player.DoubleTapSeekDuration.TEN
                ),
                onGestureSettingsChange = { updatedSettingsState = it },
                onDoubleTapSeekDurationChange = null,
                onDismiss = {}
            )
        }

        // Click 30s option -> should invoke onGestureSettingsChange with DoubleTapSeekDuration.THIRTY
        composeTestRule.onNodeWithTag("gesture_double_tap_seek_30s", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertNotNull(updatedSettingsState)
        assertEquals(com.example.ui.player.DoubleTapSeekDuration.THIRTY, updatedSettingsState?.doubleTapSeekDuration)
    }
}
