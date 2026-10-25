package com.example.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * Visibility state of the playback HUD plus its inactivity timestamp.
 *
 * This is exactly the state that used to live directly in [PlayerScreen]: the
 * HUD starts visible, an interaction restarts the auto-hide budget, and the
 * budget only runs while playback is playing. The settings panel and the PiP
 * mode are deliberately NOT owned here — they are gating inputs handed in by
 * the screen, just like the playback and gesture signals.
 *
 * One holder, one timer, no global state, no synchronization: it is created
 * per player-screen composition and touched only from the UI thread.
 */
internal class PlayerHudLayerState {
    /**
     * Whether the HUD wants to be shown, before the PiP and settings gates of
     * [PlayerHudLayer] are applied. Starts visible, as the screen did.
     */
    var isVisible by mutableStateOf(true)
        private set

    /**
     * Timestamp of the last user interaction. Changing it restarts the
     * auto-hide timer, which is why it is a key of the timer effect.
     */
    var lastInteractionTime by mutableLongStateOf(System.currentTimeMillis())

    /** Hides the HUD: auto-hide timeout, tap-to-hide, and PiP entry. */
    fun hide() {
        isVisible = false
    }

    /**
     * Registers user activity: resets the inactivity budget and, unless the
     * settings panel owns the screen, brings the HUD back.
     */
    fun notifyInteraction(settingsOpen: Boolean) {
        lastInteractionTime = System.currentTimeMillis()
        if (!isVisible && !settingsOpen) {
            isVisible = true
        }
    }
}

/** Remembers the [PlayerHudLayerState] for one player-screen composition. */
@Composable
internal fun rememberPlayerHudLayerState(): PlayerHudLayerState = remember { PlayerHudLayerState() }

/**
 * The HUD layer of the player screen: sole owner of the auto-hide timer and of
 * the visibility formula, rendering [PlayerHud] with them.
 *
 * Everything this layer cannot own arrives as an explicit parameter: the
 * settings-panel state, the PiP mode, the horizontal-swipe suppression, the
 * playback state, and the command callbacks. It decides only *whether* the HUD
 * is shown and *when* it hides itself — it never touches the player, the
 * engine, or the surface.
 */
@Composable
internal fun PlayerHudLayer(
    state: PlayerHudLayerState,
    settingsOpen: Boolean,
    inPipMode: Boolean,
    horizontalSwiping: Boolean,
    title: String,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onPipClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    // Auto-hide timer: when HUD is visible and playing, auto-hide after 3.5 seconds of inactivity.
    // Moved verbatim from PlayerScreen: same keys, same budget, single instance.
    LaunchedEffect(state.isVisible, isPlaying, state.lastInteractionTime, horizontalSwiping, settingsOpen) {
        if (state.isVisible && isPlaying && !horizontalSwiping && !settingsOpen) {
            delay(3500L)
            state.hide()
        }
    }

    PlayerHud(
        isVisible = !inPipMode && state.isVisible && !settingsOpen,
        title = title,
        isPlaying = isPlaying,
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        bufferedPositionMs = bufferedPositionMs,
        onPlayPauseClick = onPlayPauseClick,
        onSeek = onSeek,
        onBack = onBack,
        onUserInteraction = { state.notifyInteraction(settingsOpen) },
        onSettingsClick = onSettingsClick,
        onPipClick = onPipClick,
        modifier = modifier
    )
}
