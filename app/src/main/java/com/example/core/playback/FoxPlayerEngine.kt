package com.example.core.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.core.model.PlaybackState
import com.example.core.model.PlaybackStatus
import com.example.core.model.VideoItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Production implementation of [PlayerEngine] backed by AndroidX Media3 [ExoPlayer].
 * Completely decoupled from UI, managing playback lifecycle, reactive state updates, and resource release.
 */
class FoxPlayerEngine(
    context: Context,
    val exoPlayer: ExoPlayer = buildExoPlayer(context),
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : PlayerEngine {

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    override val player: Player
        get() = exoPlayer

    override val isPlaying: Boolean
        get() = exoPlayer.isPlaying

    override val currentPositionMs: Long
        get() = exoPlayer.currentPosition.coerceAtLeast(0L)

    override val durationMs: Long
        get() = exoPlayer.duration.takeIf { it > 0 } ?: 0L

    override val playbackSpeed: Float
        get() = exoPlayer.playbackParameters.speed

    override val volume: Float
        get() = exoPlayer.volume

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState()
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                if (player.isPlaying) {
                    startProgressUpdates()
                } else {
                    stopProgressUpdates()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.update { current ->
                current.copy(
                    status = PlaybackStatus.ERROR,
                    isPlaying = false,
                    errorMessage = error.localizedMessage ?: "Playback error: ${error.errorCodeName}"
                )
            }
            stopProgressUpdates()
        }
    }

    init {
        exoPlayer.addListener(playerListener)
        updateState()
    }

    override fun prepare(uri: Uri, playWhenReady: Boolean) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.playWhenReady = playWhenReady
        exoPlayer.prepare()
    }

    override fun prepare(videoItem: VideoItem, playWhenReady: Boolean) {
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(videoItem.title.ifBlank { videoItem.displayName })
            .setDisplayTitle(videoItem.title.ifBlank { videoItem.displayName })
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(videoItem.uri))
            .setMediaId(videoItem.id.toString())
            .setMediaMetadata(mediaMetadata)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.playWhenReady = playWhenReady
        exoPlayer.prepare()
    }

    override fun play() {
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun playPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    override fun seekTo(positionMs: Long) {
        val dur = exoPlayer.duration
        val target = if (dur > 0) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
        exoPlayer.seekTo(target)
        updateState()
    }

    override fun seekForward(offsetMs: Long) {
        val dur = exoPlayer.duration
        val current = exoPlayer.currentPosition.coerceAtLeast(0L)
        val target = if (dur > 0) (current + offsetMs).coerceIn(0L, dur) else (current + offsetMs).coerceAtLeast(0L)
        exoPlayer.seekTo(target)
        updateState()
    }

    override fun seekBackward(offsetMs: Long) {
        val current = exoPlayer.currentPosition.coerceAtLeast(0L)
        val target = (current - offsetMs).coerceAtLeast(0L)
        exoPlayer.seekTo(target)
        updateState()
    }

    override fun setPlaybackSpeed(speed: Float) {
        val validSpeed = speed.coerceIn(0.25f, 4.0f)
        exoPlayer.setPlaybackSpeed(validSpeed)
        updateState()
    }

    override fun setVolume(volume: Float) {
        val validVolume = volume.coerceIn(0.0f, 1.0f)
        exoPlayer.volume = validVolume
        updateState()
    }

    override fun stop() {
        exoPlayer.stop()
        stopProgressUpdates()
        updateState()
    }

    override fun release() {
        stopProgressUpdates()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        scope.cancel()
    }

    private fun updateState() {
        val status = when (exoPlayer.playbackState) {
            Player.STATE_IDLE -> if (exoPlayer.playerError != null) PlaybackStatus.ERROR else PlaybackStatus.IDLE
            Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            Player.STATE_READY -> PlaybackStatus.READY
            Player.STATE_ENDED -> PlaybackStatus.ENDED
            else -> PlaybackStatus.IDLE
        }
        val dur = exoPlayer.duration.takeIf { it > 0 } ?: 0L
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val buffered = exoPlayer.bufferedPosition.coerceAtLeast(0L)

        _playbackState.update { current ->
            current.copy(
                status = status,
                isPlaying = exoPlayer.isPlaying,
                currentPositionMs = pos,
                durationMs = dur,
                bufferedPositionMs = buffered,
                playbackSpeed = exoPlayer.playbackParameters.speed,
                volume = exoPlayer.volume,
                errorMessage = exoPlayer.playerError?.localizedMessage
            )
        }
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive && exoPlayer.isPlaying) {
                val dur = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val buffered = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                _playbackState.update { current ->
                    current.copy(
                        currentPositionMs = pos,
                        durationMs = dur,
                        bufferedPositionMs = buffered
                    )
                }
                delay(250L)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    @OptIn(UnstableApi::class)
    companion object {
        fun buildExoPlayer(context: Context): ExoPlayer {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            return ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
        }
    }
}
