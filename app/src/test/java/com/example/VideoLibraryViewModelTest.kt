package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.VideoFolder
import com.example.core.model.VideoItem
import com.example.data.repository.MediaRepository
import com.example.ui.library.VideoLibraryViewModel
import com.example.ui.library.VideoSortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoLibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application

    private val sampleVideos = listOf(
        VideoItem(
            id = 1L,
            uri = "content://media/external/video/media/1",
            title = "Alpha Video",
            displayName = "alpha.mp4",
            durationMs = 120_000L,
            sizeBytes = 50_000_000L,
            dateModified = 1000L
        ),
        VideoItem(
            id = 2L,
            uri = "content://media/external/video/media/2",
            title = "Beta Video",
            displayName = "beta.mp4",
            durationMs = 300_000L,
            sizeBytes = 10_000_000L,
            dateModified = 3000L
        ),
        VideoItem(
            id = 3L,
            uri = "content://media/external/video/media/3",
            title = "Gamma Video",
            displayName = "gamma.mp4",
            durationMs = 60_000L,
            sizeBytes = 100_000_000L,
            dateModified = 2000L
        )
    )

    private val fakeRepository = object : MediaRepository {
        override fun getVideosFlow(): Flow<List<VideoItem>> = flowOf(sampleVideos)
        override suspend fun queryVideos(): List<VideoItem> = sampleVideos
        override suspend fun getVideoByUri(contentUriString: String): VideoItem? = sampleVideos.find { it.uri == contentUriString }
        override suspend fun getVideoFolders(): List<VideoFolder> = emptyList()
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialPermissionGrantedLoadsVideos() = runTest(testDispatcher) {
        val viewModel = VideoLibraryViewModel(application, fakeRepository)
        viewModel.onPermissionResult(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Permission should be granted", state.isPermissionGranted)
        assertFalse("Should not be loading", state.isLoading)
        assertEquals(3, state.videos.size)
    }

    @Test
    fun testSearchFiltering() = runTest(testDispatcher) {
        val viewModel = VideoLibraryViewModel(application, fakeRepository)
        viewModel.onPermissionResult(true)
        advanceUntilIdle()

        // Search for "Alpha"
        viewModel.onSearchQueryChanged("alpha")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.videos.size)
        assertEquals("Alpha Video", state.videos[0].title)

        // Clear search
        viewModel.onSearchQueryChanged("")
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.videos.size)
    }

    @Test
    fun testSortingOptions() = runTest(testDispatcher) {
        val viewModel = VideoLibraryViewModel(application, fakeRepository)
        viewModel.onPermissionResult(true)
        advanceUntilIdle()

        // Sort by Date Descending (Default): Beta (3000) -> Gamma (2000) -> Alpha (1000)
        viewModel.onSortOptionChanged(VideoSortOption.DATE_DESC)
        assertEquals(listOf(2L, 3L, 1L), viewModel.uiState.value.videos.map { it.id })

        // Sort by Date Ascending: Alpha (1000) -> Gamma (2000) -> Beta (3000)
        viewModel.onSortOptionChanged(VideoSortOption.DATE_ASC)
        assertEquals(listOf(1L, 3L, 2L), viewModel.uiState.value.videos.map { it.id })

        // Sort by Name Ascending: Alpha -> Beta -> Gamma
        viewModel.onSortOptionChanged(VideoSortOption.NAME_ASC)
        assertEquals(listOf(1L, 2L, 3L), viewModel.uiState.value.videos.map { it.id })

        // Sort by Duration Descending: Beta (300s) -> Alpha (120s) -> Gamma (60s)
        viewModel.onSortOptionChanged(VideoSortOption.DURATION_DESC)
        assertEquals(listOf(2L, 1L, 3L), viewModel.uiState.value.videos.map { it.id })

        // Sort by Size Descending: Gamma (100MB) -> Alpha (50MB) -> Beta (10MB)
        viewModel.onSortOptionChanged(VideoSortOption.SIZE_DESC)
        assertEquals(listOf(3L, 1L, 2L), viewModel.uiState.value.videos.map { it.id })
    }

    @Test
    fun testPermissionDeniedState() = runTest(testDispatcher) {
        val viewModel = VideoLibraryViewModel(application, fakeRepository)
        viewModel.onPermissionResult(false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Permission should be denied", state.isPermissionGranted)
        assertFalse("Should not be loading", state.isLoading)
        assertTrue("Videos list should be empty", state.videos.isEmpty())
    }

    @Test
    fun testRepeatedRefreshDeduplication() = runTest(testDispatcher) {
        val viewModel = VideoLibraryViewModel(application, fakeRepository)
        viewModel.onPermissionResult(true)
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.videos.size)

        // Trigger multiple rapid refresh operations
        viewModel.refresh()
        viewModel.refresh()
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Should not be loading after refreshes complete", state.isLoading)
        assertEquals(3, state.videos.size)
        assertTrue("Permission should remain granted", state.isPermissionGranted)
    }
}
