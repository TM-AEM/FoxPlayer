package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.AppTheme
import com.example.ui.theme.MyApplicationTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression protection for the saved navigation state of [FoxPlayerHome].
 *
 * The home flow is a saveable enum rather than a navigation graph. A future
 * extraction must not silently reset the user to the library when the activity
 * is recreated while Settings is open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        )
    }

    @Test
    fun settingsIsRestoredAfterSavedStateRestoration() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            MyApplicationTheme(
                appTheme = AppTheme.LIGHT,
                darkTheme = false,
                dynamicColor = false
            ) {
                FoxPlayerHome()
            }
        }

        waitForTag("library_settings_button")
        composeTestRule.onNodeWithTag("library_settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_back_button").assertIsDisplayed()

        // Simulate the activity being recreated while Settings is open.
        restorationTester.emulateSavedInstanceStateRestore()
        waitForTag("settings_back_button")
        composeTestRule.onNodeWithTag("settings_back_button").assertIsDisplayed()

        // Returning from the restored Settings screen must still reach the library.
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        waitForTag("library_settings_button")
        composeTestRule.onNodeWithTag("library_settings_button").assertIsDisplayed()
    }

    private fun waitForTag(tag: String) {
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
