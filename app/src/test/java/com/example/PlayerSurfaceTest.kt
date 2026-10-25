package com.example

import android.content.Context
import android.view.SurfaceView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.example.ui.player.PlayerSurface
import com.example.ui.player.ResizeMode
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
 * Contract of the extracted [PlayerSurface].
 *
 * The component owns the SurfaceView it creates and keeps exactly one player
 * attached to it at any moment: a replacement receives the surface only after
 * the previous owner has been cleared, and disposal clears the player that
 * still owns the surface — never one that was replaced earlier.
 *
 * Limitation: these tests observe the Player API calls, i.e. ownership and
 * lifecycle outcomes at the observable API level. Robolectric has no
 * SurfaceFlinger, so actual rendering and BufferQueue producer state cannot be
 * verified here, and surface destruction cannot be dispatched deterministically
 * because SurfaceHolder callbacks are not publicly reachable. Those paths
 * require a physical device or emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerSurfaceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var surfaceEvents: MutableList<String>
    private lateinit var firstRecording: RecordingPlayer
    private var secondRecording: RecordingPlayer? = null
    private var thirdRecording: RecordingPlayer? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        surfaceEvents = mutableListOf()
        firstRecording = RecordingPlayer(
            ExoPlayer.Builder(context).build(),
            surfaceEvents,
            "A"
        )
    }

    @After
    fun tearDown() {
        thirdRecording?.release()
        secondRecording?.release()
        firstRecording.release()
    }

    @Test
    fun theSurfaceIsOwnedByOnePlayerAndTransferredBeforeTheReplacementAttaches() {
        var activePlayer by mutableStateOf<Player?>(firstRecording)
        var showSurface by mutableStateOf(true)

        composeTestRule.setContent {
            if (showSurface) {
                PlayerSurface(
                    activePlayer = activePlayer,
                    videoAspectRatio = 16f / 9f,
                    resizeMode = ResizeMode.FIT,
                    isInPipMode = false
                )
            }
        }

        // 1. Initial attachment: only the active player holds the surface.
        composeTestRule.onNodeWithTag("player_surface_view").assertIsDisplayed()
        composeTestRule.waitForIdle()

        val firstSurface = firstRecording.attachedSurfaces.lastOrNull()
        assertNotNull("The active player must receive the rendered SurfaceView", firstSurface)
        assertTrue(
            "The initial owner must not be cleared while it still owns the surface",
            firstRecording.detachedSurfaces.isEmpty()
        )

        // 2 + 3. Replacement of A with B: A is cleared before B attaches.
        val secondPlayer = RecordingPlayer(
            ExoPlayer.Builder(context).build(),
            surfaceEvents,
            "B"
        )
        secondRecording = secondPlayer
        activePlayer = secondPlayer
        composeTestRule.waitForIdle()

        assertSame(
            "The replacement must receive the very same SurfaceView",
            firstSurface,
            secondPlayer.attachedSurfaces.lastOrNull()
        )
        assertEquals(
            "The previous owner must be cleared exactly once during the transfer",
            listOf(firstSurface),
            firstRecording.detachedSurfaces
        )
        assertEquals(
            "Ownership must move as detach A then attach B, never attach B while A still owns the surface",
            listOf("A:attach", "A:detach", "B:attach"),
            surfaceEvents
        )

        // 4 + 7. Disposal after the replacement clears only the current owner;
        // the replaced player must never receive a stale cleanup.
        showSurface = false
        composeTestRule.waitForIdle()

        assertEquals(
            "Disposal must clear the surface from the player that owns it now",
            listOf(firstSurface),
            secondPlayer.detachedSurfaces
        )
        assertEquals(
            "The replaced player must not be cleared a second time on disposal",
            listOf(firstSurface),
            firstRecording.detachedSurfaces
        )
        assertEquals(
            "The event log must show exactly one ownership transfer and one disposal",
            listOf("A:attach", "A:detach", "B:attach", "B:detach"),
            surfaceEvents
        )
    }

    @Test
    fun consecutiveReplacementsTransferOwnershipExactlyOnceEachTime() {
        var activePlayer by mutableStateOf<Player?>(firstRecording)
        var showSurface by mutableStateOf(true)

        composeTestRule.setContent {
            if (showSurface) {
                PlayerSurface(
                    activePlayer = activePlayer,
                    videoAspectRatio = 16f / 9f,
                    resizeMode = ResizeMode.FIT,
                    isInPipMode = false
                )
            }
        }

        composeTestRule.waitForIdle()
        val surface: SurfaceView? = firstRecording.attachedSurfaces.lastOrNull()
        assertNotNull("The initial owner must receive the surface", surface)

        // 5. Two consecutive replacements while the component stays composed.
        val secondPlayer = RecordingPlayer(
            ExoPlayer.Builder(context).build(),
            surfaceEvents,
            "B"
        )
        secondRecording = secondPlayer
        activePlayer = secondPlayer
        composeTestRule.waitForIdle()

        val thirdPlayer = RecordingPlayer(
            ExoPlayer.Builder(context).build(),
            surfaceEvents,
            "C"
        )
        thirdRecording = thirdPlayer
        activePlayer = thirdPlayer
        composeTestRule.waitForIdle()

        assertSame(
            "The second player must receive the same surface",
            surface,
            secondPlayer.attachedSurfaces.lastOrNull()
        )
        assertSame(
            "The third player must receive the same surface",
            surface,
            thirdPlayer.attachedSurfaces.lastOrNull()
        )
        assertEquals(
            "Each replacement must take ownership from its predecessor exactly once",
            listOf("A:attach", "A:detach", "B:attach", "B:detach", "C:attach"),
            surfaceEvents
        )

        showSurface = false
        composeTestRule.waitForIdle()

        assertEquals(
            "Only the last owner may be cleared on disposal",
            listOf("A:attach", "A:detach", "B:attach", "B:detach", "C:attach", "C:detach"),
            surfaceEvents
        )
        assertEquals(listOf(surface), firstRecording.detachedSurfaces)
        assertEquals(listOf(surface), secondPlayer.detachedSurfaces)
        assertEquals(listOf(surface), thirdPlayer.detachedSurfaces)
    }

    @Test
    fun losingTheActivePlayerReleasesOwnershipAndTheNextOwnerStartsClean() {
        var activePlayer by mutableStateOf<Player?>(firstRecording)
        var showSurface by mutableStateOf(true)

        composeTestRule.setContent {
            if (showSurface) {
                PlayerSurface(
                    activePlayer = activePlayer,
                    videoAspectRatio = 16f / 9f,
                    resizeMode = ResizeMode.FIT,
                    isInPipMode = false
                )
            }
        }

        composeTestRule.waitForIdle()
        val surface: SurfaceView? = firstRecording.attachedSurfaces.lastOrNull()
        assertNotNull(surface)

        // No session is alive: the surface must not stay owned by the old player.
        activePlayer = null
        composeTestRule.waitForIdle()

        assertEquals(
            "Losing the active player must release the surface from its owner",
            listOf(surface),
            firstRecording.detachedSurfaces
        )
        assertEquals(
            listOf("A:attach", "A:detach"),
            surfaceEvents
        )

        // A new session takes over after the ownerless window.
        val secondPlayer = RecordingPlayer(
            ExoPlayer.Builder(context).build(),
            surfaceEvents,
            "B"
        )
        secondRecording = secondPlayer
        activePlayer = secondPlayer
        composeTestRule.waitForIdle()

        assertSame(
            "The new session must receive the surface after the ownerless window",
            surface,
            secondPlayer.attachedSurfaces.lastOrNull()
        )

        showSurface = false
        composeTestRule.waitForIdle()

        assertEquals(
            listOf("A:attach", "A:detach", "B:attach", "B:detach"),
            surfaceEvents
        )
        assertEquals(
            "The player released during the ownerless window must not be cleared again",
            listOf(surface),
            firstRecording.detachedSurfaces
        )
        assertEquals(listOf(surface), secondPlayer.detachedSurfaces)
    }
}
