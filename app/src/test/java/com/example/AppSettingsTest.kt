package com.example

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.core.model.AppLanguage
import com.example.core.model.AppSettings
import com.example.core.model.AppTheme
import com.example.core.playback.FoxPlayerEngine
import com.example.data.repository.AppSettingsRepository
import com.example.data.repository.AppSettingsRepositoryImpl
import com.example.ui.player.PlayerHud
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppSettingsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var application: Application
    private lateinit var dataStoreFile: File
    private lateinit var repository: AppSettingsRepository

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        dataStoreFile = tempFolder.newFile("test_app_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { dataStoreFile }
        )
        repository = AppSettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        if (dataStoreFile.exists()) {
            dataStoreFile.delete()
        }
    }

    @Test
    fun testDefaultSettings() = testScope.runTest {
        val initial = repository.appSettingsFlow.first()
        assertEquals(AppLanguage.SYSTEM, initial.language)
        assertEquals(AppTheme.SYSTEM, initial.theme)
        assertTrue(initial.resumePlayback)
    }

    @Test
    fun testUpdateLanguage() = testScope.runTest {
        repository.updateLanguage(AppLanguage.ARABIC)
        val arabicState = repository.appSettingsFlow.first()
        assertEquals(AppLanguage.ARABIC, arabicState.language)

        repository.updateLanguage(AppLanguage.ENGLISH)
        val englishState = repository.appSettingsFlow.first()
        assertEquals(AppLanguage.ENGLISH, englishState.language)
    }

    @Test
    fun testUpdateTheme() = testScope.runTest {
        repository.updateTheme(AppTheme.DARK)
        val darkState = repository.appSettingsFlow.first()
        assertEquals(AppTheme.DARK, darkState.theme)

        repository.updateTheme(AppTheme.LIGHT)
        val lightState = repository.appSettingsFlow.first()
        assertEquals(AppTheme.LIGHT, lightState.theme)
    }

    @Test
    fun testUpdateResumePlayback() = testScope.runTest {
        repository.updateResumePlayback(false)
        val disabledState = repository.appSettingsFlow.first()
        assertFalse(disabledState.resumePlayback)

        repository.updateResumePlayback(true)
        val enabledState = repository.appSettingsFlow.first()
        assertTrue(enabledState.resumePlayback)
    }

    @Test
    fun testSettingsViewModelInteractions() = testScope.runTest {
        val viewModel = SettingsViewModel(application, repository)
        viewModel.onLanguageSelected(AppLanguage.ARABIC)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppLanguage.ARABIC, repository.appSettingsFlow.first().language)

        viewModel.onThemeSelected(AppTheme.DARK)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppTheme.DARK, repository.appSettingsFlow.first().theme)

        viewModel.onResumePlaybackToggled(false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(repository.appSettingsFlow.first().resumePlayback)
    }

    @Test
    fun testSettingsScreenComposesAndDisplaysElements() {
        val viewModel = SettingsViewModel(application, repository)
        composeTestRule.setContent {
            SettingsScreen(viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("settings_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_language_item").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_theme_item").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_resume_switch").assertIsDisplayed()
    }

    @Test
    fun testTimelineSliderPreservedUnderRtlContext() {
        composeTestRule.setContent {
            // Emulate an Arabic RTL environment for the whole application
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                PlayerHud(
                    isVisible = true,
                    title = "Test Video",
                    isPlaying = true,
                    currentPositionMs = 15000L,
                    durationMs = 60000L,
                    bufferedPositionMs = 30000L,
                    onPlayPauseClick = {},
                    onSeek = {},
                    onBack = {},
                    onUserInteraction = {},
                    onSettingsClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("player_hud_bottom_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_hud_timeline_slider").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_hud_current_position").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_hud_duration").assertIsDisplayed()
    }
}
