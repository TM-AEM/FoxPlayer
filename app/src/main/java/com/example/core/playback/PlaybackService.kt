package com.example.core.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.example.MainActivity
import com.example.core.model.VideoItem

/**
 * Foreground [MediaSessionService] managing the lifecycle of the [MediaSession] and [ExoPlayer].
 * Handles background playback, system media notifications, lockscreen controls, and safe resource disposal.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var playerEngine: FoxPlayerEngine? = null

    companion object {
        const val ACTION_PLAY_VIDEO = "com.example.action.PLAY_VIDEO"
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_VIDEO_ID = "extra_video_id"

        @Volatile
        var currentEngine: PlayerEngine? = null
            internal set

        @Volatile
        var currentSessionToken: SessionToken? = null
            internal set

        fun startPlayback(context: Context, videoItem: VideoItem) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_PLAY_VIDEO
                putExtra(EXTRA_VIDEO_URI, videoItem.uri)
                putExtra(EXTRA_VIDEO_TITLE, videoItem.title.ifBlank { videoItem.displayName })
                putExtra(EXTRA_VIDEO_ID, videoItem.id)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                try {
                    context.startService(intent)
                } catch (_: Exception) {}
            }
            currentEngine?.prepare(videoItem, playWhenReady = true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PLAY_VIDEO) {
            val uriString = intent.getStringExtra(EXTRA_VIDEO_URI)
            val title = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""
            val videoId = intent.getLongExtra(EXTRA_VIDEO_ID, -1L)
            if (!uriString.isNullOrBlank()) {
                val videoItem = VideoItem(
                    id = videoId,
                    uri = uriString,
                    title = title,
                    displayName = title,
                    durationMs = 0L,
                    sizeBytes = 0L
                )
                playerEngine?.prepare(videoItem, playWhenReady = true)
            }
        }
        return result
    }

    override fun onCreate() {
        super.onCreate()
        initializeSessionAndPlayer()
    }

    private fun initializeSessionAndPlayer() {
        if (mediaSession != null) return

        val engine = FoxPlayerEngine(applicationContext)
        this.playerEngine = engine
        currentEngine = engine

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().build()
                val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_PREPARE)
                    .add(Player.COMMAND_STOP)
                    .add(Player.COMMAND_SEEK_BACK)
                    .add(Player.COMMAND_SEEK_FORWARD)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_SET_SPEED_AND_PITCH)
                    .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_GET_TIMELINE)
                    .add(Player.COMMAND_SET_VOLUME)
                    .add(Player.COMMAND_GET_VOLUME)
                    .add(Player.COMMAND_GET_TRACKS)
                    .add(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)
                    .build()

                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .setAvailablePlayerCommands(playerCommands)
                    .build()
            }
        }

        val session = MediaSession.Builder(this, engine.exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(callback)
            .setId("FoxPlayerMediaSession")
            .build()
        this.mediaSession = session
        currentSessionToken = session.token
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0 || currentPlayer.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        currentSessionToken = null
        currentEngine = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        playerEngine?.release()
        playerEngine = null
        super.onDestroy()
    }
}
