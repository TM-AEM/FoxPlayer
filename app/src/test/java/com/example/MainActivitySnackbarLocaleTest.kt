package com.example

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.MediaStore
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.AppTheme
import com.example.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies that the playback failure Snackbar follows the app locale that the settings screen
 * applies through [AppCompatDelegate.setApplicationLocales].
 *
 * This is the real integration path of [com.example.ui.settings.SettingsViewModel] and of
 * [MainActivity], and not a resource qualifier of the test, so the Arabic copy of the failure
 * message and of the retry action is resolved through the locale that the app actually applies.
 *
 * The host is [MainActivity] because only an [androidx.appcompat.app.AppCompatActivity] receives
 * the locale that [AppCompatDelegate.setApplicationLocales] applies.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class MainActivitySnackbarLocaleTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var application: android.app.Application
    private lateinit var context: Context

    /** Id of the video inserted into the MediaStore, used to address its library card. */
    private var videoId: Long = 0L

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        application = context as android.app.Application
        shadowOf(application).grantPermissions("android.permission.READ_EXTERNAL_STORAGE")

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
        ShadowLooper.idleMainLooper()

        videoId = insertVideo()
    }

    @After
    fun tearDown() {
        // The app locale is global state, it must not leak into the other test classes.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        ShadowLooper.idleMainLooper()
    }

    /**
     * Inserts a single playable video into the MediaStore so that the library renders a card that
     * can be clicked. Returns the MediaStore id of the inserted row.
     */
    private fun insertVideo(): Long {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "snackbar_locale_regression.mp4")
            put(MediaStore.Video.Media.TITLE, "Snackbar Locale Regression")
            put(MediaStore.Video.Media.DURATION, 45_000L)
            put(MediaStore.Video.Media.SIZE, 2_048_500L)
            put(MediaStore.Video.Media.WIDTH, 1920)
            put(MediaStore.Video.Media.HEIGHT, 1080)
            put(MediaStore.Video.Media.DATE_MODIFIED, 1_710_000_000L)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/")
        }
        val uri = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        )
        assertTrue("The regression video must be inserted into the MediaStore", uri != null)
        return uri!!.lastPathSegment!!.toLong()
    }

    /**
     * Wraps the host activity and makes every service start attempt fail, so that
     * [com.example.core.playback.PlaybackService.startPlayback] really returns `false` through its
     * own production fallback path. No production code is mocked.
     */
    private class ServiceStartContext(base: Context) : ContextWrapper(base) {

        val startAttempts = AtomicInteger(0)

        override fun startForegroundService(intent: Intent): ComponentName? {
            startAttempts.incrementAndGet()
            throw IllegalStateException("simulated foreground service start failure")
        }

        override fun startService(intent: Intent): ComponentName? {
            startAttempts.incrementAndGet()
            throw IllegalStateException("simulated service start failure")
        }
    }

    @Test
    fun theFailureSnackbarUsesTheArabicCopyAfterTheAppLocaleWasApplied() {
        // The Arabic copy has to be the one that the applied app locale resolves.
        val arabicMessage =
            composeTestRule.activity.getString(R.string.player_error_playback_start_failed)
        val arabicRetry = composeTestRule.activity.getString(R.string.library_retry)

        assertEquals(
            "The applied app locale must resolve the Arabic failure copy",
            "تعذر بدء تشغيل الفيديو. يرجى المحاولة مرة أخرى.",
            arabicMessage
        )
        assertEquals(
            "The applied app locale must resolve the Arabic retry label",
            "إعادة المحاولة",
            arabicRetry
        )

        val failingContext = ServiceStartContext(composeTestRule.activity)
        // MainActivity already populated its own content, so the composition that renders the
        // production home screen is replaced inside its ComposeView.
        val contentView = composeTestRule.activity.findViewById<ViewGroup>(android.R.id.content)
        val composeView = contentView.getChildAt(0) as ComposeView
        composeView.setContent {
            CompositionLocalProvider(LocalContext provides failingContext) {
                MyApplicationTheme(
                    appTheme = AppTheme.LIGHT,
                    darkTheme = false,
                    dynamicColor = false
                ) {
                    FoxPlayerHome()
                }
            }
        }

        val cardTag = "video_card_$videoId"
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithTag(cardTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag(cardTag).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasText(arabicMessage)).assertIsDisplayed()
        composeTestRule
            .onNode(hasText(arabicRetry) and hasClickAction())
            .assertIsDisplayed()
    }
}
