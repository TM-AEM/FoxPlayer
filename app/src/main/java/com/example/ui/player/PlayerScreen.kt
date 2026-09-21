package com.example.ui.player

import android.app.Activity
import android.content.ComponentName
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.core.model.PlaybackState
import com.example.core.model.PlaybackStatus
import com.example.core.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Fullscreen video player screen connected directly to [PlaybackService] / [MediaSession].
 *
 * Architecture & Lifecycle guarantees:
 * - Does NOT create or own an ExoPlayer instance.
 * - Connects via [MediaController] to [PlaybackService].
 * - Decouples HUD overlay from hardware [SurfaceView].
 * - Play/Pause and Seek are dispatched directly through [MediaController] (or active player).
 * - Implements lifecycle-safe Auto-hide mechanism for HUD controls with single-tap toggle.
 * - Dynamically maintains video aspect ratio without distortion (16:9, 4:3, ultrawide, portrait).
 * - Leaves system bars in immersive sticky mode during playback and cleanly restores them on exit.
 * - Detaches the [SurfaceView] on exit WITHOUT stopping or releasing the background player.
 */
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Configure immersive fullscreen system bars (Edge-to-Edge and hide navigation/status bars)
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            val originalBehavior = insetsController.systemBarsBehavior
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                insetsController.systemBarsBehavior = originalBehavior
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            onDispose {}
        }
    }

    // MediaController connection to PlaybackService
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val sessionToken = PlaybackService.currentSessionToken
            ?: SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java)
            )

        var futureToRelease: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        try {
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            futureToRelease = controllerFuture
            controllerFuture.addListener(
                {
                    try {
                        if (!controllerFuture.isCancelled) {
                            controller = controllerFuture.get()
                        }
                    } catch (_: Throwable) {
                        // Fallback to PlaybackService.currentEngine if controller future encounters issue
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        } catch (_: Throwable) {
            // Handled gracefully in environments without full ServiceManager
        }

        onDispose {
            controller = null
            futureToRelease?.let { MediaController.releaseFuture(it) }
        }
    }

    // Use connected MediaController or service engine player
    val activePlayer: Player? = controller ?: PlaybackService.currentEngine?.player

    // Collect PlaybackState from the active engine
    val playbackStateFlow = remember(PlaybackService.currentEngine) {
        PlaybackService.currentEngine?.playbackState ?: MutableStateFlow(PlaybackState())
    }
    val playbackState by playbackStateFlow.collectAsState()

    // Title from MediaMetadata or PlaybackService engine
    val videoTitle = remember(activePlayer?.currentMediaItem) {
        activePlayer?.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: activePlayer?.mediaMetadata?.title?.toString()
            ?: "Video"
    }

    // Gesture Controller for Volume and Brightness
    val gestureController = remember(context, activity) {
        PlayerGestureController(
            context = context,
            activity = activity,
            playerProvider = { controller ?: activePlayer }
        )
    }

    // Transient Visual Gesture Feedback State
    var gestureFeedback by remember { mutableStateOf<GestureFeedback?>(null) }
    var gestureFeedbackDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun showTemporaryFeedback(feedback: GestureFeedback, durationMs: Long = 1200L) {
        gestureFeedbackDismissJob?.cancel()
        gestureFeedback = feedback
        gestureFeedbackDismissJob = coroutineScope.launch {
            delay(durationMs)
            gestureFeedback = null
        }
    }

    // HUD Auto-Hide and Settings Dialog State
    var isHudVisible by remember { mutableStateOf(true) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Picture-in-Picture (PiP) State Tracking
    var isInPipMode by remember {
        mutableStateOf(activity?.isInPictureInPictureMode ?: false)
    }

    DisposableEffect(activity) {
        val act = activity
        if (act is androidx.activity.ComponentActivity) {
            val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
                isInPipMode = info.isInPictureInPictureMode
                if (info.isInPictureInPictureMode) {
                    isSettingsOpen = false
                    isHudVisible = false
                }
            }
            act.addOnPictureInPictureModeChangedListener(listener)
            onDispose {
                act.removeOnPictureInPictureModeChangedListener(listener)
                PipHelper.disableAutoEnterPip(act)
            }
        } else {
            onDispose {
                PipHelper.disableAutoEnterPip(act)
            }
        }
    }

    // Configure Auto-Enter PiP for Android 12+ gesture navigation while video is playing
    LaunchedEffect(playbackState.isPlaying, videoAspectRatio) {
        PipHelper.updateAutoEnterPip(activity, videoAspectRatio, playbackState.isPlaying)
    }

    // Horizontal Swipe Seek drag tracking state
    var isHorizontalSwiping by remember { mutableStateOf(false) }
    var swipeStartSeekPositionMs by remember { mutableLongStateOf(0L) }
    var swipeTargetSeekPositionMs by remember { mutableLongStateOf(0L) }

    // Checkpoint 9: Video Aspect Ratio / Resize Mode (Fit, Fill, Zoom)
    var resizeMode by remember { mutableStateOf(ResizeMode.FIT) }

    // Checkpoint 9: Real-time Tracks and Selection parameters state
    var currentTracks by remember { mutableStateOf(activePlayer?.currentTracks ?: Tracks.EMPTY) }
    var currentTrackParameters by remember {
        mutableStateOf(activePlayer?.trackSelectionParameters ?: TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT)
    }
    var currentSpeed by remember {
        mutableFloatStateOf(activePlayer?.playbackParameters?.speed ?: playbackState.playbackSpeed)
    }

    // Auto-hide timer: when HUD is visible and playing, auto-hide after 3.5 seconds of inactivity
    LaunchedEffect(isHudVisible, playbackState.isPlaying, lastInteractionTime, isHorizontalSwiping, isSettingsOpen) {
        if (isHudVisible && playbackState.isPlaying && !isHorizontalSwiping && !isSettingsOpen) {
            delay(3500L)
            isHudVisible = false
        }
    }

    fun notifyUserInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        if (!isHudVisible && !isSettingsOpen) {
            isHudVisible = true
        }
    }

    // Handle system back gesture/button: dismiss settings panel if open, otherwise exit player screen
    BackHandler {
        if (isSettingsOpen) {
            isSettingsOpen = false
            notifyUserInteraction()
        } else {
            onBack()
        }
    }

    // Listen to video dimensions, tracks, parameters, and speed dynamically from active Player
    var videoSize by remember { mutableStateOf(activePlayer?.videoSize ?: VideoSize.UNKNOWN) }

    DisposableEffect(activePlayer) {
        val player = activePlayer ?: return@DisposableEffect onDispose {}
        videoSize = player.videoSize
        currentTracks = player.currentTracks
        currentTrackParameters = player.trackSelectionParameters
        currentSpeed = player.playbackParameters.speed

        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(newVideoSize: VideoSize) {
                videoSize = newVideoSize
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }

            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                currentTrackParameters = parameters
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                currentSpeed = playbackParameters.speed
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Memoized Audio & Subtitle track lists from actual Player tracks
    val audioTracks = remember(currentTracks, currentTrackParameters) {
        PlayerTrackHelper.getAudioTracks(currentTracks, currentTrackParameters)
    }
    val isAudioAuto = remember(currentTrackParameters) {
        PlayerTrackHelper.isAudioAuto(currentTrackParameters)
    }

    val subtitleTracks = remember(currentTracks, currentTrackParameters) {
        PlayerTrackHelper.getSubtitleTracks(currentTracks, currentTrackParameters)
    }
    val isSubtitleOff = remember(currentTrackParameters) {
        PlayerTrackHelper.isSubtitleOff(currentTrackParameters)
    }
    val isSubtitleAuto = remember(currentTrackParameters) {
        PlayerTrackHelper.isSubtitleAuto(currentTrackParameters)
    }

    // Compute pixel-accurate aspect ratio (handling anamorphic ratios if present)
    val videoAspectRatio = remember(videoSize) {
        if (videoSize.width > 0 && videoSize.height > 0) {
            val pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) {
                videoSize.pixelWidthHeightRatio
            } else {
                1.0f
            }
            (videoSize.width.toFloat() * pixelRatio) / videoSize.height.toFloat()
        } else {
            16f / 9f
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
            .testTag("player_screen_root"),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        val containerAspect = if (containerHeight.value > 0) {
            containerWidth.value / containerHeight.value
        } else {
            16f / 9f
        }

        // Calculate surface dimensions based on selected ResizeMode:
        // - FIT: letterbox/pillarbox preserving aspect ratio
        // - FILL: stretches completely to container dimensions
        // - ZOOM: crops/zooms to fill entire container preserving aspect ratio without black bars
        val (surfaceWidth, surfaceHeight) = remember(
            containerAspect,
            videoAspectRatio,
            containerWidth,
            containerHeight,
            resizeMode,
            isInPipMode
        ) {
            if (isInPipMode) {
                Pair(containerWidth, containerHeight)
            } else {
                when (resizeMode) {
                    ResizeMode.FIT -> {
                        if (containerAspect > videoAspectRatio) {
                            // Wider container (e.g. landscape): pillarbox on sides
                            Pair(containerHeight * videoAspectRatio, containerHeight)
                        } else {
                            // Taller container (e.g. portrait): letterbox on top/bottom
                            Pair(containerWidth, containerWidth / videoAspectRatio)
                        }
                    }
                    ResizeMode.FILL -> {
                        Pair(containerWidth, containerHeight)
                    }
                    ResizeMode.ZOOM -> {
                        if (containerAspect > videoAspectRatio) {
                            // Container wider: scale by width so height overflows container
                            Pair(containerWidth, containerWidth / videoAspectRatio)
                        } else {
                            // Container taller: scale by height so width overflows container
                            Pair(containerHeight * videoAspectRatio, containerHeight)
                        }
                    }
                }
            }
        }

        // Native SurfaceView composition with hardware surface pipeline
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { surfaceView ->
                activePlayer?.setVideoSurfaceView(surfaceView)
            },
            onRelease = { surfaceView ->
                activePlayer?.clearVideoSurfaceView(surfaceView)
            },
            modifier = Modifier
                .size(surfaceWidth, surfaceHeight)
                .align(Alignment.Center)
                .testTag("player_surface_view")
        )

        // Checkpoint 8: Interactive Player Gesture Detector Layer (disabled in PiP mode)
        if (!isInPipMode) {
            PlayerGestureDetector(
                onSingleTap = {
                    if (isHudVisible) {
                        isHudVisible = false
                    } else {
                        notifyUserInteraction()
                    }
                },
                onDoubleTapSeek = { isForward ->
                    val targetPlayer = controller ?: activePlayer
                    val current = playbackState.currentPositionMs
                    val duration = playbackState.durationMs
                    val deltaMs = 10_000L
                    val targetMs = if (isForward) {
                        if (duration > 0L) (current + deltaMs).coerceIn(0L, duration) else (current + deltaMs).coerceAtLeast(0L)
                    } else {
                        (current - deltaMs).coerceAtLeast(0L)
                    }
                    targetPlayer?.seekTo(targetMs)
                    showTemporaryFeedback(
                        GestureFeedback.DoubleTapSeek(deltaSeconds = 10, isForward = isForward),
                        durationMs = 900L
                    )
                    notifyUserInteraction()
                },
                onHorizontalSwipeSeekStart = {
                    isHorizontalSwiping = true
                    swipeStartSeekPositionMs = playbackState.currentPositionMs
                    swipeTargetSeekPositionMs = playbackState.currentPositionMs
                    notifyUserInteraction()
                },
                onHorizontalSwipeSeekChange = { deltaPx, totalWidthPx ->
                    if (totalWidthPx > 0) {
                        val duration = playbackState.durationMs
                        // Map full screen width drag to up to 90 seconds or full video duration if shorter
                        val maxSeekSpanMs = if (duration in 1L..90_000L) duration else 90_000L
                        val fraction = deltaPx / totalWidthPx.toFloat()
                        val deltaMs = (fraction * maxSeekSpanMs).toLong()

                        val targetMs = if (duration > 0L) {
                            (swipeStartSeekPositionMs + deltaMs).coerceIn(0L, duration)
                        } else {
                            (swipeStartSeekPositionMs + deltaMs).coerceAtLeast(0L)
                        }
                        swipeTargetSeekPositionMs = targetMs

                        showTemporaryFeedback(
                            GestureFeedback.SwipeSeek(
                                targetPositionMs = targetMs,
                                deltaMs = targetMs - swipeStartSeekPositionMs,
                                durationMs = duration
                            ),
                            durationMs = 1500L
                        )
                    }
                },
                onHorizontalSwipeSeekEnd = {
                    if (isHorizontalSwiping) {
                        val targetPlayer = controller ?: activePlayer
                        targetPlayer?.seekTo(swipeTargetSeekPositionMs)
                        isHorizontalSwiping = false
                        notifyUserInteraction()
                    }
                },
                onVerticalSwipeBrightnessChange = { deltaFraction ->
                    val newPercent = gestureController.adjustBrightness(deltaFraction)
                    showTemporaryFeedback(
                        GestureFeedback.Brightness(newPercent),
                        durationMs = 1200L
                    )
                    notifyUserInteraction()
                },
                onVerticalSwipeVolumeChange = { deltaFraction ->
                    val newPercent = gestureController.adjustVolume(deltaFraction)
                    showTemporaryFeedback(
                        GestureFeedback.Volume(newPercent),
                        durationMs = 1200L
                    )
                    notifyUserInteraction()
                },
                onVerticalSwipeEnd = {
                    notifyUserInteraction()
                }
            )
        }

        // Buffering indicator
        if (playbackState.status == PlaybackStatus.BUFFERING) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("player_buffering_indicator"),
                color = Color.White
            )
        }

        // Error message if playback failed
        if (playbackState.status == PlaybackStatus.ERROR && !playbackState.errorMessage.isNullOrBlank()) {
            Text(
                text = playbackState.errorMessage ?: "",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
                    .testTag("player_error_text")
            )
        }

        // Checkpoint 8: Transient Visual Gesture Feedback Overlay (hidden in PiP mode)
        if (!isInPipMode) {
            GestureFeedbackOverlay(
                feedback = gestureFeedback,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // HUD Controls Layer (Top Bar, Play/Pause, Timeline) - hidden in PiP mode
        PlayerHud(
            isVisible = !isInPipMode && isHudVisible && !isSettingsOpen,
            title = videoTitle,
            isPlaying = playbackState.isPlaying,
            currentPositionMs = playbackState.currentPositionMs,
            durationMs = playbackState.durationMs,
            bufferedPositionMs = playbackState.bufferedPositionMs,
            onPlayPauseClick = {
                val targetPlayer = controller ?: activePlayer
                if (targetPlayer != null) {
                    if (targetPlayer.isPlaying) {
                        targetPlayer.pause()
                    } else {
                        targetPlayer.play()
                    }
                }
            },
            onSeek = { targetPositionMs ->
                val targetPlayer = controller ?: activePlayer
                val safeDur = playbackState.durationMs
                val clampedPosition = if (safeDur > 0L) {
                    targetPositionMs.coerceIn(0L, safeDur)
                } else {
                    targetPositionMs.coerceAtLeast(0L)
                }
                targetPlayer?.seekTo(clampedPosition)
            },
            onBack = onBack,
            onUserInteraction = {
                notifyUserInteraction()
            },
            onSettingsClick = {
                isSettingsOpen = true
                notifyUserInteraction()
            },
            onPipClick = if (PipHelper.isPipSupported(context)) {
                {
                    isSettingsOpen = false
                    isHudVisible = false
                    PipHelper.enterPip(activity, videoAspectRatio)
                }
            } else null
        )

        // Checkpoint 9: Player Settings Panel (Speed, Audio, Subtitles, Aspect Ratio)
        if (!isInPipMode) {
            PlayerSettingsPanel(
                isOpen = isSettingsOpen,
                currentSpeed = currentSpeed,
                onSpeedSelected = { speed ->
                    val targetPlayer = controller ?: activePlayer
                    targetPlayer?.setPlaybackSpeed(speed)
                    currentSpeed = speed
                    notifyUserInteraction()
                },
                currentResizeMode = resizeMode,
                onResizeModeSelected = { mode ->
                    resizeMode = mode
                    notifyUserInteraction()
                },
                audioTracks = audioTracks,
                isAudioAuto = isAudioAuto,
                onAudioTrackSelected = { track ->
                    val targetPlayer = controller ?: activePlayer
                    if (targetPlayer != null) {
                        PlayerTrackHelper.selectAudioTrack(targetPlayer, track)
                        currentTrackParameters = targetPlayer.trackSelectionParameters
                    }
                    notifyUserInteraction()
                },
                subtitleTracks = subtitleTracks,
                isSubtitleOff = isSubtitleOff,
                isSubtitleAuto = isSubtitleAuto,
                onSubtitleModeSelected = { mode ->
                    val targetPlayer = controller ?: activePlayer
                    if (targetPlayer != null) {
                        PlayerTrackHelper.selectSubtitleTrack(targetPlayer, mode)
                        currentTrackParameters = targetPlayer.trackSelectionParameters
                    }
                    notifyUserInteraction()
                },
                onDismiss = {
                    isSettingsOpen = false
                    notifyUserInteraction()
                }
            )
        }
    }
}

