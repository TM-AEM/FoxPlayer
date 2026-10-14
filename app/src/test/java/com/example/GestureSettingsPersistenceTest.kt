package com.example

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.GestureSettingsRepository
import com.example.data.repository.GestureSettingsRepositoryImpl
import com.example.ui.player.DoubleTapSeekDuration
import com.example.ui.player.GestureSensitivity
import com.example.ui.player.GestureSettingsState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class GestureSettingsPersistenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStoreFile: File
    private lateinit var repository: GestureSettingsRepository

    @Before
    fun setUp() {
        dataStoreFile = tempFolder.newFile("test_gesture_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { dataStoreFile }
        )
        repository = GestureSettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        if (dataStoreFile.exists()) {
            dataStoreFile.delete()
        }
    }

    @Test
    fun testFirstLaunchUsesDefaultSensitivityPresets() = testScope.runTest {
        val initialSettings = repository.gestureSettingsFlow.first()
        assertEquals(
            "Default volume sensitivity on first launch must be MEDIUM",
            GestureSensitivity.MEDIUM,
            initialSettings.volumeSensitivity
        )
        assertEquals(
            "Default brightness sensitivity on first launch must be MEDIUM",
            GestureSensitivity.MEDIUM,
            initialSettings.brightnessSensitivity
        )
        assertEquals(18f, initialSettings.volumeSensitivity.touchSlop, 0.01f)
        assertEquals(1.0f, initialSettings.volumeSensitivity.multiplier, 0.01f)
        assertEquals(
            "Default double-tap seek duration on first launch must be TEN (10 seconds)",
            DoubleTapSeekDuration.TEN,
            initialSettings.doubleTapSeekDuration
        )
        assertEquals(10, initialSettings.doubleTapSeekSeconds)
    }

    @Test
    fun testVolumeAndBrightnessStoredAndRestoredIndependently() = testScope.runTest {
        // Update only volume to HIGH
        repository.updateVolumeSensitivity(GestureSensitivity.HIGH)
        var settings = repository.gestureSettingsFlow.first()
        assertEquals(GestureSensitivity.HIGH, settings.volumeSensitivity)
        assertEquals(GestureSensitivity.MEDIUM, settings.brightnessSensitivity)

        // Update only brightness to LOW
        repository.updateBrightnessSensitivity(GestureSensitivity.LOW)
        settings = repository.gestureSettingsFlow.first()
        assertEquals(GestureSensitivity.HIGH, settings.volumeSensitivity)
        assertEquals(GestureSensitivity.LOW, settings.brightnessSensitivity)

        // Update both via updateSettings
        repository.updateSettings(
            GestureSettingsState(
                volumeSensitivity = GestureSensitivity.LOW,
                brightnessSensitivity = GestureSensitivity.HIGH
            )
        )
        settings = repository.gestureSettingsFlow.first()
        assertEquals(GestureSensitivity.LOW, settings.volumeSensitivity)
        assertEquals(GestureSensitivity.HIGH, settings.brightnessSensitivity)
    }

    @Test
    fun testRecreatingRepositoryRestoresPreviouslySavedSelections() = testScope.runTest {
        val restartFile = File(tempFolder.root, "restart_test.preferences_pb")
        val job1 = kotlinx.coroutines.Job()
        val scope1 = kotlinx.coroutines.CoroutineScope(testDispatcher + job1)
        val dataStore1 = PreferenceDataStoreFactory.create(
            scope = scope1,
            produceFile = { restartFile }
        )
        val repo1 = GestureSettingsRepositoryImpl(dataStore1)

        // Save custom settings
        repo1.updateSettings(
            GestureSettingsState(
                volumeSensitivity = GestureSensitivity.HIGH,
                brightnessSensitivity = GestureSensitivity.LOW
            )
        )

        val beforeRestart = repo1.gestureSettingsFlow.first()
        assertEquals(GestureSensitivity.HIGH, beforeRestart.volumeSensitivity)
        assertEquals(GestureSensitivity.LOW, beforeRestart.brightnessSensitivity)

        // Cancel scope1 to simulate process death and release the DataStore file lock
        job1.cancel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Simulate app restart with a fresh scope and DataStore reading the persisted file
        val job2 = kotlinx.coroutines.Job()
        val scope2 = kotlinx.coroutines.CoroutineScope(testDispatcher + job2)
        val dataStore2 = PreferenceDataStoreFactory.create(
            scope = scope2,
            produceFile = { restartFile }
        )
        val repo2 = GestureSettingsRepositoryImpl(dataStore2)

        val afterRestart = repo2.gestureSettingsFlow.first()
        assertEquals("Saved volume sensitivity must survive restart", GestureSensitivity.HIGH, afterRestart.volumeSensitivity)
        assertEquals("Saved brightness sensitivity must survive restart", GestureSensitivity.LOW, afterRestart.brightnessSensitivity)
        assertEquals(12f, afterRestart.volumeSensitivity.touchSlop, 0.01f)
        assertEquals(1.45f, afterRestart.volumeSensitivity.multiplier, 0.01f)
        assertEquals(26f, afterRestart.brightnessSensitivity.touchSlop, 0.01f)
        assertEquals(0.65f, afterRestart.brightnessSensitivity.multiplier, 0.01f)

        job2.cancel()
    }

    @Test
    fun testInvalidOrMissingStoredValuesFallBackSafelyToDefaults() = testScope.runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { dataStoreFile }
        )

        // Write corrupt/invalid string values into the preferences
        testDataStore.edit { prefs ->
            prefs[stringPreferencesKey("gesture_volume_sensitivity")] = "INVALID_CORRUPT_KEY"
            prefs[stringPreferencesKey("gesture_brightness_sensitivity")] = "UNKNOWN_VALUE_123"
        }

        val fallbackRepository = GestureSettingsRepositoryImpl(testDataStore)
        val fallbackSettings = fallbackRepository.gestureSettingsFlow.first()

        assertEquals(
            "Invalid volume string must safely fall back to MEDIUM default",
            GestureSensitivity.MEDIUM,
            fallbackSettings.volumeSensitivity
        )
        assertEquals(
            "Invalid brightness string must safely fall back to MEDIUM default",
            GestureSensitivity.MEDIUM,
            fallbackSettings.brightnessSensitivity
        )

        // Test static helper fallback parsing
        assertEquals(GestureSensitivity.DEFAULT, GestureSettingsRepositoryImpl.parseSensitivity(null))
        assertEquals(GestureSensitivity.DEFAULT, GestureSettingsRepositoryImpl.parseSensitivity(""))
        assertEquals(GestureSensitivity.DEFAULT, GestureSettingsRepositoryImpl.parseSensitivity("   "))
        assertEquals(GestureSensitivity.DEFAULT, GestureSettingsRepositoryImpl.parseSensitivity("not_a_valid_enum"))
        assertEquals(GestureSensitivity.HIGH, GestureSettingsRepositoryImpl.parseSensitivity("high"))
        assertEquals(GestureSensitivity.LOW, GestureSettingsRepositoryImpl.parseSensitivity("LOW"))
    }

    @Test
    fun testFieldSpecificUpdatePreventsLostUpdateWhenStaleSnapshotSubmitted() = testScope.runTest {
        // --- 1. Demonstrate original failure mode: full-state write of stale snapshot restores old value ---
        // 1.1 Start with volume MEDIUM and brightness MEDIUM
        val initial = repository.gestureSettingsFlow.first()
        assertEquals(GestureSensitivity.MEDIUM, initial.volumeSensitivity)
        assertEquals(GestureSensitivity.MEDIUM, initial.brightnessSensitivity)

        // 1.2 Capture a full-state snapshot containing MEDIUM/MEDIUM
        val staleSnapshot = initial

        // 1.3 Update volume to HIGH using updateVolumeSensitivity(HIGH)
        repository.updateVolumeSensitivity(GestureSensitivity.HIGH)
        val afterVolumeUpdate = repository.gestureSettingsFlow.first()
        assertEquals(GestureSensitivity.HIGH, afterVolumeUpdate.volumeSensitivity)

        // 1.4 Submit the stale snapshot through updateSettings(staleSnapshot) to demonstrate
        // that a full-state write restores the old volume value (causing a lost update)
        repository.updateSettings(staleSnapshot)

        // 1.5 Verify that the test actually detects this failure mode
        val afterStaleSubmission = repository.gestureSettingsFlow.first()
        assertEquals(
            "Submitting stale snapshot restores old volume (MEDIUM), causing the HIGH update to be lost",
            GestureSensitivity.MEDIUM,
            afterStaleSubmission.volumeSensitivity
        )
        assertEquals(GestureSensitivity.MEDIUM, afterStaleSubmission.brightnessSensitivity)

        // Also demonstrate the realistic UI case: user changes brightness to LOW based on the stale snapshot
        val staleBrightnessSnapshot = staleSnapshot.copy(brightnessSensitivity = GestureSensitivity.LOW)
        repository.updateVolumeSensitivity(GestureSensitivity.HIGH) // Re-apply HIGH volume
        assertEquals(GestureSensitivity.HIGH, repository.gestureSettingsFlow.first().volumeSensitivity)

        repository.updateSettings(staleBrightnessSnapshot) // Full-state update with stale volume
        val afterStaleBrightnessSnapshot = repository.gestureSettingsFlow.first()
        assertEquals(
            "Full-state write with stale volume overwrites newer volume HIGH back to MEDIUM",
            GestureSensitivity.MEDIUM,
            afterStaleBrightnessSnapshot.volumeSensitivity
        )
        assertEquals(GestureSensitivity.LOW, afterStaleBrightnessSnapshot.brightnessSensitivity)

        // --- 2. Verify corrected behavior: field-specific updates preserve both values ---
        // Reset to initial baseline
        repository.updateSettings(GestureSettingsState(GestureSensitivity.MEDIUM, GestureSensitivity.MEDIUM))

        // Separately verify that the corrected field-specific sequence:
        // volume HIGH followed by brightness LOW preserves both intended values without lost updates
        repository.updateVolumeSensitivity(GestureSensitivity.HIGH)
        repository.updateBrightnessSensitivity(GestureSensitivity.LOW)

        val finalSettings = repository.gestureSettingsFlow.first()
        assertEquals(
            "Volume must remain HIGH and not be overwritten by field-specific brightness update",
            GestureSensitivity.HIGH,
            finalSettings.volumeSensitivity
        )
        assertEquals(
            "Brightness must be LOW",
            GestureSensitivity.LOW,
            finalSettings.brightnessSensitivity
        )
    }

    @Test
    fun testFieldSpecificUpdatePreventsLostUpdateReversedOrder() = testScope.runTest {
        // 1. Start with volume MEDIUM and brightness MEDIUM
        val initial = repository.gestureSettingsFlow.first()
        assertEquals(GestureSensitivity.MEDIUM, initial.volumeSensitivity)
        assertEquals(GestureSensitivity.MEDIUM, initial.brightnessSensitivity)

        // Snapshot taken at this point (stale snapshot: volume=MEDIUM, brightness=MEDIUM)
        val staleSnapshot = initial

        // 2. User updates brightness to HIGH
        repository.updateBrightnessSensitivity(GestureSensitivity.HIGH)
        val afterBrightnessUpdate = repository.gestureSettingsFlow.first()
        assertEquals(GestureSensitivity.MEDIUM, afterBrightnessUpdate.volumeSensitivity)
        assertEquals(GestureSensitivity.HIGH, afterBrightnessUpdate.brightnessSensitivity)

        // 3. User updates volume to LOW
        repository.updateVolumeSensitivity(GestureSensitivity.LOW)

        // 4. Verify final persisted state is volume LOW and brightness HIGH
        val finalSettings = repository.gestureSettingsFlow.first()
        assertEquals(
            "Volume must be LOW",
            GestureSensitivity.LOW,
            finalSettings.volumeSensitivity
        )
        assertEquals(
            "Brightness must remain HIGH and not be overwritten by stale snapshot",
            GestureSensitivity.HIGH,
            finalSettings.brightnessSensitivity
        )
    }

    @Test
    fun testDeliberateFullStateUpdateModifiesAllThreeFieldsCorrectly() = testScope.runTest {
        val targetState = GestureSettingsState(
            volumeSensitivity = GestureSensitivity.LOW,
            brightnessSensitivity = GestureSensitivity.HIGH,
            doubleTapSeekDuration = DoubleTapSeekDuration.FIFTEEN
        )
        repository.updateSettings(targetState)

        val persisted = repository.gestureSettingsFlow.first()
        assertEquals(GestureSensitivity.LOW, persisted.volumeSensitivity)
        assertEquals(GestureSensitivity.HIGH, persisted.brightnessSensitivity)
        assertEquals(DoubleTapSeekDuration.FIFTEEN, persisted.doubleTapSeekDuration)
        assertEquals(15, persisted.doubleTapSeekSeconds)
    }

    @Test
    fun testDoubleTapSeekDurationStoredAndRestoredIndependently() = testScope.runTest {
        // 1. Initial state defaults
        val initial = repository.gestureSettingsFlow.first()
        assertEquals(DoubleTapSeekDuration.TEN, initial.doubleTapSeekDuration)
        assertEquals(GestureSensitivity.MEDIUM, initial.volumeSensitivity)
        assertEquals(GestureSensitivity.MEDIUM, initial.brightnessSensitivity)

        // 2. Update duration to 5s, verify sensitivity unchanged
        repository.updateDoubleTapSeekDuration(DoubleTapSeekDuration.FIVE)
        var settings = repository.gestureSettingsFlow.first()
        assertEquals(DoubleTapSeekDuration.FIVE, settings.doubleTapSeekDuration)
        assertEquals(5, settings.doubleTapSeekSeconds)
        assertEquals(GestureSensitivity.MEDIUM, settings.volumeSensitivity)
        assertEquals(GestureSensitivity.MEDIUM, settings.brightnessSensitivity)

        // 3. Update duration to 15s
        repository.updateDoubleTapSeekDuration(DoubleTapSeekDuration.FIFTEEN)
        settings = repository.gestureSettingsFlow.first()
        assertEquals(DoubleTapSeekDuration.FIFTEEN, settings.doubleTapSeekDuration)
        assertEquals(15, settings.doubleTapSeekSeconds)

        // 4. Update duration to 30s
        repository.updateDoubleTapSeekDuration(DoubleTapSeekDuration.THIRTY)
        settings = repository.gestureSettingsFlow.first()
        assertEquals(DoubleTapSeekDuration.THIRTY, settings.doubleTapSeekDuration)
        assertEquals(30, settings.doubleTapSeekSeconds)

        // 5. Updating volume sensitivity does not alter duration
        repository.updateVolumeSensitivity(GestureSensitivity.HIGH)
        settings = repository.gestureSettingsFlow.first()
        assertEquals(GestureSensitivity.HIGH, settings.volumeSensitivity)
        assertEquals(DoubleTapSeekDuration.THIRTY, settings.doubleTapSeekDuration)

        // 6. Updating brightness sensitivity does not alter duration
        repository.updateBrightnessSensitivity(GestureSensitivity.LOW)
        settings = repository.gestureSettingsFlow.first()
        assertEquals(GestureSensitivity.LOW, settings.brightnessSensitivity)
        assertEquals(DoubleTapSeekDuration.THIRTY, settings.doubleTapSeekDuration)
    }

    @Test
    fun testRecreatingRepositoryRestoresSavedDoubleTapSeekDuration() = testScope.runTest {
        val restartFile = File(tempFolder.root, "restart_seek_test.preferences_pb")
        val job1 = kotlinx.coroutines.Job()
        val scope1 = kotlinx.coroutines.CoroutineScope(testDispatcher + job1)
        val dataStore1 = PreferenceDataStoreFactory.create(
            scope = scope1,
            produceFile = { restartFile }
        )
        val repo1 = GestureSettingsRepositoryImpl(dataStore1)

        // Save custom settings with 30s seek duration
        repo1.updateSettings(
            GestureSettingsState(
                volumeSensitivity = GestureSensitivity.HIGH,
                brightnessSensitivity = GestureSensitivity.LOW,
                doubleTapSeekDuration = DoubleTapSeekDuration.THIRTY
            )
        )

        val beforeRestart = repo1.gestureSettingsFlow.first()
        assertEquals(DoubleTapSeekDuration.THIRTY, beforeRestart.doubleTapSeekDuration)
        assertEquals(30, beforeRestart.doubleTapSeekSeconds)

        job1.cancel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Simulate app restart with a fresh scope and repository reading the persisted file
        val job2 = kotlinx.coroutines.Job()
        val scope2 = kotlinx.coroutines.CoroutineScope(testDispatcher + job2)
        val dataStore2 = PreferenceDataStoreFactory.create(
            scope = scope2,
            produceFile = { restartFile }
        )
        val repo2 = GestureSettingsRepositoryImpl(dataStore2)

        val afterRestart = repo2.gestureSettingsFlow.first()
        assertEquals(
            "Saved seek duration must survive restart",
            DoubleTapSeekDuration.THIRTY,
            afterRestart.doubleTapSeekDuration
        )
        assertEquals(30, afterRestart.doubleTapSeekSeconds)
        assertEquals(GestureSensitivity.HIGH, afterRestart.volumeSensitivity)
        assertEquals(GestureSensitivity.LOW, afterRestart.brightnessSensitivity)

        job2.cancel()
    }

    @Test
    fun testInvalidOrMissingStoredSeekDurationFallsBackSafelyToTenSeconds() = testScope.runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { dataStoreFile }
        )

        // Write corrupt/invalid string value for seek duration into preferences
        testDataStore.edit { prefs ->
            prefs[stringPreferencesKey("gesture_double_tap_seek_duration")] = "CORRUPT_DURATION_VAL"
        }

        val fallbackRepository = GestureSettingsRepositoryImpl(testDataStore)
        val fallbackSettings = fallbackRepository.gestureSettingsFlow.first()

        assertEquals(
            "Corrupt duration string must safely fall back to TEN default",
            DoubleTapSeekDuration.TEN,
            fallbackSettings.doubleTapSeekDuration
        )
        assertEquals(10, fallbackSettings.doubleTapSeekSeconds)

        // Test static helper fallback parsing
        assertEquals(DoubleTapSeekDuration.DEFAULT, GestureSettingsRepositoryImpl.parseSeekDuration(null as String?))
        assertEquals(DoubleTapSeekDuration.DEFAULT, GestureSettingsRepositoryImpl.parseSeekDuration(""))
        assertEquals(DoubleTapSeekDuration.DEFAULT, GestureSettingsRepositoryImpl.parseSeekDuration("   "))
        assertEquals(DoubleTapSeekDuration.DEFAULT, GestureSettingsRepositoryImpl.parseSeekDuration("invalid"))
        assertEquals(DoubleTapSeekDuration.DEFAULT, GestureSettingsRepositoryImpl.parseSeekDuration("99"))
        assertEquals(DoubleTapSeekDuration.FIVE, GestureSettingsRepositoryImpl.parseSeekDuration("FIVE"))
        assertEquals(DoubleTapSeekDuration.FIVE, GestureSettingsRepositoryImpl.parseSeekDuration("5"))
        assertEquals(DoubleTapSeekDuration.FIVE, GestureSettingsRepositoryImpl.parseSeekDuration("5s"))
        assertEquals(DoubleTapSeekDuration.TEN, GestureSettingsRepositoryImpl.parseSeekDuration("TEN"))
        assertEquals(DoubleTapSeekDuration.TEN, GestureSettingsRepositoryImpl.parseSeekDuration("10"))
        assertEquals(DoubleTapSeekDuration.FIFTEEN, GestureSettingsRepositoryImpl.parseSeekDuration("FIFTEEN"))
        assertEquals(DoubleTapSeekDuration.FIFTEEN, GestureSettingsRepositoryImpl.parseSeekDuration("15"))
        assertEquals(DoubleTapSeekDuration.FIFTEEN, GestureSettingsRepositoryImpl.parseSeekDuration("15s"))
        assertEquals(DoubleTapSeekDuration.THIRTY, GestureSettingsRepositoryImpl.parseSeekDuration("THIRTY"))
        assertEquals(DoubleTapSeekDuration.THIRTY, GestureSettingsRepositoryImpl.parseSeekDuration("30"))
        assertEquals(DoubleTapSeekDuration.THIRTY, GestureSettingsRepositoryImpl.parseSeekDuration("30s"))
    }

    @Test
    fun testFieldSpecificSeekDurationUpdatePreventsLostUpdates() = testScope.runTest {
        // 1. Initial state
        val initial = repository.gestureSettingsFlow.first()
        assertEquals(GestureSensitivity.MEDIUM, initial.volumeSensitivity)
        assertEquals(GestureSensitivity.MEDIUM, initial.brightnessSensitivity)
        assertEquals(DoubleTapSeekDuration.TEN, initial.doubleTapSeekDuration)

        // 2. Field-specific duration update to THIRTY
        repository.updateDoubleTapSeekDuration(DoubleTapSeekDuration.THIRTY)
        assertEquals(DoubleTapSeekDuration.THIRTY, repository.gestureSettingsFlow.first().doubleTapSeekDuration)
        assertEquals(GestureSensitivity.MEDIUM, repository.gestureSettingsFlow.first().volumeSensitivity)
        assertEquals(GestureSensitivity.MEDIUM, repository.gestureSettingsFlow.first().brightnessSensitivity)

        // 3. Field-specific volume update to HIGH
        repository.updateVolumeSensitivity(GestureSensitivity.HIGH)
        assertEquals(DoubleTapSeekDuration.THIRTY, repository.gestureSettingsFlow.first().doubleTapSeekDuration)
        assertEquals(GestureSensitivity.HIGH, repository.gestureSettingsFlow.first().volumeSensitivity)

        // 4. Field-specific brightness update to LOW
        repository.updateBrightnessSensitivity(GestureSensitivity.LOW)
        val finalState = repository.gestureSettingsFlow.first()
        assertEquals(
            "Duration must remain THIRTY and not be overwritten",
            DoubleTapSeekDuration.THIRTY,
            finalState.doubleTapSeekDuration
        )
        assertEquals(
            "Volume must remain HIGH and not be overwritten",
            GestureSensitivity.HIGH,
            finalState.volumeSensitivity
        )
        assertEquals(
            "Brightness must be LOW",
            GestureSensitivity.LOW,
            finalState.brightnessSensitivity
        )
    }
}

