package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.PlaybackState
import com.example.core.model.VideoItem
import com.example.core.playback.ActivePlaybackResolver
import com.example.core.playback.FoxPlayerEngine
import com.example.core.playback.PlaybackService
import com.example.core.playback.PlayerEngine
import com.example.ui.player.PlayerScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Verifies that the player screen observes the playback state of the session it
 * actually commands.
 *
 * The screen used to pin the engine that existed at its first composition and
 * read the state from it, while the commands went to a separately resolved peer.
 * A torn down or replaced session could therefore keep reporting a stale state.
 * These tests drive the engine published by [PlaybackService] and assert what the
 * screen displays.
 *
 * The published engine always wraps the engine of the running service, so the
 * player it exposes is the very player the session hosts. The screen therefore
 * commands and observes one and the same playback session, exactly as in
 * production.
 *
 * The command target is covered as well: a controller whose session is gone must
 * never be commanded, and a command must reach the engine that is live now
 * instead of the session that was torn down or replaced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerScreenPlaybackSourceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var serviceController: ServiceController<PlaybackService>
    private lateinit var publishedEngine: ControllableEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        startService()
        // The engine of the running service is wrapped so the test controls the
        // reported state while the player stays the player of the live session.
        publishedEngine = ControllableEngine(PlaybackService.currentEngine!!)
        PlaybackService.publishEngine(publishedEngine)
    }

    @After
    fun tearDown() {
        // The published engine is process wide state: it must not leak into other
        // test classes.
        PlaybackService.publishEngine(null)
        serviceController.destroy()
    }

    private fun startService(): PlaybackService {
        serviceController = Robolectric.buildService(PlaybackService::class.java)
        return serviceController.create().get()
    }

    @Test
    fun theScreenDisplaysTheStateOfThePublishedEngine() {
        publishedEngine.publish(
            PlaybackState(
                isPlaying = false,
                currentPositionMs = 30_000L,
                durationMs = 60_000L
            )
        )

        composeTestRule.setContent {
            PlayerScreen()
        }

        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("01:00")
        composeTestRule.onNodeWithTag("player_hud_current_position").assertTextEquals("00:30")
        composeTestRule.onNodeWithTag("player_hud_timeline_slider").assertIsEnabled()
    }

    @Test
    fun theScreenFollowsTheEngineWhenTheServiceEngineIsReplaced() {
        publishedEngine.publish(PlaybackState(currentPositionMs = 30_000L, durationMs = 60_000L))

        composeTestRule.setContent {
            PlayerScreen()
        }

        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("01:00")

        // The service is torn down and a new one takes over.
        serviceController.destroy()
        composeTestRule.waitForIdle()

        startService()
        val recreatedEngine = ControllableEngine(PlaybackService.currentEngine!!)
        recreatedEngine.publish(PlaybackState(currentPositionMs = 5_000L, durationMs = 120_000L))
        PlaybackService.publishEngine(recreatedEngine)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("02:00")
        composeTestRule.onNodeWithTag("player_hud_current_position").assertTextEquals("00:05")
    }

    @Test
    fun anObsoleteEngineCannotOverwriteTheStateOfTheActiveEngine() {
        publishedEngine.publish(PlaybackState(currentPositionMs = 30_000L, durationMs = 60_000L))

        composeTestRule.setContent {
            PlayerScreen()
        }

        // A new engine takes over the published slot.
        val activeEngine = ControllableEngine(PlaybackService.currentEngine!!)
        activeEngine.publish(PlaybackState(currentPositionMs = 5_000L, durationMs = 120_000L))
        PlaybackService.publishEngine(activeEngine)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("02:00")

        // The replaced engine keeps publishing: its state must not reach the screen.
        publishedEngine.publish(PlaybackState(currentPositionMs = 999_000L, durationMs = 999_000L))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("02:00")
        composeTestRule.onNodeWithTag("player_hud_current_position").assertTextEquals("00:05")
    }

    @Test
    fun theStaleStateIsDroppedWhenTheEngineIsWithdrawn() {
        publishedEngine.publish(PlaybackState(currentPositionMs = 30_000L, durationMs = 60_000L))

        composeTestRule.setContent {
            PlayerScreen()
        }

        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("01:00")

        // The service is torn down: no session is alive any more.
        PlaybackService.publishEngine(null)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("00:00")
        composeTestRule.onNodeWithTag("player_hud_current_position").assertTextEquals("00:00")
        composeTestRule.onNodeWithTag("player_hud_timeline_slider").assertIsNotEnabled()
    }

    @Test
    fun theScreenKeepsReportingTheLatestEngineAcrossRepeatedSwaps() {
        publishedEngine.publish(PlaybackState(currentPositionMs = 30_000L, durationMs = 60_000L))

        composeTestRule.setContent {
            PlayerScreen()
        }

        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("01:00")

        val secondEngine = ControllableEngine(PlaybackService.currentEngine!!)
        secondEngine.publish(PlaybackState(currentPositionMs = 5_000L, durationMs = 120_000L))
        PlaybackService.publishEngine(secondEngine)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("02:00")

        // A third session takes over: only the latest state may be displayed.
        val thirdEngine = ControllableEngine(PlaybackService.currentEngine!!)
        thirdEngine.publish(PlaybackState(currentPositionMs = 0L, durationMs = 300_000L))
        PlaybackService.publishEngine(thirdEngine)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("05:00")
        composeTestRule.onNodeWithTag("player_hud_current_position").assertTextEquals("00:00")
    }

    @Test
    fun theCommandsOfTheScreenReachThePublishedEngine() {
        val sessionPlayer = PlaybackService.currentEngine!!.player

        composeTestRule.setContent {
            PlayerScreen()
        }

        composeTestRule.onNodeWithTag("player_hud_play_pause_button").assertIsDisplayed()

        // The play command must be dispatched to the player of the published
        // engine, which is the very peer the screen observes.
        composeTestRule.onNodeWithTag("player_hud_play_pause_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(
            "The play command must reach the player of the published engine",
            sessionPlayer.playWhenReady
        )
    }

    @Test
    fun aDisconnectedControllerIsNeverSelectedAsTheCommandTarget() {
        val staleController = ExoPlayer.Builder(context).build()
        val engine = FoxPlayerEngine(context)
        val engineView = ControllableEngine(engine)
        try {
            val source = ActivePlaybackResolver.resolve(staleController, engineView) { false }

            assertSame(
                "A controller that lost its session must never be the command target",
                engine.player,
                source.player
            )
            assertSame(
                "The observed state must still come from the active engine",
                engineView.playbackState,
                source.playbackState
            )
        } finally {
            staleController.release()
            engine.release()
        }
    }

    /**
     * The screen depends on two facts about a controller whose session died: the
     * controller reports itself as disconnected and it notifies its listener.
     * The first fact lets the resolution reject such a controller, the second one
     * lets the screen drop it and ask for a controller that reaches the new
     * session. Both facts are pinned down here, because a controller that kept
     * reporting itself connected would silently stay the command target of a
     * session that no longer exists.
     */
    @Test
    fun aControllerReportsItselfDisconnectedWhenItsSessionDies() {
        val sessionToken = PlaybackService.currentSessionToken
        assertNotNull("the running service must publish its session token", sessionToken)

        val disconnected = AtomicBoolean(false)
        val controllerFuture = MediaController.Builder(context, sessionToken!!)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    disconnected.set(true)
                }
            })
            .buildAsync()
        composeTestRule.waitForIdle()
        // A bounded wait: the connection is local, so it either completes or
        // fails, and a timeout reports the failure instead of hanging the run.
        val controller = controllerFuture.get(30, TimeUnit.SECONDS)

        assertTrue("the controller must be connected to the live session", controller.isConnected)

        // The service is torn down: the session of the controller is gone.
        serviceController.destroy()
        composeTestRule.waitForIdle()

        assertTrue(
            "the controller must be notified when its session dies",
            disconnected.get()
        )
        assertFalse(
            "a controller whose session died must not report itself connected",
            controller.isConnected
        )
    }

    /**
     * The screen must stop commanding a controller whose session is gone.
     *
     * The media session of the service dies while the service keeps its engine
     * alive, so neither the controller nor the engine changes for the screen: the
     * only signal that the session is gone is the disconnection notification of
     * the controller. The screen has to drop that controller, otherwise it keeps
     * commanding a proxy of a session that no longer exists and its commands are
     * swallowed.
     *
     * The destruction of the service cannot cover this: it withdraws the engine
     * together with the session, and the change of the engine alone already
     * re-resolves the active peer.
     */
    @Test
    fun theScreenDropsTheControllerOfADeadSessionWhileTheEngineStaysAlive() {
        val livePlayer = PlaybackService.currentEngine!!.player
        publishedEngine.publish(PlaybackState(currentPositionMs = 30_000L, durationMs = 60_000L))

        composeTestRule.setContent {
            PlayerScreen()
        }
        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("01:00")

        // The media session dies while the service keeps its engine alive.
        val session = serviceController.get().onGetSession(testControllerInfo())
        assertNotNull("the running service must expose its media session", session)
        session!!.release()
        composeTestRule.waitForIdle()

        // A real UI action must reach the player of the live engine and must not
        // be swallowed by the controller of the dead session.
        composeTestRule.onNodeWithTag("player_hud_play_pause_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(
            "The command must reach the player of the live engine",
            livePlayer.playWhenReady
        )
    }

    /**
     * The identity of a controller that asks the service for its session.
     */
    private fun testControllerInfo(): MediaSession.ControllerInfo =
        MediaSession.ControllerInfo.createTestOnlyControllerInfo(
            "com.example",
            Process.myUid(),
            0,
            0,
            MediaSession.ControllerInfo.LEGACY_CONTROLLER_INTERFACE_VERSION,
            false,
            Bundle()
        )

    @Test
    fun theCommandReachesTheActiveEngineAfterTheControllerSessionIsLost() {
        val obsoleteSessionPlayer = PlaybackService.currentEngine!!.player
        publishedEngine.publish(PlaybackState(currentPositionMs = 30_000L, durationMs = 60_000L))

        composeTestRule.setContent {
            PlayerScreen()
        }
        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("01:00")

        // The service is torn down: the session of the screen's controller is gone.
        serviceController.destroy()
        composeTestRule.waitForIdle()

        // A new engine takes over the published slot while the screen may still
        // hold a reference to the controller of the dead session.
        val replacement = FoxPlayerEngine(context)
        try {
            val replacementEngine = ControllableEngine(replacement)
            replacementEngine.publish(PlaybackState(currentPositionMs = 5_000L, durationMs = 120_000L))
            PlaybackService.publishEngine(replacementEngine)
            composeTestRule.waitForIdle()

            // The observed state comes from the engine that is live now.
            composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("02:00")
            composeTestRule.onNodeWithTag("player_hud_current_position").assertTextEquals("00:05")

            // A real UI action must reach the player of the live engine and must not be
            // swallowed by the controller of the dead session.
            composeTestRule.onNodeWithTag("player_hud_play_pause_button").performClick()
            composeTestRule.waitForIdle()

            assertTrue(
                "The command must reach the player of the active engine",
                replacement.player.playWhenReady
            )
            assertFalse(
                "The obsolete controller must not receive the command",
                obsoleteSessionPlayer.playWhenReady
            )
        } finally {
            replacement.release()
        }
    }

    @Test
    fun theCommandReachesTheNewEngineAfterTheServiceEngineIsReplaced() {
        val obsoletePlayer = PlaybackService.currentEngine!!.player
        publishedEngine.publish(PlaybackState(currentPositionMs = 30_000L, durationMs = 60_000L))

        composeTestRule.setContent {
            PlayerScreen()
        }
        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("01:00")

        // The service is torn down and a new one takes over with a new session.
        serviceController.destroy()
        composeTestRule.waitForIdle()
        startService()
        val newEngine = ControllableEngine(PlaybackService.currentEngine!!)
        newEngine.publish(PlaybackState(currentPositionMs = 5_000L, durationMs = 120_000L))
        PlaybackService.publishEngine(newEngine)
        composeTestRule.waitForIdle()

        // The displayed state corresponds to the new engine.
        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("02:00")
        composeTestRule.onNodeWithTag("player_hud_current_position").assertTextEquals("00:05")

        // A real UI action must reach the player of the new engine.
        composeTestRule.onNodeWithTag("player_hud_play_pause_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(
            "The command must reach the player of the new engine",
            newEngine.player.playWhenReady
        )
        assertFalse(
            "The obsolete player must not receive the command",
            obsoletePlayer.playWhenReady
        )
    }

    @Test
    fun theCommandReachesTheLatestEngineAcrossRepeatedServiceReplacements() {
        publishedEngine.publish(PlaybackState(currentPositionMs = 30_000L, durationMs = 60_000L))

        composeTestRule.setContent {
            PlayerScreen()
        }
        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("01:00")

        // First replacement.
        serviceController.destroy()
        composeTestRule.waitForIdle()
        startService()
        val firstReplacement = ControllableEngine(PlaybackService.currentEngine!!)
        firstReplacement.publish(PlaybackState(currentPositionMs = 5_000L, durationMs = 120_000L))
        PlaybackService.publishEngine(firstReplacement)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("02:00")

        // Second replacement.
        serviceController.destroy()
        composeTestRule.waitForIdle()
        startService()
        val secondReplacement = ControllableEngine(PlaybackService.currentEngine!!)
        secondReplacement.publish(PlaybackState(currentPositionMs = 0L, durationMs = 300_000L))
        PlaybackService.publishEngine(secondReplacement)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("05:00")

        // Only the latest engine may receive the command.
        composeTestRule.onNodeWithTag("player_hud_play_pause_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(
            "The command must reach the player of the latest engine",
            secondReplacement.player.playWhenReady
        )
        assertFalse(
            "The replaced engine must not receive the command",
            firstReplacement.player.playWhenReady
        )
    }

    @Test
    fun noCommandReachesTheSessionWhenTheEngineIsWithdrawn() {
        val sessionPlayer = PlaybackService.currentEngine!!.player
        publishedEngine.publish(PlaybackState(currentPositionMs = 30_000L, durationMs = 60_000L))

        composeTestRule.setContent {
            PlayerScreen()
        }
        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("01:00")

        // The engine is withdrawn while the screen still holds a controller.
        PlaybackService.publishEngine(null)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("player_hud_duration").assertTextEquals("00:00")
        composeTestRule.onNodeWithTag("player_hud_timeline_slider").assertIsNotEnabled()

        // Without a live engine no session is alive, so no command may be sent.
        composeTestRule.onNodeWithTag("player_hud_play_pause_button").performClick()
        composeTestRule.waitForIdle()

        assertFalse(
            "No command may reach the session of a withdrawn engine",
            sessionPlayer.playWhenReady
        )
    }

    /**
     * A view over the engine of the running service whose reported state is fully
     * controlled by the test. The player stays the player of the live session, so
     * the screen commands and observes one and the same playback session.
     */
    private class ControllableEngine(
        private val delegate: PlayerEngine
    ) : PlayerEngine {

        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

        var isReleased: Boolean = false
            private set

        fun publish(state: PlaybackState) {
            _playbackState.value = state
        }

        override val player: Player get() = delegate.player
        override val isPlaying: Boolean get() = _playbackState.value.isPlaying
        override val currentPositionMs: Long get() = _playbackState.value.currentPositionMs
        override val durationMs: Long get() = _playbackState.value.durationMs
        override val playbackSpeed: Float get() = _playbackState.value.playbackSpeed
        override val volume: Float get() = _playbackState.value.volume

        override fun prepare(uri: Uri, playWhenReady: Boolean) = Unit
        override fun prepare(videoItem: VideoItem, playWhenReady: Boolean) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun playPause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun seekForward(offsetMs: Long) = Unit
        override fun seekBackward(offsetMs: Long) = Unit
        override fun setPlaybackSpeed(speed: Float) = Unit
        override fun setVolume(volume: Float) = Unit
        override fun stop() = Unit
        override fun release() {
            isReleased = true
        }
    }
}
