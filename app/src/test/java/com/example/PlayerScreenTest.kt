package com.example

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.VideoItem
import com.example.core.playback.FoxPlayerEngine
import com.example.core.playback.PlaybackService
import com.example.ui.player.PlayerScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var serviceController: ServiceController<PlaybackService>
    private lateinit var service: PlaybackService

    @Before
    fun setUp() {
        serviceController = Robolectric.buildService(PlaybackService::class.java)
        service = serviceController.create().get()
    }

    @After
    fun tearDown() {
        serviceController.destroy()
    }

    @Test
    fun testPlayerScreenDisplaysRootAndSurface() {
        val testVideo = VideoItem(
            id = 101L,
            uri = "content://media/external/video/media/101",
            title = "Test Sunset",
            displayName = "sunset.mp4"
        )
        PlaybackService.startPlayback(service, testVideo)

        composeTestRule.setContent {
            PlayerScreen()
        }

        composeTestRule.onNodeWithTag("player_screen_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_surface_view").assertIsDisplayed()
    }

    @Test
    fun testPlayerScreenPreservesPlayerOnExit() {
        val testVideo = VideoItem(
            id = 202L,
            uri = "content://media/external/video/media/202",
            title = "Ocean Wave",
            displayName = "wave.mp4"
        )
        PlaybackService.startPlayback(service, testVideo)
        val initialPlayer = PlaybackService.currentEngine?.player
        assertNotNull(initialPlayer)

        var showPlayer by androidx.compose.runtime.mutableStateOf(true)

        // Open PlayerScreen
        composeTestRule.setContent {
            if (showPlayer) {
                PlayerScreen()
            }
        }

        composeTestRule.onNodeWithTag("player_surface_view").assertIsDisplayed()

        // Exit PlayerScreen (toggle state to dispose the screen)
        showPlayer = false
        composeTestRule.waitForIdle()

        // CRITICAL: Verify player was NOT released and engine is still active in service
        val engineAfterExit = PlaybackService.currentEngine
        assertNotNull("Engine must remain alive after leaving PlayerScreen", engineAfterExit)
        assertEquals("Player must remain the same instance", initialPlayer, engineAfterExit?.player)
    }

    @Test
    fun testPlayerScreenBufferingState() {
        val testVideo = VideoItem(
            id = 303L,
            uri = "content://media/external/video/media/303",
            title = "Buffering Stream",
            displayName = "stream.mp4"
        )
        PlaybackService.startPlayback(service, testVideo)

        // Simulate buffering status
        (PlaybackService.currentEngine as? FoxPlayerEngine)?.let { engine ->
            engine.exoPlayer.stop()
        }

        composeTestRule.setContent {
            PlayerScreen()
        }

        composeTestRule.onNodeWithTag("player_screen_root").assertIsDisplayed()
    }

    @Test
    fun testMediaSessionTokenCreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val componentName = ComponentName(context, PlaybackService::class.java)
        val sessionToken = SessionToken(context, componentName)

        assertNotNull(sessionToken)
        assertEquals(context.packageName, sessionToken.packageName)
        assertEquals(PlaybackService::class.java.name, sessionToken.serviceName)
    }

    @Test
    fun testPlayerScreenHudInteractionAndBackHandler() {
        val testVideo = VideoItem(
            id = 404L,
            uri = "content://media/external/video/media/404",
            title = "Mountain Peaks",
            displayName = "peaks.mp4"
        )
        PlaybackService.startPlayback(service, testVideo)

        var backInvoked = false

        composeTestRule.setContent {
            PlayerScreen(
                onBack = { backInvoked = true }
            )
        }

        // HUD and controls must be initially visible
        composeTestRule.onNodeWithTag("player_hud_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_hud_play_pause_button").assertIsDisplayed()

        // Clicking back button triggers onBack callback
        composeTestRule.onNodeWithTag("player_hud_back_button").performClick()
        org.junit.Assert.assertTrue("onBack should be called", backInvoked)
    }

    @Test
    fun testPlayerScreenGestureDetectorRendered() {
        val testVideo = VideoItem(
            id = 505L,
            uri = "content://media/external/video/media/505",
            title = "Ocean Waves",
            displayName = "waves.mp4"
        )
        PlaybackService.startPlayback(service, testVideo)

        composeTestRule.setContent {
            PlayerScreen()
        }

        // Gesture detector layer must be mounted and ready for touch input
        composeTestRule.onNodeWithTag("player_gesture_detector").assertIsDisplayed()
    }
}
