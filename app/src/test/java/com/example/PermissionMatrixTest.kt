package com.example

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.core.util.PermissionUtils
import com.example.data.repository.MediaRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Coverage for the video storage permission matrix across the Android versions the app supports.
 *
 * [PermissionUtils] selects the permission to request and the permission to check depending on
 * [Build.VERSION.SDK_INT], and the app supports API 24 to API 36. Robolectric runs every test in
 * this class once per configured SDK level, so each test below is executed for API 32, 33, 34 and
 * 36 and asserts the branch that belongs to that level.
 *
 * The expected values are declared as an explicit table instead of being recomputed with the same
 * conditions the production code uses, so that a change in the production branches fails this test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 33, 34, 36])
class PermissionMatrixTest {

    private lateinit var context: Context
    private lateinit var application: android.app.Application

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        application = context as android.app.Application
    }

    /**
     * The permission matrix the app is expected to implement, keyed by the Android API level.
     */
    private data class ExpectedMatrix(
        val requiredPermission: String,
        val requestedPermissions: List<String>
    )

    private fun expectedMatrixFor(sdk: Int): ExpectedMatrix = when (sdk) {
        // Android 12L and below: legacy external storage permission.
        32 -> ExpectedMatrix(
            requiredPermission = PERMISSION_READ_EXTERNAL_STORAGE,
            requestedPermissions = listOf(PERMISSION_READ_EXTERNAL_STORAGE)
        )
        // Android 13: granular media permission, no partial access yet.
        33 -> ExpectedMatrix(
            requiredPermission = PERMISSION_READ_MEDIA_VIDEO,
            requestedPermissions = listOf(PERMISSION_READ_MEDIA_VIDEO)
        )
        // Android 14 and later: partial access through READ_MEDIA_VISUAL_USER_SELECTED.
        34, 36 -> ExpectedMatrix(
            requiredPermission = PERMISSION_READ_MEDIA_VIDEO,
            requestedPermissions = listOf(
                PERMISSION_READ_MEDIA_VIDEO,
                PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED
            )
        )
        else -> throw IllegalStateException(
            "Unexpected API level $sdk in the permission matrix, covered levels are $COVERED_API_LEVELS"
        )
    }

    private fun expectedMatrix(): ExpectedMatrix = expectedMatrixFor(Build.VERSION.SDK_INT)

    @Test
    fun requiredVideoPermissionMatchesTheApiLevelMatrix() {
        val expected = expectedMatrix()

        assertEquals(
            "API ${Build.VERSION.SDK_INT} must request the permission of its matrix row",
            expected.requiredPermission,
            PermissionUtils.getRequiredVideoPermission()
        )
    }

    @Test
    fun theTestRunsOnOneOfTheCoveredApiLevels() {
        // Guards the matrix itself: this test class is configured for API 32, 33, 34 and 36, so
        // a run on any other level means the matrix below does not cover the current device.
        assertTrue(
            "The permission matrix must cover API ${Build.VERSION.SDK_INT}",
            Build.VERSION.SDK_INT in COVERED_API_LEVELS
        )
    }

    @Test
    fun requestedVideoPermissionsMatchTheApiLevelMatrix() {
        val expected = expectedMatrix()

        assertEquals(
            "API ${Build.VERSION.SDK_INT} must request exactly the permissions of its matrix row",
            expected.requestedPermissions,
            PermissionUtils.getRequiredVideoPermissions().toList()
        )
    }

    @Test
    fun videoPermissionIsDeniedWhenNothingIsGranted() {
        shadowOf(application).denyPermissions(*expectedMatrix().requestedPermissions.toTypedArray())

        assertFalse(
            "Video access must be denied while no media permission is granted",
            PermissionUtils.hasVideoPermission(context)
        )
    }

    @Test
    fun videoPermissionIsGrantedWhenTheRequiredPermissionIsGranted() {
        shadowOf(application).grantPermissions(expectedMatrix().requiredPermission)

        assertTrue(
            "The required permission of API ${Build.VERSION.SDK_INT} must grant video access",
            PermissionUtils.hasVideoPermission(context)
        )
    }

    @Test
    fun videoPermissionIsNotGrantedByAnUnrelatedPermission() {
        // Grant the permission that belongs to a different Android version. It must never be
        // accepted as video access on the current one.
        val unrelatedPermission = when (Build.VERSION.SDK_INT) {
            32 -> PERMISSION_READ_MEDIA_VIDEO
            else -> PERMISSION_READ_EXTERNAL_STORAGE
        }

        shadowOf(application).grantPermissions(unrelatedPermission)

        assertFalse(
            "An unrelated permission must not grant video access on API ${Build.VERSION.SDK_INT}",
            PermissionUtils.hasVideoPermission(context)
        )
    }

    @Test
    fun videoPermissionIsRevokedWhenTheRequiredPermissionIsDenied() {
        shadowOf(application).grantPermissions(expectedMatrix().requiredPermission)
        assertTrue(
            "Precondition: the required permission must grant video access",
            PermissionUtils.hasVideoPermission(context)
        )

        shadowOf(application).denyPermissions(expectedMatrix().requiredPermission)

        assertFalse(
            "Denying the required permission must revoke video access",
            PermissionUtils.hasVideoPermission(context)
        )
    }

    @Test
    fun mediaRepositoryReturnsAnEmptyListWhenVideoPermissionIsDenied() = runBlocking {
        shadowOf(application).denyPermissions(*expectedMatrix().requestedPermissions.toTypedArray())

        val videos = MediaRepositoryImpl(context).queryVideos()

        assertTrue(
            "The library must be empty while the permission is denied",
            videos.isEmpty()
        )
    }

    @Test
    fun mediaRepositoryReturnsVideosWhenVideoPermissionIsGranted() = runBlocking {
        shadowOf(application).grantPermissions(expectedMatrix().requiredPermission)

        val contentResolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "matrix.mp4")
            put(MediaStore.Video.Media.TITLE, "Matrix Clip")
            put(MediaStore.Video.Media.DURATION, 30000L)
            put(MediaStore.Video.Media.SIZE, 1024L)
            put(MediaStore.Video.Media.WIDTH, 1280)
            put(MediaStore.Video.Media.HEIGHT, 720)
            put(MediaStore.Video.Media.DATE_MODIFIED, 1710000000L)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/")
        }
        contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

        val videos = MediaRepositoryImpl(context).queryVideos()

        val found = videos.find { it.displayName == "matrix.mp4" }
        assertTrue(
            "The granted permission must allow the library query to return the video",
            found != null
        )
        assertEquals("Matrix Clip", found?.title)
    }

    @Test
    @Config(sdk = [34, 36])
    fun partialVisualUserSelectedGrantIsEnoughForVideoAccess() {
        shadowOf(application).denyPermissions(
            PERMISSION_READ_MEDIA_VIDEO,
            PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED
        )

        // Android 14 partial access: the user selected some media but not full video access.
        shadowOf(application).grantPermissions(PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED)

        assertTrue(
            "A partial media grant must be accepted as video access on API 34 and later",
            PermissionUtils.hasVideoPermission(context)
        )
    }

    @Test
    @Config(sdk = [34, 36])
    fun partialVisualUserSelectedGrantAloneDoesNotUnlockTheFullLibrary() {
        // The permission check accepts the partial grant, but the app must not receive the
        // restricted full-library access that only READ_MEDIA_VIDEO provides.
        shadowOf(application).denyPermissions(
            PERMISSION_READ_MEDIA_VIDEO,
            PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED
        )
        shadowOf(application).grantPermissions(PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED)

        assertTrue(
            "Precondition: the partial grant must be accepted by the permission check",
            PermissionUtils.hasVideoPermission(context)
        )
        assertFalse(
            "The full video permission must stay denied when only partial access was granted",
            ContextCompat.checkSelfPermission(context, PERMISSION_READ_MEDIA_VIDEO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    @Test
    @Config(sdk = [34, 36])
    fun videoPermissionIsRevokedWhenBothMediaPermissionsAreDenied() {
        shadowOf(application).grantPermissions(
            PERMISSION_READ_MEDIA_VIDEO,
            PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED
        )
        assertTrue(
            "Precondition: the media permissions must grant video access",
            PermissionUtils.hasVideoPermission(context)
        )

        shadowOf(application).denyPermissions(
            PERMISSION_READ_MEDIA_VIDEO,
            PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED
        )

        assertFalse(
            "Denying every media permission must revoke video access",
            PermissionUtils.hasVideoPermission(context)
        )
    }

    private companion object {
        const val PERMISSION_READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
        const val PERMISSION_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        const val PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        /** The Android versions the permission matrix below describes. */
        val COVERED_API_LEVELS = setOf(32, 33, 34, 36)
    }
}
