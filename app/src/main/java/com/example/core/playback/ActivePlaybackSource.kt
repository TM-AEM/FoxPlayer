package com.example.core.playback

import androidx.media3.common.Player
import com.example.core.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single playback peer the player UI both commands and observes.
 *
 * [player] receives every playback command while [playbackState] reports the
 * state of the very same playback session. Because both members always describe
 * one session, the UI can never display the state of one session while
 * commanding another.
 */
class ActivePlaybackSource(
    val player: Player?,
    val playbackState: StateFlow<PlaybackState>
)

/**
 * Resolves the active playback peer out of the two handles the player UI owns:
 * the [androidx.media3.session.MediaController] it may be connected to, and the
 * [PlayerEngine] published by [PlaybackService].
 *
 * The controller and the engine are not interchangeable. The controller is a
 * session proxy that is only meaningful while its session is alive, while the
 * engine owns the live player and is the only component that mirrors the
 * player state. Resolving both through this single entry point keeps the
 * command target and the observed state bound to the same session.
 */
object ActivePlaybackResolver {

    /**
     * The state reported while no playback session is alive.
     *
     * Shared on purpose: a fresh instance per resolution would restart the state
     * collection of every consumer each time the peer is re-resolved.
     */
    private val NO_SESSION_STATE: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())

    /**
     * Resolves the single active playback peer.
     *
     * @param controller the controller to consider, or `null` when no controller is
     *   available.
     * @param engine the engine published by [PlaybackService], or `null` when the
     *   service is not running or is being torn down.
     * @param isControllerConnected reports whether [controller] is still connected
     *   to its session. A controller is only a proxy of a session: once that
     *   session is gone it can no longer forward a command, so it must never be
     *   selected as the command target while it is disconnected.
     *
     * The predicate is injected instead of reading the connection state here so
     * that this resolver stays independent of the Media3 session types and stays
     * testable with a plain [Player] double. It defaults to trusting a supplied
     * controller, which preserves the historical contract for callers that cannot
     * observe the connection state.
     *
     * Note that a successful check is not a guarantee: the connection may be lost
     * immediately after it was read. The player UI therefore also reacts to the
     * disconnection through its controller lifecycle.
     */
    fun resolve(
        controller: Player?,
        engine: PlayerEngine?,
        isControllerConnected: () -> Boolean = { true }
    ): ActivePlaybackSource {
        val activeEngine = engine
            ?: return ActivePlaybackSource(player = null, playbackState = NO_SESSION_STATE)

        // A connected controller proxies the very player the engine hosts, so the
        // controller and the engine describe the same session. The engine stays the
        // state authority because it is the only component mirroring the live
        // player state, and the controller only becomes the command target while it
        // is connected to that session.
        //
        // A disconnected controller is not a peer any more: it is a proxy of a
        // session that no longer exists, so the engine's own player takes over the
        // commands. The observed state still comes from the engine, so the command
        // target and the observed state keep describing the same session.
        //
        // Without an engine no session is alive: the service is either not running
        // or already tearing its player down. A controller left over from such a
        // session is stale, so it is not used and no state is reported.
        val commandTarget = if (controller != null && isControllerConnected()) {
            controller
        } else {
            activeEngine.player
        }

        return ActivePlaybackSource(
            player = commandTarget,
            playbackState = activeEngine.playbackState
        )
    }
}
