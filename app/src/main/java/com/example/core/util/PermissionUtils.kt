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
     * Checks whether the application has been granted permission to read local video media.
     */
    fun hasVideoPermission(context: Context): Boolean {
        val permission = getRequiredVideoPermission()
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
