package com.example.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utility helper for managing video storage permissions across different Android versions.
 * Note: Does not automatically request permissions; only checks and provides permission definitions.
 */
object PermissionUtils {

    /**
     * Returns the required permission string based on the running Android OS version:
     * - Android 13+ (API 33+): Manifest.permission.READ_MEDIA_VIDEO
     * - Android 12 and below (API <= 32): Manifest.permission.READ_EXTERNAL_STORAGE
     */
    fun getRequiredVideoPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    /**
     * Returns an array of permission strings to request based on Android OS version:
     * - Android 14+ (API 34+): Both READ_MEDIA_VIDEO and READ_MEDIA_VISUAL_USER_SELECTED
     * - Android 13 (API 33): READ_MEDIA_VIDEO
     * - Android 12 and below (API <= 32): READ_EXTERNAL_STORAGE
     */
    fun getRequiredVideoPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * Checks whether the application has been granted permission to read local video media.
     */
    fun hasVideoPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 34) {
            val hasVideo = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val hasVisualSelected = ContextCompat.checkSelfPermission(context, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED
            hasVideo || hasVisualSelected
        } else {
            val permission = getRequiredVideoPermission()
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
