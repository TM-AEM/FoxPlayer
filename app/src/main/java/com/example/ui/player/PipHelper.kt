package com.example.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational

/**
 * Utility helper for managing Android Picture-in-Picture (PiP) mode.
 *
 * Guarantees:
 * - Checks device feature support via [PackageManager.FEATURE_PICTURE_IN_PICTURE].
 * - Configures appropriate aspect ratio within Android PiP constraints (0.4184 to 2.3900).
 * - Safe on Android versions prior to Android 8.0 (API 26).
 * - Safe on devices without PiP hardware/OS capability.
 * - Does not manipulate or own the media player.
 */
object PipHelper {

    /**
     * Checks if the device running the app supports Picture-in-Picture.
     */
    fun isPipSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    /**
     * Attempts to enter Picture-in-Picture mode with the specified video aspect ratio.
     * Returns true if enterPictureInPictureMode was successfully invoked, false otherwise.
     */
    fun enterPip(activity: Activity?, videoAspectRatio: Float): Boolean {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        if (!isPipSupported(activity)) {
            return false
        }

        return try {
            val clampedRatio = videoAspectRatio.coerceIn(0.42f, 2.38f)
            val rational = Rational((clampedRatio * 1000).toInt(), 1000)

            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(rational)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setSeamlessResizeEnabled(true)
                builder.setAutoEnterEnabled(true)
            }

            activity.enterPictureInPictureMode(builder.build())
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Updates auto-enter PiP behavior for Android 12+ (API 31+) gesture navigation.
     * When autoEnter is enabled, swiping home automatically transitions into PiP seamlessly.
     */
    fun updateAutoEnterPip(activity: Activity?, videoAspectRatio: Float, isPlaying: Boolean): Boolean {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false
        }
        if (!isPipSupported(activity)) {
            return false
        }
        return try {
            val clampedRatio = videoAspectRatio.coerceIn(0.42f, 2.38f)
            val rational = Rational((clampedRatio * 1000).toInt(), 1000)

            val params = PictureInPictureParams.Builder()
                .setAspectRatio(rational)
                .setAutoEnterEnabled(isPlaying)
                .setSeamlessResizeEnabled(true)
                .build()

            activity.setPictureInPictureParams(params)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Disables auto-enter PiP on Android 12+ (API 31+) when leaving player screen.
     */
    fun disableAutoEnterPip(activity: Activity?) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val params = PictureInPictureParams.Builder()
                .setAutoEnterEnabled(false)
                .build()
            activity.setPictureInPictureParams(params)
        } catch (_: Throwable) {}
    }
}
