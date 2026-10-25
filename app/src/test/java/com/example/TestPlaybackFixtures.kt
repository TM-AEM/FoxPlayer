package com.example

import android.net.Uri
import android.view.SurfaceView
import androidx.media3.common.Player
import com.example.core.model.AppSettings
import com.example.core.model.PlaybackState
import com.example.core.model.VideoItem
import com.example.core.playback.PlayerEngine
import com.example.data.repository.AppSettingsRepository
import com.example.data.repository.GestureSettingsRepository
import com.example.ui.player.DoubleTapSeekDuration
import com.example.ui.player.GestureSensitivity
import com.example.ui.player.GestureSettingsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A [PlayerEngine] whose reported state is controlled by the test while its
 * [player] remains the production player of the live session.
 */
internal class TestPlayerEngine(
    override val player: Player,
    private val releasePlayer: () -> Unit = {}
) : PlayerEngine {

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

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
    override fun release() = releasePlayer()
}

/**
 * A delegating [Player] that records the surface and speed commands the screen
 * sends to the active peer, so a test can verify routing and lifecycle without
 * replacing the production player implementation. When several recordings
 * share one [surfaceEvents] log, a test can also assert the cross-player
 * ordering of attach and detach events.
 */
internal class RecordingPlayer(
    private val delegate: Player,
    private val surfaceEvents: MutableList<String>? = null,
    private val name: String = "player"
) : Player by delegate {

    val attachedSurfaces = mutableListOf<SurfaceView?>()
    val detachedSurfaces = mutableListOf<SurfaceView?>()
    val playbackSpeeds = mutableListOf<Float>()

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        attachedSurfaces += surfaceView
        surfaceEvents?.add("$name:attach")
        delegate.setVideoSurfaceView(surfaceView)
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        detachedSurfaces += surfaceView
        surfaceEvents?.add("$name:detach")
        delegate.clearVideoSurfaceView(surfaceView)
    }

    override fun setPlaybackSpeed(speed: Float) {
        playbackSpeeds += speed
        delegate.setPlaybackSpeed(speed)
    }
}

/** A settings repository with no persisted state, used to isolate player tests. */
internal object NoopGestureSettingsRepository : GestureSettingsRepository {

    private val state = MutableStateFlow(GestureSettingsState())

    override val gestureSettingsFlow = state

    override suspend fun updateVolumeSensitivity(sensitivity: GestureSensitivity) = Unit
    override suspend fun updateBrightnessSensitivity(sensitivity: GestureSensitivity) = Unit
    override suspend fun updateDoubleTapSeekDuration(duration: DoubleTapSeekDuration) = Unit
    override suspend fun updateSettings(settings: GestureSettingsState) = Unit
}

/** A settings repository that always reports the given application settings. */
internal class FixedAppSettingsRepository(
    private val settings: AppSettings
) : AppSettingsRepository {

    override val appSettingsFlow = flowOf(settings)

    override suspend fun updateLanguage(language: com.example.core.model.AppLanguage) = Unit
    override suspend fun updateTheme(theme: com.example.core.model.AppTheme) = Unit
    override suspend fun updateResumePlayback(resume: Boolean) = Unit
}
