package com.example

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.AppLanguage
import com.example.core.model.AppSettings
import com.example.data.repository.AppSettingsRepositoryImpl
import com.example.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocaleIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testDispatcher: StandardTestDispatcher
    private lateinit var testScope: TestScope

    private lateinit var dataStoreFile: File
    private lateinit var repo: com.example.data.repository.AppSettingsRepository

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        dataStoreFile = tempFolder.newFile("test_locale_settings_${System.currentTimeMillis()}_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { dataStoreFile }
        )
        repo = AppSettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        if (::dataStoreFile.isInitialized && dataStoreFile.exists()) {
            dataStoreFile.delete()
        }
    }

    @Test
    fun testMainActivityLaunchesAsAppCompatActivityWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
                assertTrue(activity is androidx.appcompat.app.AppCompatActivity)
            }
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    @Test
    fun testDirectAppCompatDelegateLocales() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            println("IN_ACTIVITY_BEFORE: '${AppCompatDelegate.getApplicationLocales().toLanguageTags()}'")
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            println("IN_ACTIVITY_AFTER: '${AppCompatDelegate.getApplicationLocales().toLanguageTags()}'")
        }
    }

    @Test
    fun testSelectingArabicAppliesAppCompatLocales() = testScope.runTest {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            val viewModel = SettingsViewModel(context, repo, testScope.backgroundScope)

            viewModel.onLanguageSelected(AppLanguage.ARABIC)
            testDispatcher.scheduler.advanceUntilIdle()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            // Verify DataStore persistence
            val saved = repo.appSettingsFlow.first()
            assertEquals(AppLanguage.ARABIC, saved.language)

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            assertTrue("Expected 'ar' in '${currentLocales.toLanguageTags()}'", currentLocales.toLanguageTags().contains("ar"))
        }
    }

    @Test
    fun testSelectingEnglishAppliesAppCompatLocales() = testScope.runTest {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            val viewModel = SettingsViewModel(context, repo, testScope.backgroundScope)

            viewModel.onLanguageSelected(AppLanguage.ENGLISH)
            testDispatcher.scheduler.advanceUntilIdle()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            val saved = repo.appSettingsFlow.first()
            assertEquals(AppLanguage.ENGLISH, saved.language)

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            assertTrue(currentLocales.toLanguageTags().contains("en"))
        }
    }

    @Test
    fun testSelectingSystemResetsAppCompatLocales() = testScope.runTest {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            val viewModel = SettingsViewModel(context, repo, testScope.backgroundScope)

            // First select Arabic
            viewModel.onLanguageSelected(AppLanguage.ARABIC)
            testDispatcher.scheduler.advanceUntilIdle()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertTrue(AppCompatDelegate.getApplicationLocales().toLanguageTags().contains("ar"))

            // Then select System Default
            viewModel.onLanguageSelected(AppLanguage.SYSTEM)
            testDispatcher.scheduler.advanceUntilIdle()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            val saved = repo.appSettingsFlow.first()
            assertEquals(AppLanguage.SYSTEM, saved.language)

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            assertTrue(currentLocales.isEmpty || currentLocales.toLanguageTags().isEmpty())
        }
    }

    @Test
    fun testActivityRecreationUnderArabicLocale() {
        // Set Arabic locale before launch
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
            // Recreate activity to test configuration change lifecycle
            scenario.recreate()
            scenario.onActivity { activity ->
                assertNotNull(activity)
                assertTrue(activity is androidx.appcompat.app.AppCompatActivity)
            }
        }
    }
}
