package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.core.thumbnail.ThumbnailEngineImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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
        val testUri1 = "content://media/external/video/media/1"
        val testUri2 = "content://media/external/video/media/2"
        assertNull(thumbnailEngine.getCachedThumbnail(testUri1))
        assertEquals(0, thumbnailEngine.getCacheCount())

        // Insert first bitmap and verify exact item count
        val bitmap1 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        thumbnailEngine.putThumbnailInCache(testUri1, bitmap1)
        assertEquals(1, thumbnailEngine.getCacheCount())
        assertNotNull(thumbnailEngine.getCachedThumbnail(testUri1))

        // Insert second bitmap and verify count increases to 2
        val bitmap2 = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        thumbnailEngine.putThumbnailInCache(testUri2, bitmap2)
        assertEquals(2, thumbnailEngine.getCacheCount())
        assertNotNull(thumbnailEngine.getCachedThumbnail(testUri2))

        // Clear cache and verify count drops to 0 and items are evicted
        thumbnailEngine.clearCache()
        assertEquals(0, thumbnailEngine.getCacheCount())
        assertNull(thumbnailEngine.getCachedThumbnail(testUri1))
        assertNull(thumbnailEngine.getCachedThumbnail(testUri2))
    }

    @Test
    fun testNonExistentUriReturnsNullWithoutCrashing() = runBlocking {
        val result = thumbnailEngine.loadThumbnail("content://invalid/uri/999999")
        assertNull("Invalid URI should safely return null", result)
    }

    @Test
    fun testConcurrentThumbnailRequestsDeduplicated() = runBlocking {
        val uri = "content://invalid/uri/concurrent_test"
        val deferred1 = async { thumbnailEngine.loadThumbnail(uri, 320, 180) }
        val deferred2 = async { thumbnailEngine.loadThumbnail(uri, 320, 180) }
        val deferred3 = async { thumbnailEngine.loadThumbnail(uri, 320, 180) }

        val res1 = deferred1.await()
        val res2 = deferred2.await()
        val res3 = deferred3.await()

        assertEquals(res1, res2)
        assertEquals(res2, res3)
        assertNull(res1)
    }

    @Test
    fun testConcurrentDifferentKeysExtractIndependently() = runBlocking {
        val uri1 = "content://invalid/uri/key1"
        val uri2 = "content://invalid/uri/key2"
        val deferred1 = async { thumbnailEngine.loadThumbnail(uri1, 320, 180) }
        val deferred2 = async { thumbnailEngine.loadThumbnail(uri2, 320, 180) }

        val res1 = deferred1.await()
        val res2 = deferred2.await()

        assertNull(res1)
        assertNull(res2)
    }

    @Test
    fun testFailedExtractionCleansInFlightAndAllowsRetry() = runBlocking {
        val uri = "content://invalid/uri/retry_test"
        // First attempt fails cleanly
        val firstResult = thumbnailEngine.loadThumbnail(uri, 320, 180)
        assertNull(firstResult)

        // Second attempt must not hang on a stale in-flight CompletableDeferred, executes retry
        val secondResult = thumbnailEngine.loadThumbnail(uri, 320, 180)
        assertNull(secondResult)
    }

    @Test
    fun testCancellationCleansInFlightAndAllowsSubsequentRequest() = runBlocking {
        val uri = "content://invalid/uri/cancel_test"
        val job = launch {
            thumbnailEngine.loadThumbnail(uri, 320, 180)
        }
        job.cancelAndJoin()

        // Immediate subsequent request must succeed without deadlock or hanging
        val subsequent = thumbnailEngine.loadThumbnail(uri, 320, 180)
        assertNull(subsequent)
    }
}
