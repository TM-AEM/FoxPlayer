package com.example

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.AppLanguage
import com.example.data.repository.AppSettingsRepositoryImpl
import com.example.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocaleIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope

    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repo: com.example.data.repository.AppSettingsRepository

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        ShadowLooper.idleMainLooper()
        dataStoreFile = tempFolder.newFile(
            "test_locale_settings_${System.currentTimeMillis()}_${System.nanoTime()}.preferences_pb"
        )
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { dataStoreFile }
        )
        repo = AppSettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        ShadowLooper.idleMainLooper()
        if (::dataStoreScope.isInitialized) {
            dataStoreScope.cancel()
        }
        if (::dataStoreFile.isInitialized && dataStoreFile.exists()) {
            dataStoreFile.delete()
        }
    }

    private fun flushLocaleWork() {
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun testMainActivityLaunchesAsAppCompatActivityWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
                assertTrue(activity is androidx.appcompat.app.AppCompatActivity)
            }
        }
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun testDirectAppCompatDelegateLocales() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            println("IN_ACTIVITY_BEFORE: '${AppCompatDelegate.getApplicationLocales().toLanguageTags()}'")
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
            ShadowLooper.idleMainLooper()
            println("IN_ACTIVITY_AFTER: '${AppCompatDelegate.getApplicationLocales().toLanguageTags()}'")
            assertTrue(
                "Expected 'ar' in '${AppCompatDelegate.getApplicationLocales().toLanguageTags()}'",
                AppCompatDelegate.getApplicationLocales().toLanguageTags().contains("ar")
            )
        }
    }

    @Test
    fun testSelectingArabicAppliesAppCompatLocales() = testScope.runTest {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val context = ApplicationProvider.getApplicationContext<Application>()
            val viewModel = SettingsViewModel(context, repo)

            viewModel.onLanguageSelected(AppLanguage.ARABIC)
            flushLocaleWork()

            val saved = repo.appSettingsFlow.first()
            assertEquals(AppLanguage.ARABIC, saved.language)

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            assertTrue(
                "Expected 'ar' in '${currentLocales.toLanguageTags()}'",
                currentLocales.toLanguageTags().contains("ar")
            )
        }
    }

    @Test
    fun testSelectingEnglishAppliesAppCompatLocales() = testScope.runTest {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val context = ApplicationProvider.getApplicationContext<Application>()
            val viewModel = SettingsViewModel(context, repo)

            viewModel.onLanguageSelected(AppLanguage.ENGLISH)
            flushLocaleWork()

            val saved = repo.appSettingsFlow.first()
            assertEquals(AppLanguage.ENGLISH, saved.language)

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            assertTrue(currentLocales.toLanguageTags().contains("en"))
        }
    }

    @Test
    fun testSelectingSystemResetsAppCompatLocales() = testScope.runTest {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val context = ApplicationProvider.getApplicationContext<Application>()
            val viewModel = SettingsViewModel(context, repo)

            viewModel.onLanguageSelected(AppLanguage.ARABIC)
            flushLocaleWork()
            assertTrue(AppCompatDelegate.getApplicationLocales().toLanguageTags().contains("ar"))

            viewModel.onLanguageSelected(AppLanguage.SYSTEM)
            flushLocaleWork()

            val saved = repo.appSettingsFlow.first()
            assertEquals(AppLanguage.SYSTEM, saved.language)

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            assertTrue(currentLocales.isEmpty || currentLocales.toLanguageTags().isEmpty())
        }
    }

    @Test
    fun testDefaultViewModelFactoryCanConstructSettingsViewModel() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(
                    ViewModelStore(),
                    ViewModelProvider.AndroidViewModelFactory.getInstance(
                        activity.application
                    )
                )[SettingsViewModel::class.java]
                assertNotNull(viewModel)
                assertEquals(AppLanguage.SYSTEM, viewModel.uiState.value.language)
            }
        }
    }

    @Test
    fun testActivityRecreationUnderArabicLocale() = testScope.runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val productionRepo = AppSettingsRepositoryImpl.getInstance(context)
        try {
            productionRepo.updateLanguage(AppLanguage.ARABIC)
            flushLocaleWork()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
            ShadowLooper.idleMainLooper()

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertNotNull(activity)
                }
                scenario.recreate()
                flushLocaleWork()
                scenario.onActivity { activity ->
                    assertNotNull(activity)
                    assertTrue(activity is androidx.appcompat.app.AppCompatActivity)
                    assertTrue(
                        "Arabic locale must survive recreation, was '${AppCompatDelegate.getApplicationLocales().toLanguageTags()}'",
                        AppCompatDelegate.getApplicationLocales().toLanguageTags().contains("ar")
                    )
                }
                assertEquals(AppLanguage.ARABIC, productionRepo.appSettingsFlow.first().language)
            }
        } finally {
            productionRepo.updateLanguage(AppLanguage.SYSTEM)
            flushLocaleWork()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            ShadowLooper.idleMainLooper()
        }
    }

    @Test
    fun testRepeatedLanguageChangesDoNotCrash() = testScope.runTest {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val context = ApplicationProvider.getApplicationContext<Application>()
            val viewModel = SettingsViewModel(context, repo)

            viewModel.onLanguageSelected(AppLanguage.ARABIC)
            flushLocaleWork()

            viewModel.onLanguageSelected(AppLanguage.ENGLISH)
            flushLocaleWork()

            viewModel.onLanguageSelected(AppLanguage.ARABIC)
            flushLocaleWork()

            viewModel.onLanguageSelected(AppLanguage.SYSTEM)
            flushLocaleWork()

            scenario.onActivity { activity ->
                assertNotNull(activity)
            }

            val saved = repo.appSettingsFlow.first()
            assertEquals(AppLanguage.SYSTEM, saved.language)
            val currentLocales = AppCompatDelegate.getApplicationLocales()
            assertTrue(currentLocales.isEmpty || currentLocales.toLanguageTags().isEmpty())
        }
    }

    @Test
    fun testSavedLanguageRestoredAfterProcessRestart() = testScope.runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val productionRepo = AppSettingsRepositoryImpl.getInstance(context)
        try {
            productionRepo.updateLanguage(AppLanguage.ARABIC)
            flushLocaleWork()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            ShadowLooper.idleMainLooper()

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                flushLocaleWork()
                scenario.onActivity { activity ->
                    assertNotNull(activity)
                    assertTrue(
                        "Saved Arabic locale must be restored on launch, was '${AppCompatDelegate.getApplicationLocales().toLanguageTags()}'",
                        AppCompatDelegate.getApplicationLocales().toLanguageTags().contains("ar")
                    )
                }
            }
        } finally {
            productionRepo.updateLanguage(AppLanguage.SYSTEM)
            flushLocaleWork()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            ShadowLooper.idleMainLooper()
        }
    }
}
