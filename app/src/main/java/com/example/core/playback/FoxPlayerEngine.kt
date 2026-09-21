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
import com.example.data.local.database.FoxPlayerDatabase
import com.example.data.local.database.dao.HistoryDao
import com.example.data.local.database.entity.PlaybackHistoryEntity
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
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Production implementation of [PlayerEngine] backed by AndroidX Media3 [ExoPlayer].
 * Completely decoupled from UI, managing playback lifecycle, reactive state updates, and resource release.
 */
class FoxPlayerEngine(
    context: Context,
    val exoPlayer: ExoPlayer = buildExoPlayer(context),
    private val historyDao: HistoryDao? = try { FoxPlayerDatabase.getInstance(context).historyDao() } catch (_: Throwable) { null },
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PlayerEngine {

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var progressJob: Job? = null

    // Track active video item and history throttle state
    private var currentVideoItem: VideoItem? = null
    private var lastPersistedPositionMs: Long = -1L
    private var lastPersistedTimeMs: Long = 0L
    private var lastPersistedUri: String? = null

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
                    // Paused or stopped playing: save position once if changed by at least 1 second
                    val dur = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    if (abs(pos - lastPersistedPositionMs) >= 1000L) {
                        saveHistory(pos, dur, isEnded = player.playbackState == Player.STATE_ENDED)
                    }
                }
            }

            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                if (player.playbackState == Player.STATE_ENDED) {
                    val dur = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    saveHistory(pos, dur, isEnded = true)
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

    /**
     * Calculates whether playback should resume from [lastPositionMs] and returns the resume position.
     * Returns 0L if position is near start, near end, or invalid.
     */
    fun calculateResumePosition(lastPositionMs: Long, durationMs: Long): Long {
        if (lastPositionMs < 3000L) {
            // Watched less than 3 seconds: start from beginning
            return 0L
        }
        if (durationMs in 1L..15000L) {
            // Very short video (<= 15 seconds): don't resume if within 3 seconds of end or >= 80% watched
            if (lastPositionMs >= durationMs - 3000L || lastPositionMs >= (durationMs * 0.8f)) {
                return 0L
            }
        } else if (durationMs > 15000L) {
            // Standard video: don't resume if within 5 seconds of end or >= 95% watched
            if (lastPositionMs >= durationMs - 5000L || lastPositionMs >= (durationMs * 0.95f)) {
                return 0L
            }
        }
        return lastPositionMs
    }

    override fun prepare(uri: Uri, playWhenReady: Boolean) {
        val uriString = uri.toString()
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.playWhenReady = playWhenReady
        exoPlayer.prepare()

        // Asynchronously check history to resume position without blocking UI
        scope.launch(ioDispatcher) {
            val history = historyDao?.getHistoryByUriDirect(uriString)
            if (history != null) {
                val resumePos = calculateResumePosition(history.lastPositionMs, history.durationMs)
                if (resumePos > 0L) {
                    withContext(mainDispatcher) {
                        if (exoPlayer.currentMediaItem?.localConfiguration?.uri == uri) {
                            exoPlayer.seekTo(resumePos)
                            updateState()
                        }
                    }
                }
            }
        }
    }

    override fun prepare(videoItem: VideoItem, playWhenReady: Boolean) {
        currentVideoItem = videoItem
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

        // Asynchronously check history to resume position without blocking UI
        scope.launch(ioDispatcher) {
            val history = historyDao?.getHistoryByUriDirect(videoItem.uri)
            if (history != null) {
                val resumePos = calculateResumePosition(
                    history.lastPositionMs,
                    history.durationMs.takeIf { it > 0 } ?: videoItem.durationMs
                )
                if (resumePos > 0L) {
                    withContext(mainDispatcher) {
                        if (currentVideoItem?.uri == videoItem.uri) {
                            exoPlayer.seekTo(resumePos)
                            updateState()
                        }
                    }
                }
            }
        }
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
        val dur = exoPlayer.duration.takeIf { it > 0 } ?: 0L
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        if (abs(pos - lastPersistedPositionMs) >= 1000L) {
            saveHistory(pos, dur, isEnded = exoPlayer.playbackState == Player.STATE_ENDED)
        }
        exoPlayer.stop()
        stopProgressUpdates()
        updateState()
    }

    override fun release() {
        val dur = exoPlayer.duration.takeIf { it > 0 } ?: 0L
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        if (abs(pos - lastPersistedPositionMs) >= 1000L) {
            saveHistory(pos, dur, isEnded = exoPlayer.playbackState == Player.STATE_ENDED)
        }
        stopProgressUpdates()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        scope.cancel()
    }

    private fun saveHistory(positionMs: Long, durationMs: Long, isEnded: Boolean) {
        val currentItem = currentVideoItem
        val uri = currentItem?.uri
            ?: exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            ?: return
        val title = currentItem?.title?.ifBlank { currentItem.displayName }
            ?: exoPlayer.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: "Video"

        val safeDuration = if (durationMs > 0) durationMs else (currentItem?.durationMs ?: 0L)
        val percentage = if (isEnded) {
            1.0f
        } else if (safeDuration > 0) {
            (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        lastPersistedPositionMs = positionMs
        lastPersistedTimeMs = System.currentTimeMillis()
        lastPersistedUri = uri

        scope.launch(ioDispatcher) {
            try {
                val entity = PlaybackHistoryEntity(
                    videoUri = uri,
                    title = title,
                    durationMs = safeDuration,
                    lastPositionMs = positionMs,
                    lastPlayedTimestamp = System.currentTimeMillis(),
                    watchPercentage = percentage
                )
                historyDao?.upsertHistory(entity)
            } catch (_: Throwable) {
                // Safe database handling
            }
        }
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

                // Throttled history save: at most once every 5 seconds during active playback
                val now = System.currentTimeMillis()
                if (now - lastPersistedTimeMs >= 5000L && abs(pos - lastPersistedPositionMs) >= 3000L) {
                    saveHistory(pos, dur, isEnded = false)
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
