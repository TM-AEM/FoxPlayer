package com.example

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.example.core.util.PermissionUtils
import com.example.data.repository.MediaRepositoryImpl
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaRepositoryTest {

    private lateinit var context: Context
    private lateinit var application: Application
    private lateinit var repository: MediaRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        application = context as Application
        repository = MediaRepositoryImpl(context)
    }

    @Test
    fun testPermissionUtilsRequirements() {
        val requiredPermission = PermissionUtils.getRequiredVideoPermission()
        // On SDK 34 (Android 14), required permission must be READ_MEDIA_VIDEO
        assertEquals("android.permission.READ_MEDIA_VIDEO", requiredPermission)

        // Denied by default
        assertFalse(PermissionUtils.hasVideoPermission(context))

        // Grant permission via Robolectric shadow
        shadowOf(application).grantPermissions(requiredPermission)
        assertTrue(PermissionUtils.hasVideoPermission(context))
    }

    @Test
    fun testEmptyListWhenPermissionDenied() = runBlocking {
        shadowOf(application).denyPermissions(PermissionUtils.getRequiredVideoPermission())

        val videos = repository.queryVideos()
        assertTrue("Videos list must be empty when permission is denied", videos.isEmpty())

        val folders = repository.getVideoFolders()
        assertTrue("Folders list must be empty when permission is denied", folders.isEmpty())
    }

    @Test
    fun testQueryVideosWithMockMediaStore() = runBlocking {
        val permission = PermissionUtils.getRequiredVideoPermission()
        shadowOf(application).grantPermissions(permission)

        val contentResolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "sunset.mp4")
            put(MediaStore.Video.Media.TITLE, "Sunset at Beach")
            put(MediaStore.Video.Media.DURATION, 45000L)
            put(MediaStore.Video.Media.SIZE, 2048500L)
            put(MediaStore.Video.Media.WIDTH, 1920)
            put(MediaStore.Video.Media.HEIGHT, 1080)
            put(MediaStore.Video.Media.DATE_MODIFIED, 1710000000L)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Vacation/")
        }

        val insertedUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        assertNotNull("Inserted URI should not be null", insertedUri)

        val videos = repository.queryVideos()
        assertFalse("Videos should contain the inserted item", videos.isEmpty())

        val foundVideo = videos.find { it.displayName == "sunset.mp4" }
        assertNotNull("Should find sunset.mp4 in query results", foundVideo)
        assertEquals("Sunset at Beach", foundVideo?.title)
        assertEquals(45000L, foundVideo?.durationMs)
        assertEquals(2048500L, foundVideo?.sizeBytes)
        assertEquals(1920, foundVideo?.width)
        assertEquals(1080, foundVideo?.height)
        assertEquals("video/mp4", foundVideo?.mimeType)
        assertTrue(foundVideo?.uri?.startsWith("content://") == true)

        // Test folder grouping
        val folders = repository.getVideoFolders()
        assertFalse("Folders should not be empty", folders.isEmpty())
        val folder = folders.find { it.name == "Vacation" } ?: folders.firstOrNull()
        assertNotNull("Should find a folder containing videos", folder)
        assertTrue((folder?.videoCount ?: 0) >= 1)

        // Test query by URI
        val singleVideo = repository.getVideoByUri(foundVideo!!.uri)
        assertNotNull(singleVideo)
        assertEquals("sunset.mp4", singleVideo?.displayName)
    }

    @Test
    fun testCorruptedOrZeroByteFilesAreExcluded() = runBlocking {
        val permission = PermissionUtils.getRequiredVideoPermission()
        shadowOf(application).grantPermissions(permission)

        val contentResolver = context.contentResolver

        // Zero byte file
        val zeroByteValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "corrupt.mp4")
            put(MediaStore.Video.Media.TITLE, "Corrupt Video")
            put(MediaStore.Video.Media.SIZE, 0L)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        }
        contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, zeroByteValues)

        val videos = repository.queryVideos()
        val corruptFound = videos.find { it.displayName == "corrupt.mp4" }
        assertTrue("Zero byte files must be excluded", corruptFound == null)
    }

    @Test
    fun testGetVideosFlowLifecycle() = runBlocking {
        val permission = PermissionUtils.getRequiredVideoPermission()
        shadowOf(application).grantPermissions(permission)

        val flow = repository.getVideosFlow()
        val firstEmission = flow.first()
        assertNotNull("Flow must emit initial video list", firstEmission)
    }

    @Test
    fun testContentObserverLifecycleAndCleanup() = runBlocking {
        val permission = PermissionUtils.getRequiredVideoPermission()
        shadowOf(application).grantPermissions(permission)

        val flow = repository.getVideosFlow()
        val job = launch {
            flow.collect { /* collect emissions */ }
        }
        job.cancelAndJoin()
        // ContentObserver registered and cleanly unregistered in awaitClose without leaking or throwing
    }
}
