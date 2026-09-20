package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.core.thumbnail.ThumbnailEngineImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThumbnailEngineTest {

    private lateinit var application: Application
    private lateinit var thumbnailEngine: ThumbnailEngineImpl

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        thumbnailEngine = ThumbnailEngineImpl(application, maxMemoryCacheKb = 1024)
    }

    @Test
    fun testCacheOperations() {
        // Initially empty
        assertNull(thumbnailEngine.getCachedThumbnail("content://media/external/video/media/1"))
        assertEquals(0, thumbnailEngine.getCacheCount())

        // Clear empty cache does not crash
        thumbnailEngine.clearCache()
        assertEquals(0, thumbnailEngine.getCacheCount())
    }

    @Test
    fun testNonExistentUriReturnsNullWithoutCrashing() = runBlocking {
        val result = thumbnailEngine.loadThumbnail("content://invalid/uri/999999")
        assertNull("Invalid URI should safely return null", result)
    }
}
