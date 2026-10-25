package com.example.ui.player

import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player

/**
 * Renders the video of the currently active playback peer.
 *
 * Ownership contract:
 * - [PlayerScreen] resolves the active player and passes it in. This component
 *   is the single owner of the [SurfaceView] it creates, so no other component
 *   attaches, replaces, or clears that surface.
 * - Exactly one player owns the surface at any moment. Media3 keeps its
 *   surface state per player instance and performs no cross-player transfer:
 *   neither the media3 1.5.1 documentation nor its implementation detach the
 *   previous player when a replacement is attached. This component therefore
 *   moves ownership explicitly — it clears the surface from the current owner
 *   before attaching the replacement.
 * - When this component leaves the composition the surface is cleared from the
 *   player that owns it at that moment, never from a player that was already
 *   replaced, so an obsolete cleanup can never detach a newer attachment.
 * - The surface is never stopped or released here, and the playback session
 *   itself (commands, position, state) is not managed by this component.
 */
@Composable
internal fun PlayerSurface(
    activePlayer: Player?,
    videoAspectRatio: Float,
    resizeMode: ResizeMode,
    isInPipMode: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
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

        // Native SurfaceView composition with hardware surface pipeline.
        //
        // The callbacks act on the `activePlayer` of the composition that
        // created them, while the owner field remembers which player the
        // surface is actually attached to. `update` runs after every
        // recomposition, so a replaced player receives the same surface — but
        // only after the surface has been cleared from the player that owned
        // it, because Media3 never performs that transfer across player
        // instances by itself.
        val owner = remember { SurfaceOwner() }

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
                if (owner.player !== activePlayer) {
                    // Transfer ownership: the previous owner is cleared first
                    // so two players never hold the same surface, then the
                    // replacement receives it. Detaching first is also the
                    // only order that is safe when both handles wrap the same
                    // underlying session player.
                    owner.player?.clearVideoSurfaceView(surfaceView)
                    owner.player = activePlayer
                    activePlayer?.setVideoSurfaceView(surfaceView)
                }
            },
            onRelease = { surfaceView ->
                // Only the player that still owns the surface is cleared. A
                // player that was replaced earlier must never clear the
                // attachment of a newer one.
                owner.player?.clearVideoSurfaceView(surfaceView)
                owner.player = null
            },
            modifier = Modifier
                .size(surfaceWidth, surfaceHeight)
                .align(Alignment.Center)
                .testTag("player_surface_view")
        )
    }
}

/**
 * The player that currently owns the [SurfaceView] created by [PlayerSurface].
 *
 * Deliberately not Compose snapshot state: the field is read and written only
 * inside the view callbacks on the main thread, and making it observable would
 * invite recompositions that no UI depends on. No synchronization either — a
 * single field mutated on a single thread.
 */
private class SurfaceOwner {
    var player: Player? = null
}
