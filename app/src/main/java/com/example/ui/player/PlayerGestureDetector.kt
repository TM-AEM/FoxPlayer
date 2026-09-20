package com.example.ui.player

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

/**
 * Directional swipe mode detected during drag gesture.
 */
enum class DragMode {
    NONE,
    HORIZONTAL_SEEK,
    VERTICAL_LEFT_BRIGHTNESS,
    VERTICAL_RIGHT_VOLUME
}

/**
 * Gesture detection layer for video player handling:
 * 1. Single-tap -> Toggle HUD visibility
 * 2. Double-tap left half -> Rewind 10s
 * 3. Double-tap right half -> Fast-forward 10s
 * 4. Horizontal swipe -> Relative seek preview & seek on release
 * 5. Vertical swipe on left half -> Screen brightness adjustment
 * 6. Vertical swipe on right half -> Media volume adjustment
 *
 * This layer sits underneath HUD interactive controls (buttons/sliders)
 * so clicks on HUD elements are not hijacked.
 */
@Composable
fun PlayerGestureDetector(
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (isForward: Boolean) -> Unit,
    onHorizontalSwipeSeekStart: () -> Unit,
    onHorizontalSwipeSeekChange: (deltaPx: Float, totalWidthPx: Int) -> Unit,
    onHorizontalSwipeSeekEnd: () -> Unit,
    onVerticalSwipeBrightnessChange: (deltaFraction: Float) -> Unit,
    onVerticalSwipeVolumeChange: (deltaFraction: Float) -> Unit,
    onVerticalSwipeEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var componentSize by remember { mutableStateOf(IntSize.Zero) }

    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val touchSlop = 18f

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { componentSize = it }
            .testTag("player_gesture_detector")
            // Tap gestures: Single tap toggle HUD, Double tap ±10s seek
            .pointerInput(componentSize) {
                detectTapGestures(
                    onTap = {
                        onSingleTap()
                    },
                    onDoubleTap = { offset ->
                        val halfWidth = componentSize.width / 2
                        if (halfWidth > 0) {
                            val isForward = offset.x >= halfWidth
                            onDoubleTapSeek(isForward)
                        }
                    }
                )
            }
            // Swipe / Drag gestures: Horizontal Seek, Vertical Volume (Right), Vertical Brightness (Left)
            .pointerInput(componentSize) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragMode = DragMode.NONE
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        val width = componentSize.width
                        val height = componentSize.height
                        if (width <= 0 || height <= 0) return@detectDragGestures

                        // Classify drag mode once touch slop threshold is surpassed
                        if (dragMode == DragMode.NONE) {
                            val absX = abs(totalDragX)
                            val absY = abs(totalDragY)

                            if (absX > touchSlop || absY > touchSlop) {
                                if (absX > absY) {
                                    dragMode = DragMode.HORIZONTAL_SEEK
                                    onHorizontalSwipeSeekStart()
                                } else {
                                    val startX = change.position.x - totalDragX
                                    val isLeftHalf = startX < (width / 2f)
                                    dragMode = if (isLeftHalf) {
                                        DragMode.VERTICAL_LEFT_BRIGHTNESS
                                    } else {
                                        DragMode.VERTICAL_RIGHT_VOLUME
                                    }
                                }
                            }
                        }

                        // Process ongoing drag according to classified mode
                        when (dragMode) {
                            DragMode.HORIZONTAL_SEEK -> {
                                onHorizontalSwipeSeekChange(totalDragX, width)
                            }
                            DragMode.VERTICAL_LEFT_BRIGHTNESS -> {
                                // Upwards drag -> increase brightness; Downwards -> decrease
                                val deltaFraction = -dragAmount.y / height.toFloat()
                                onVerticalSwipeBrightnessChange(deltaFraction)
                            }
                            DragMode.VERTICAL_RIGHT_VOLUME -> {
                                // Upwards drag -> increase volume; Downwards -> decrease
                                val deltaFraction = -dragAmount.y / height.toFloat()
                                onVerticalSwipeVolumeChange(deltaFraction)
                            }
                            DragMode.NONE -> {
                                // Within touch slop, waiting for direction lock
                            }
                        }
                    },
                    onDragEnd = {
                        when (dragMode) {
                            DragMode.HORIZONTAL_SEEK -> onHorizontalSwipeSeekEnd()
                            DragMode.VERTICAL_LEFT_BRIGHTNESS,
                            DragMode.VERTICAL_RIGHT_VOLUME -> onVerticalSwipeEnd()
                            DragMode.NONE -> {}
                        }
                        dragMode = DragMode.NONE
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragCancel = {
                        when (dragMode) {
                            DragMode.HORIZONTAL_SEEK -> onHorizontalSwipeSeekEnd()
                            DragMode.VERTICAL_LEFT_BRIGHTNESS,
                            DragMode.VERTICAL_RIGHT_VOLUME -> onVerticalSwipeEnd()
                            DragMode.NONE -> {}
                        }
                        dragMode = DragMode.NONE
                        totalDragX = 0f
                        totalDragY = 0f
                    }
                )
            }
    )
}
