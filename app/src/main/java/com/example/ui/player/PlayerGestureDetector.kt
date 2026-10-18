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
    onLongPressStart: () -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    onCenterTap: () -> Unit = onSingleTap,
    gestureSettings: GestureSettingsState = GestureSettingsState(),
    modifier: Modifier = Modifier
) {
    var componentSize by remember { mutableStateOf(IntSize.Zero) }

    var isLongPressing by remember { mutableStateOf(false) }
    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val currentGestureSettings by androidx.compose.runtime.rememberUpdatedState(gestureSettings)
    val currentOnSingleTap by androidx.compose.runtime.rememberUpdatedState(onSingleTap)
    val currentOnCenterTap by androidx.compose.runtime.rememberUpdatedState(onCenterTap)
    val currentOnDoubleTapSeek by androidx.compose.runtime.rememberUpdatedState(onDoubleTapSeek)
    val currentOnHorizontalSwipeSeekStart by androidx.compose.runtime.rememberUpdatedState(onHorizontalSwipeSeekStart)
    val currentOnHorizontalSwipeSeekChange by androidx.compose.runtime.rememberUpdatedState(onHorizontalSwipeSeekChange)
    val currentOnHorizontalSwipeSeekEnd by androidx.compose.runtime.rememberUpdatedState(onHorizontalSwipeSeekEnd)
    val currentOnVerticalSwipeBrightnessChange by androidx.compose.runtime.rememberUpdatedState(onVerticalSwipeBrightnessChange)
    val currentOnVerticalSwipeVolumeChange by androidx.compose.runtime.rememberUpdatedState(onVerticalSwipeVolumeChange)
    val currentOnVerticalSwipeEnd by androidx.compose.runtime.rememberUpdatedState(onVerticalSwipeEnd)
    val currentOnLongPressStart by androidx.compose.runtime.rememberUpdatedState(onLongPressStart)
    val currentOnLongPressEnd by androidx.compose.runtime.rememberUpdatedState(onLongPressEnd)

    val defaultTouchSlop = 18f

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { componentSize = it }
            .testTag("player_gesture_detector")
            // Tap gestures: Single tap toggle HUD, Double tap ±10s seek, Long press 2x speed boost
            .pointerInput(componentSize) {
                detectTapGestures(
                    onPress = {
                        isLongPressing = false
                        try {
                            tryAwaitRelease()
                        } finally {
                            if (isLongPressing) {
                                isLongPressing = false
                                currentOnLongPressEnd()
                            }
                        }
                    },
                    onLongPress = {
                        isLongPressing = true
                        currentOnLongPressStart()
                    },
                    onTap = { offset ->
                        val width = componentSize.width
                        val height = componentSize.height
                        // Check if tap was in the center area (middle 36% horizontally and vertically)
                        val isCenterTap = if (width > 0 && height > 0) {
                            val inCenterX = offset.x in (width * 0.32f)..(width * 0.68f)
                            val inCenterY = offset.y in (height * 0.25f)..(height * 0.75f)
                            inCenterX && inCenterY
                        } else {
                            false
                        }

                        if (isCenterTap) {
                            currentOnCenterTap()
                        } else {
                            currentOnSingleTap()
                        }
                    },
                    onDoubleTap = { offset ->
                        val halfWidth = componentSize.width / 2
                        if (halfWidth > 0) {
                            val isForward = offset.x >= halfWidth
                            currentOnDoubleTapSeek(isForward)
                        }
                    }
                )
            }
            // Swipe / Drag gestures: Horizontal Seek, Vertical Volume (Right), Vertical Brightness (Left)
            .pointerInput(componentSize) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (isLongPressing) {
                            isLongPressing = false
                            currentOnLongPressEnd()
                        }
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

                            val startX = change.position.x - totalDragX
                            val isLeftHalf = startX < (width / 2f)
                            val effectiveVerticalSlop = if (isLeftHalf) {
                                currentGestureSettings.brightnessSensitivity.touchSlop
                            } else {
                                currentGestureSettings.volumeSensitivity.touchSlop
                            }

                            if (absX > defaultTouchSlop || absY > effectiveVerticalSlop) {
                                if (absX > absY) {
                                    dragMode = DragMode.HORIZONTAL_SEEK
                                    currentOnHorizontalSwipeSeekStart()
                                } else {
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
                                currentOnHorizontalSwipeSeekChange(totalDragX, width)
                            }
                            DragMode.VERTICAL_LEFT_BRIGHTNESS -> {
                                // Upwards drag -> increase brightness; Downwards -> decrease
                                val multiplier = currentGestureSettings.brightnessSensitivity.multiplier
                                val deltaFraction = (-dragAmount.y / height.toFloat()) * multiplier
                                currentOnVerticalSwipeBrightnessChange(deltaFraction)
                            }
                            DragMode.VERTICAL_RIGHT_VOLUME -> {
                                // Upwards drag -> increase volume; Downwards -> decrease
                                val multiplier = currentGestureSettings.volumeSensitivity.multiplier
                                val deltaFraction = (-dragAmount.y / height.toFloat()) * multiplier
                                currentOnVerticalSwipeVolumeChange(deltaFraction)
                            }
                            DragMode.NONE -> {
                                // Within touch slop, waiting for direction lock
                            }
                        }
                    },
                    onDragEnd = {
                        when (dragMode) {
                            DragMode.HORIZONTAL_SEEK -> currentOnHorizontalSwipeSeekEnd()
                            DragMode.VERTICAL_LEFT_BRIGHTNESS,
                            DragMode.VERTICAL_RIGHT_VOLUME -> currentOnVerticalSwipeEnd()
                            DragMode.NONE -> {}
                        }
                        dragMode = DragMode.NONE
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragCancel = {
                        when (dragMode) {
                            DragMode.HORIZONTAL_SEEK -> currentOnHorizontalSwipeSeekEnd()
                            DragMode.VERTICAL_LEFT_BRIGHTNESS,
                            DragMode.VERTICAL_RIGHT_VOLUME -> currentOnVerticalSwipeEnd()
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
