package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.example.core.playback.PlaybackService
import com.example.ui.player.PlayerScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression protection for the Surface the player screen owns.
 *
 * The screen must attach its Surface to the player it currently commands, and
 * release that Surface from that same player when it leaves the composition.
 * A stale active player must not be the one that receives the cleanup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerScreenSurfaceLifecycleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: android.content.Context
    private var firstPlayer: ExoPlayer? = null
    private var secondPlayer: ExoPlayer? = null
    private lateinit var firstRecording: RecordingPlayer
    private var secondRecording: RecordingPlayer? = null
    private lateinit var firstEngine: TestPlayerEngine
    private var secondEngine: TestPlayerEngine? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PlaybackService.currentSessionToken = null

        firstPlayer = ExoPlayer.Builder(context).build()
        firstRecording = RecordingPlayer(firstPlayer!!)
        firstEngine = TestPlayerEngine(firstRecording)
        PlaybackService.publishEngine(firstEngine)
    }

    @After
    fun tearDown() {
        PlaybackService.publishEngine(null)
        PlaybackService.currentSessionToken = null
        secondEngine?.release()
        firstEngine.release()
        secondPlayer?.release()
        firstPlayer?.release()
    }

    @Test
    fun theSurfaceFollowsTheActivePlayerAndIsClearedFromTheLatestOne() {
        var showPlayer by mutableStateOf(true)

        composeTestRule.setContent {
            if (showPlayer) {
                PlayerScreen(gestureSettingsRepository = NoopGestureSettingsRepository)
            }
        }

        composeTestRule.onNodeWithTag("player_surface_view").assertIsDisplayed()
        composeTestRule.waitForIdle()

        val firstSurface = firstRecording.attachedSurfaces.lastOrNull()
        assertNotNull("The first active player must receive the screen's Surface", firstSurface)
        assertTrue(
            "The Surface must not be cleared while the player screen is still showing",
            firstRecording.detachedSurfaces.isEmpty()
        )

        // A replacement session takes over while the screen stays composed.
        secondPlayer = ExoPlayer.Builder(context).build()
        secondRecording = RecordingPlayer(secondPlayer!!)
        secondEngine = TestPlayerEngine(secondRecording!!)
        PlaybackService.publishEngine(secondEngine!!)
        composeTestRule.waitForIdle()

        assertNotNull(
            "The replacement player must receive the Surface",
            secondRecording!!.attachedSurfaces.lastOrNull()
        )
        assertSame(
            "The replacement must attach the same Surface the screen already owns",
            firstSurface,
            secondRecording!!.attachedSurfaces.lastOrNull()
        )
        assertEquals(
            "The replaced player must be cleared when ownership transfers",
            listOf(firstSurface),
            firstRecording.detachedSurfaces
        )

        // Leaving the screen must clear the Surface from the player that is
        // active now, not only from the player that was active at first composition.
        showPlayer = false
        composeTestRule.waitForIdle()

        assertTrue(
            "The Surface must be cleared from the latest active player when the screen exits",
            secondRecording!!.detachedSurfaces.contains(firstSurface)
        )
        assertEquals(
            "The replaced player must not receive a stale cleanup when the screen exits",
            listOf(firstSurface),
            firstRecording.detachedSurfaces
        )
    }
}
