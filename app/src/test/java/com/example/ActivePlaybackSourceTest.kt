package com.example

import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.PlaybackState
import com.example.core.model.VideoItem
import com.example.core.playback.ActivePlaybackResolver
import com.example.core.playback.PlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract of the single active playback peer.
 *
 * The player UI used to resolve its command target and its observed state through
 * two independent expressions, which allowed the displayed state to belong to a
 * different (or an already released) playback session than the one receiving the
 * commands. These tests pin the contract of the resolver that replaced them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActivePlaybackSourceTest {

    private lateinit var context: Context
    private lateinit var controller: ExoPlayer
    private lateinit var engine: FakePlayerEngine
    private lateinit var otherEngine: FakePlayerEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        controller = ExoPlayer.Builder(context).build()
        engine = FakePlayerEngine(ExoPlayer.Builder(context).build())
        otherEngine = FakePlayerEngine(ExoPlayer.Builder(context).build())
    }

    @After
    fun tearDown() {
        controller.release()
        engine.release()
        otherEngine.release()
    }

    @Test
    fun theControllerAndTheEngineOfTheSameSessionAreResolvedTogether() {
        val source = ActivePlaybackResolver.resolve(controller, engine)

        assertSame(
            "The connected controller must be the command target",
            controller,
            source.player
        )
        assertSame(
            "The observed state must belong to the engine of the same session",
            engine.playbackState,
            source.playbackState
        )
    }

    @Test
    fun theEngineIsUsedForBothCommandsAndStateWhenNoControllerIsAvailable() {
        val source = ActivePlaybackResolver.resolve(null, engine)

        assertSame(
            "The engine player must be the command target when no controller exists",
            engine.player,
            source.player
        )
        assertSame(
            "The observed state must come from the very engine that receives commands",
            engine.playbackState,
            source.playbackState
        )
    }

    @Test
    fun noSessionIsReportedWhenNoEngineIsPublished() {
        val source = ActivePlaybackResolver.resolve(null, null)

        assertNull("Without an engine there is no live session to command", source.player)
        assertEquals(
            "Without an engine the default playback state must be reported",
            PlaybackState(),
            source.playbackState.value
        )
    }

    @Test
    fun aStaleControllerIsNotUsedAfterTheEngineIsGone() {
        val source = ActivePlaybackResolver.resolve(controller, null)

        assertNull(
            "A controller of a torn down session must not be used as command target",
            source.player
        )
        assertEquals(
            "No state of the stale session may be reported",
            PlaybackState(),
            source.playbackState.value
        )
    }

    @Test
    fun theNewEngineIsObservedAfterTheEngineWasReplaced() {
        val staleSource = ActivePlaybackResolver.resolve(null, engine)
        val activeSource = ActivePlaybackResolver.resolve(null, otherEngine)

        assertNotSame(
            "The replaced engine must not stay the observed state source",
            staleSource.playbackState,
            activeSource.playbackState
        )
        assertSame(
            "The new engine must be the observed state source",
            otherEngine.playbackState,
            activeSource.playbackState
        )
        assertSame(
            "The new engine must be the command target",
            otherEngine.player,
            activeSource.player
        )
    }

    @Test
    fun anObsoleteEngineCannotOverwriteTheStateOfTheActiveEngine() {
        val activeSource = ActivePlaybackResolver.resolve(null, otherEngine)

        otherEngine.publish(PlaybackState(currentPositionMs = 5_000L, durationMs = 120_000L))
        assertEquals(5_000L, activeSource.playbackState.value.currentPositionMs)

        // The replaced engine keeps running and publishes a new state.
        engine.publish(PlaybackState(currentPositionMs = 999_000L, durationMs = 999_000L))

        assertEquals(
            "The state of the obsolete engine must not reach the active source",
            5_000L,
            activeSource.playbackState.value.currentPositionMs
        )
        assertEquals(
            "The duration of the obsolete engine must not reach the active source",
            120_000L,
            activeSource.playbackState.value.durationMs
        )
    }

    @Test
    fun aControllerReconnectionKeepsTheSameStateSource() {
        val beforeReconnect = ActivePlaybackResolver.resolve(null, engine)
        val afterReconnect = ActivePlaybackResolver.resolve(controller, engine)

        assertSame(
            "A controller reconnection must not start a second state source",
            beforeReconnect.playbackState,
            afterReconnect.playbackState
        )
        assertSame(
            "The reconnected controller must become the command target",
            controller,
            afterReconnect.player
        )
        assertSame(
            "The engine must stay the state authority after a reconnection",
            engine.playbackState,
            afterReconnect.playbackState
        )
    }

    @Test
    fun theStateOfTheActiveEngineIsReported() {
        engine.publish(
            PlaybackState(
                isPlaying = true,
                currentPositionMs = 30_000L,
                durationMs = 60_000L
            )
        )

        val source = ActivePlaybackResolver.resolve(controller, engine)

        assertTrue("The published playing state must be reported", source.playbackState.value.isPlaying)
        assertEquals(30_000L, source.playbackState.value.currentPositionMs)
        assertEquals(60_000L, source.playbackState.value.durationMs)
    }

    /**
     * Minimal [PlayerEngine] whose published state is fully controlled by the test.
     */
    private class FakePlayerEngine(
        override val player: Player
    ) : PlayerEngine {

        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

        var isReleased: Boolean = false
            private set

        fun publish(state: PlaybackState) {
            _playbackState.value = state
        }

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
