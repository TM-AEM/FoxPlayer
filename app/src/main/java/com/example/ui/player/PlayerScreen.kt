package com.example.ui.player

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.example.R
import com.example.core.model.PlaybackStatus
import com.example.core.playback.ActivePlaybackResolver
import com.example.core.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fullscreen video player screen connected directly to [PlaybackService] / [MediaSession].
 *
 * Architecture & Lifecycle guarantees:
 * - Does NOT create or own an ExoPlayer instance.
 * - Connects via [MediaController] to [PlaybackService].
 * - Decouples HUD overlay from hardware [SurfaceView].
 * - Play/Pause and Seek are dispatched through the single active peer resolved by
 *   [ActivePlaybackResolver], which also provides the observed playback state, so
 *   commands and the displayed state always describe the same playback session.
 * - Never commands a [MediaController] whose session is gone: a disconnected
 *   controller is a proxy of a session that no longer exists, so the engine of the
 *   live session takes the commands over.
 * - Rebuilds the [MediaController] when the service is recreated, so the screen
 *   reaches the new session without the user having to leave and reopen it.
 * - Implements lifecycle-safe Auto-hide mechanism for HUD controls with single-tap toggle.
 * - Dynamically maintains video aspect ratio without distortion (16:9, 4:3, ultrawide, portrait).
 * - Leaves system bars in immersive sticky mode during playback and cleanly restores them on exit.
 * - Detaches the [SurfaceView] on exit WITHOUT stopping or releasing the background player.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    gestureSettingsRepository: com.example.data.repository.GestureSettingsRepository? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val effectiveGestureSettingsRepo = remember(context, gestureSettingsRepository) {
        gestureSettingsRepository ?: com.example.data.repository.GestureSettingsRepositoryImpl.getInstance(context)
    }

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

    // The engine of the running service is the state authority. It is observed
    // reactively so the screen follows the service lifecycle instead of pinning
    // whichever engine happened to exist at the first composition.
    val engine by PlaybackService.currentEngineFlow.collectAsState()

    // MediaController connection to PlaybackService.
    //
    // The controller is only a proxy of a session: it can forward commands while
    // that session is alive and turns useless the moment the session is gone.
    // The lifecycle of the controller keeps that promise: every build attempt
    // owns a unique token, so a callback of an obsolete attempt can never clear
    // or overwrite a newer controller.
    val controllerLifecycle = remember { ControllerLifecycle<MediaController>() }
    val controller = controllerLifecycle.controller

    // Bumped to request a fresh controller after the previous one was lost.
    val controllerRequest = controllerLifecycle.requestCount

    DisposableEffect(controllerRequest, engine) {
        val buildToken = controllerLifecycle.startAttempt()

        // Only a live session can be controlled. The service publishes the token of
        // its session before it publishes the engine, so the token that belongs to
        // the observed engine is always reachable here. Without a session there is
        // nothing to connect to: a controller for a service that is not alive would
        // only be a proxy of a session that does not exist, and it could never
        // forward a command.
        val sessionToken = PlaybackService.currentSessionToken

        var futureToRelease: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        if (sessionToken != null) {
            try {
                val controllerFuture = MediaController.Builder(context, sessionToken)
                    .setListener(object : MediaController.Listener {
                        override fun onDisconnected(disconnected: MediaController) {
                            // The session of this controller is gone. Drop it so the UI falls
                            // back to the engine of the live session instead of commanding a
                            // controller whose session no longer exists. This callback is the
                            // authoritative signal of that event; the connection check of the
                            // active peer resolution is the safety net for a controller that
                            // is already disconnected when the peer is resolved.
                            ContextCompat.getMainExecutor(context).execute {
                                // A callback of an obsolete attempt must never touch the
                                // controller of a newer attempt.
                                if (!controllerLifecycle.isCurrentAttempt(buildToken)) return@execute
                                // The controller of the dead session is dropped and a
                                // fresh one is requested: the service may have been
                                // recreated with a new session.
                                controllerLifecycle.onDisconnected(disconnected)
                            }
                        }
                    })
                    .buildAsync()
                futureToRelease = controllerFuture
                controllerFuture.addListener(
                    {
                        try {
                            if (!controllerFuture.isCancelled) {
                                val built = controllerFuture.get()
                                // The completion of an obsolete attempt must never replace
                                // the controller of a newer attempt.
                                controllerLifecycle.onBuilt(buildToken, built)
                            }
                        } catch (_: Throwable) {
                            // The session is not reachable. The engine of the live service
                            // stays the command target.
                        }
                    },
                    ContextCompat.getMainExecutor(context)
                )
            } catch (_: Throwable) {
                // Handled gracefully in environments without full ServiceManager
            }
        }

        onDispose {
            // The attempt is obsolete: a late callback of it must never clear or
            // overwrite the controller of a newer attempt. The controller itself is
            // released here, which makes it report itself as disconnected, so it can
            // no longer be mistaken for a usable peer.
            controllerLifecycle.invalidateAttempt(buildToken)
            futureToRelease?.let { MediaController.releaseFuture(it) }
        }
    }

    // A single resolution of the active peer: playback commands and the observed
    // playback state always describe the same playback session. Re-resolving on
    // every change of the controller or the engine restarts the state collection
    // for the new source and stops the collection of the obsolete one.
    //
    // A controller that is disconnected at the moment of the resolution is never
    // the command target: the engine of the live session takes the commands over
    // while the observed state keeps coming from that very engine. The connection
    // is read again on every resolution, and the disconnection callback drops the
    // controller as soon as its session is reported gone, so the commands reach
    // the engine of the live session from that moment on.
    val activeSource = remember(controller, engine) {
        ActivePlaybackResolver.resolve(controller, engine) {
            controller?.isConnected() == true
        }
    }

    // The peer that receives every playback command.
    val activePlayer: Player? = activeSource.player

    // The state of that very peer.
    val playbackState by activeSource.playbackState.collectAsState()

    // Title from MediaMetadata or PlaybackService engine
    val defaultVideoTitle = stringResource(R.string.player_default_video_title)
    val videoTitle = remember(activePlayer?.currentMediaItem, defaultVideoTitle) {
        activePlayer?.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: activePlayer?.mediaMetadata?.title?.toString()
            ?: defaultVideoTitle
    }

    // Gesture Controller for Volume and Brightness
    val gestureController = remember(context, activity) {
        PlayerGestureController(
            context = context,
            activity = activity
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

    // Settings Dialog State. HUD visibility and its auto-hide timer moved to
    // PlayerHudLayer, the single owner of that state; the settings panel stays
    // here because the back handler and the panel itself also need it.
    val hudState = rememberPlayerHudLayerState()
    var isSettingsOpen by remember { mutableStateOf(false) }

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
                    hudState.hide()
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

    // Horizontal Swipe Seek drag tracking state
    var isHorizontalSwiping by remember { mutableStateOf(false) }
    var swipeStartSeekPositionMs by remember { mutableLongStateOf(0L) }
    var swipeTargetSeekPositionMs by remember { mutableLongStateOf(0L) }

    // Checkpoint 9: Video Aspect Ratio / Resize Mode (Fit, Fill, Zoom)
    var resizeMode by remember { mutableStateOf(ResizeMode.FIT) }

    // User-configurable Gesture Sensitivity Threshold settings state (persisted across app restarts)
    val persistedGestureSettings by effectiveGestureSettingsRepo.gestureSettingsFlow
        .collectAsState(initial = GestureSettingsState())
    var localVolumeSensitivity by remember { mutableStateOf<GestureSensitivity?>(null) }
    var localBrightnessSensitivity by remember { mutableStateOf<GestureSensitivity?>(null) }
    var localDoubleTapSeekDuration by remember { mutableStateOf<DoubleTapSeekDuration?>(null) }
    val gestureSettings = GestureSettingsState(
        volumeSensitivity = localVolumeSensitivity ?: persistedGestureSettings.volumeSensitivity,
        brightnessSensitivity = localBrightnessSensitivity ?: persistedGestureSettings.brightnessSensitivity,
        doubleTapSeekDuration = localDoubleTapSeekDuration ?: persistedGestureSettings.doubleTapSeekDuration
    )

    // Checkpoint 9: Real-time Tracks and Selection parameters state
    var currentTracks by remember { mutableStateOf(activePlayer?.currentTracks ?: Tracks.EMPTY) }
    var currentTrackParameters by remember {
        mutableStateOf(activePlayer?.trackSelectionParameters ?: TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT)
    }
    var currentSpeed by remember {
        mutableFloatStateOf(activePlayer?.playbackParameters?.speed ?: playbackState.playbackSpeed)
    }
    // Long-Press 2x Speed Boost state
    var speedBeforeBoost by remember {
        mutableFloatStateOf(activePlayer?.playbackParameters?.speed ?: 1.0f)
    }
    var isSpeedBoosted by remember { mutableStateOf(false) }

    fun startSpeedBoost() {
        if (!isSpeedBoosted) {
            val targetPlayer = activePlayer
            val original = targetPlayer?.playbackParameters?.speed ?: currentSpeed
            speedBeforeBoost = original
            isSpeedBoosted = true
            targetPlayer?.setPlaybackSpeed(2.0f)
            gestureFeedbackDismissJob?.cancel()
            gestureFeedback = GestureFeedback.SpeedBoost(2.0f)
        }
    }

    fun endSpeedBoost() {
        if (isSpeedBoosted) {
            val targetPlayer = activePlayer
            targetPlayer?.setPlaybackSpeed(speedBeforeBoost)
            isSpeedBoosted = false
            if (gestureFeedback is GestureFeedback.SpeedBoost) {
                gestureFeedback = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            endSpeedBoost()
        }
    }

    // The auto-hide timer itself lives in PlayerHudLayer, next to the HUD it
    // hides. This forwards interactions to that single owner, so there is
    // exactly one timer and one visibility state for the HUD.
    fun notifyUserInteraction() {
        hudState.notifyInteraction(isSettingsOpen)
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
                if (!isSpeedBoosted) {
                    currentSpeed = playbackParameters.speed
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Memoized Audio & Subtitle track lists from actual Player tracks
    val audioTracks = remember(currentTracks, currentTrackParameters, context) {
        PlayerTrackHelper.getAudioTracks(context, currentTracks, currentTrackParameters)
    }
    val isAudioAuto = remember(currentTrackParameters) {
        PlayerTrackHelper.isAudioAuto(currentTrackParameters)
    }

    val subtitleTracks = remember(currentTracks, currentTrackParameters, context) {
        PlayerTrackHelper.getSubtitleTracks(context, currentTracks, currentTrackParameters)
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

    // Configure Auto-Enter PiP for Android 12+ gesture navigation while video is playing
    LaunchedEffect(playbackState.isPlaying, videoAspectRatio) {
        PipHelper.updateAutoEnterPip(activity, videoAspectRatio, playbackState.isPlaying)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
            .testTag("player_screen_root"),
        contentAlignment = Alignment.Center
    ) {
        // Native SurfaceView composition with hardware surface pipeline. The
        // component owns the surface lifecycle: it attaches the surface to the
        // active player, re-attaches it when the active player is replaced, and
        // clears it from the player that is active when the screen exits.
        PlayerSurface(
            activePlayer = activePlayer,
            videoAspectRatio = videoAspectRatio,
            resizeMode = resizeMode,
            isInPipMode = isInPipMode
        )

        // Checkpoint 8: Interactive Player Gesture Detector Layer (disabled in PiP mode)
        if (!isInPipMode) {
            PlayerGestureDetector(
                onSingleTap = {
                    if (hudState.isVisible) {
                        hudState.hide()
                    } else {
                        notifyUserInteraction()
                    }
                },
                onCenterTap = {
                    val targetPlayer = activePlayer
                    if (targetPlayer != null) {
                        // If video has completed/ended, seek back to 0 and replay
                        if (targetPlayer.playbackState == Player.STATE_ENDED) {
                            targetPlayer.seekTo(0L)
                            targetPlayer.play()
                        } else if (targetPlayer.isPlaying) {
                            targetPlayer.pause()
                        } else {
                            targetPlayer.play()
                        }
                    }
                    notifyUserInteraction()
                },
                onDoubleTapSeek = { isForward ->
                    val targetPlayer = activePlayer
                    val current = playbackState.currentPositionMs
                    val duration = playbackState.durationMs
                    val seekDuration = gestureSettings.doubleTapSeekDuration
                    val deltaSeconds = seekDuration.seconds
                    val deltaMs = seekDuration.durationMillis
                    val targetMs = if (isForward) {
                        if (duration > 0L) (current + deltaMs).coerceIn(0L, duration) else (current + deltaMs).coerceAtLeast(0L)
                    } else {
                        (current - deltaMs).coerceAtLeast(0L)
                    }
                    targetPlayer?.seekTo(targetMs)
                    showTemporaryFeedback(
                        GestureFeedback.DoubleTapSeek(deltaSeconds = deltaSeconds, isForward = isForward),
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
                        val targetPlayer = activePlayer
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
                    gestureController.resetPendingVolumeAdjustment()
                    notifyUserInteraction()
                },
                onLongPressStart = {
                    startSpeedBoost()
                },
                onLongPressEnd = {
                    endSpeedBoost()
                },
                gestureSettings = gestureSettings
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

        // HUD Controls Layer (Top Bar, Play/Pause, Timeline) - hidden in PiP mode.
        // Rendered by PlayerHudLayer, which owns the HUD visibility state and
        // the single auto-hide timer.
        PlayerHudLayer(
            state = hudState,
            settingsOpen = isSettingsOpen,
            inPipMode = isInPipMode,
            horizontalSwiping = isHorizontalSwiping,
            title = videoTitle,
            isPlaying = playbackState.isPlaying,
            currentPositionMs = playbackState.currentPositionMs,
            durationMs = playbackState.durationMs,
            bufferedPositionMs = playbackState.bufferedPositionMs,
            onPlayPauseClick = {
                val targetPlayer = activePlayer
                if (targetPlayer != null) {
                    if (targetPlayer.isPlaying) {
                        targetPlayer.pause()
                    } else {
                        targetPlayer.play()
                    }
                }
            },
            onSeek = { targetPositionMs ->
                val targetPlayer = activePlayer
                val safeDur = playbackState.durationMs
                val clampedPosition = if (safeDur > 0L) {
                    targetPositionMs.coerceIn(0L, safeDur)
                } else {
                    targetPositionMs.coerceAtLeast(0L)
                }
                targetPlayer?.seekTo(clampedPosition)
            },
            onBack = onBack,
            onSettingsClick = {
                isSettingsOpen = true
                notifyUserInteraction()
            },
            onPipClick = if (PipHelper.isPipSupported(context)) {
                {
                    isSettingsOpen = false
                    hudState.hide()
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
                    val targetPlayer = activePlayer
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
                    val targetPlayer = activePlayer
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
                    val targetPlayer = activePlayer
                    if (targetPlayer != null) {
                        PlayerTrackHelper.selectSubtitleTrack(targetPlayer, mode)
                        currentTrackParameters = targetPlayer.trackSelectionParameters
                    }
                    notifyUserInteraction()
                },
                gestureSettings = gestureSettings,
                onVolumeSensitivityChange = { newVolumeSensitivity ->
                    localVolumeSensitivity = newVolumeSensitivity
                    coroutineScope.launch {
                        effectiveGestureSettingsRepo.updateVolumeSensitivity(newVolumeSensitivity)
                    }
                    notifyUserInteraction()
                },
                onBrightnessSensitivityChange = { newBrightnessSensitivity ->
                    localBrightnessSensitivity = newBrightnessSensitivity
                    coroutineScope.launch {
                        effectiveGestureSettingsRepo.updateBrightnessSensitivity(newBrightnessSensitivity)
                    }
                    notifyUserInteraction()
                },
                onDoubleTapSeekDurationChange = { newDuration ->
                    localDoubleTapSeekDuration = newDuration
                    coroutineScope.launch {
                        effectiveGestureSettingsRepo.updateDoubleTapSeekDuration(newDuration)
                    }
                    notifyUserInteraction()
                },
                onGestureSettingsChange = { updatedSettings ->
                    localVolumeSensitivity = updatedSettings.volumeSensitivity
                    localBrightnessSensitivity = updatedSettings.brightnessSensitivity
                    localDoubleTapSeekDuration = updatedSettings.doubleTapSeekDuration
                    coroutineScope.launch {
                        effectiveGestureSettingsRepo.updateSettings(updatedSettings)
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

