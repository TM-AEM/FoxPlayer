package com.example

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.AppTheme
import com.example.core.playback.PlaybackService
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression coverage for the Snackbar that [FoxPlayerHome] shows when the playback service
 * cannot be started.
 *
 * The failure is produced by handing the composition a [Context] whose service start methods
 * throw, so [com.example.core.playback.PlaybackService.startPlayback] really returns `false`
 * through its own production fallback path. No mock of the production code is involved, therefore
 * a regression in the start failure detection, in the Snackbar wiring or in the localized copy
 * fails these tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivitySnackbarTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var application: android.app.Application
    private lateinit var context: Context

    /** Id of the first video inserted into the MediaStore, used to address its library card. */
    private var videoId: Long = 0L

    /** Id of the second video inserted into the MediaStore, used to address its library card. */
    private var secondVideoId: Long = 0L

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        application = context as android.app.Application
        shadowOf(application).grantPermissions(
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        )
        videoId = insertVideo("snackbar_regression_first.mp4")
        secondVideoId = insertVideo("snackbar_regression_second.mp4")
    }

    /**
     * Inserts a single playable video into the MediaStore so that the library renders a card that
     * can be clicked. Returns the MediaStore id of the inserted row.
     */
    private fun insertVideo(displayName: String): Long {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.TITLE, "Snackbar Regression")
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
     * Wraps the host activity and counts every attempt to start the playback service.
     *
     * When [serviceStartFails] is true both start methods throw, which is what the platform does
     * when a foreground service cannot be launched from the current app state.
     * [com.example.core.playback.PlaybackService.startPlayback] then falls back to a plain service
     * start and finally reports the failure to its caller.
     *
     * The test can flip [serviceStartFails] so that a later attempt, for example the retry of a
     * failed start, succeeds. The production start path itself is never replaced.
     */
    private class ServiceStartContext(
        base: Context,
        failServiceStart: Boolean
    ) : ContextWrapper(base) {

        /** Number of service start attempts, including the ones that throw. */
        val startAttempts = AtomicInteger(0)

        /** Number of service start attempts that the platform accepted. */
        val successfulStarts = AtomicInteger(0)

        /** The video id of every attempted playback start, in call order. */
        val startedVideoIds = mutableListOf<Long>()

        /** Whether the next service start attempt fails. Switched by the test. */
        @Volatile
        var serviceStartFails: Boolean = failServiceStart

        override fun startForegroundService(intent: Intent): ComponentName? {
            record(intent)
            if (serviceStartFails) {
                throw IllegalStateException("simulated foreground service start failure")
            }
            successfulStarts.incrementAndGet()
            return super.startForegroundService(intent)
        }

        override fun startService(intent: Intent): ComponentName? {
            record(intent)
            if (serviceStartFails) {
                throw IllegalStateException("simulated service start failure")
            }
            successfulStarts.incrementAndGet()
            return super.startService(intent)
        }

        private fun record(intent: Intent) {
            startAttempts.incrementAndGet()
            startedVideoIds += intent.getLongExtra(PlaybackService.EXTRA_VIDEO_ID, -1L)
        }

        /** The video id of the most recent playback start attempt. */
        fun lastStartedVideoId(): Long = startedVideoIds.last()
    }

    /**
     * Renders the production home screen with a replaced [LocalContext], so that the service start
     * behaviour of the whole screen is controlled by the test.
     */
    private fun setHomeContent(failingContext: Context) {
        composeTestRule.setContent {
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
    }

    /** Waits until the library card of the given video is on screen and clicks it. */
    private fun clickVideoCard(id: Long = videoId) {
        val cardTag = "video_card_$id"
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithTag(cardTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag(cardTag).performClick()
        composeTestRule.waitForIdle()
    }

    private fun expectedString(resId: Int): String = context.getString(resId)

    /**
     * Makes the MediaSession service binding of the player screen fail in a controlled way.
     *
     * The player screen connects to the playback service through a MediaSession controller. Under
     * Robolectric such a binding succeeds but reports no component name, which makes the Media3
     * controller fail with a null pointer. Declaring the component unbindable makes the binding
     * return false instead, which the player screen already handles, so the screen renders its
     * player surface without a live session.
     */
    private fun declarePlaybackServiceUnbindable() {
        shadowOf(application).declareComponentUnbindable(
            ComponentName(context, PlaybackService::class.java)
        )
    }

    private fun countNodesWithText(text: String): Int =
        composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun playbackStartFailureShowsSnackbarWithTheFailureMessage() {
        val failingContext = ServiceStartContext(composeTestRule.activity, failServiceStart = true)
        setHomeContent(failingContext)

        clickVideoCard()

        composeTestRule
            .onNode(hasText(expectedString(R.string.player_error_playback_start_failed)))
            .assertIsDisplayed()
    }

    @Test
    fun playbackStartFailureSnackbarOffersTheRetryAction() {
        val failingContext = ServiceStartContext(composeTestRule.activity, failServiceStart = true)
        setHomeContent(failingContext)

        clickVideoCard()

        composeTestRule
            .onNode(hasText(expectedString(R.string.library_retry)) and hasClickAction())
            .assertIsDisplayed()
    }

    @Test
    fun playbackStartFailureUsesBothServiceStartAttemptsOfTheProductionFallback() {
        val failingContext = ServiceStartContext(composeTestRule.activity, failServiceStart = true)
        setHomeContent(failingContext)

        clickVideoCard()

        // The production code tries the foreground service first and falls back to a plain
        // service start before it reports the failure.
        assertEquals(
            "The foreground start and its plain fallback must both be attempted",
            2,
            failingContext.startAttempts.get()
        )
    }

    @Test
    fun retryActionRetriesStartingThePlaybackServiceAndOpensThePlayer() {
        val serviceContext = ServiceStartContext(composeTestRule.activity, failServiceStart = true)
        setHomeContent(serviceContext)

        val failureMessage = expectedString(R.string.player_error_playback_start_failed)
        val retryLabel = expectedString(R.string.library_retry)

        // The real start path fails, so the failure Snackbar with its retry action appears.
        clickVideoCard()
        composeTestRule.onNode(hasText(retryLabel) and hasClickAction()).assertIsDisplayed()

        // The retry is allowed to succeed now, the platform accepts the service start.
        serviceContext.serviceStartFails = false
        declarePlaybackServiceUnbindable()

        composeTestRule.onNode(hasText(retryLabel) and hasClickAction()).performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "The retry action must start the playback service once more",
            3,
            serviceContext.startAttempts.get()
        )
        assertEquals(
            "The retried playback start must be accepted by the platform",
            1,
            serviceContext.successfulStarts.get()
        )

        // A successful retry has to open the player, not only to start the service.
        composeTestRule.onNodeWithTag("player_screen_root").assertIsDisplayed()
        assertEquals(
            "The failure Snackbar must not be visible after a successful retry",
            0,
            countNodesWithText(failureMessage)
        )
    }

    @Test
    fun aFailingRetryStartsThePlaybackServiceAgain() {
        val failingContext = ServiceStartContext(composeTestRule.activity, failServiceStart = true)
        setHomeContent(failingContext)

        clickVideoCard()
        val attemptsBeforeRetry = failingContext.startAttempts.get()

        composeTestRule
            .onNode(hasText(expectedString(R.string.library_retry)) and hasClickAction())
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "The retry action must start the playback service once more",
            attemptsBeforeRetry + 2,
            failingContext.startAttempts.get()
        )
    }

    @Test
    fun successfulPlaybackStartShowsNoSnackbarAndOpensThePlayer() {
        val workingContext = ServiceStartContext(composeTestRule.activity, failServiceStart = false)
        setHomeContent(workingContext)
        declarePlaybackServiceUnbindable()

        clickVideoCard()

        composeTestRule.onNodeWithTag("player_screen_root").assertIsDisplayed()
        assertEquals(
            "No failure Snackbar may be shown when the playback service starts",
            0,
            countNodesWithText(expectedString(R.string.player_error_playback_start_failed))
        )
    }

    @Test
    fun aSecondFailureSupersedesTheVisibleSnackbarInsteadOfStackingIt() {
        val failingContext = ServiceStartContext(composeTestRule.activity, failServiceStart = true)
        setHomeContent(failingContext)

        val failureMessage = expectedString(R.string.player_error_playback_start_failed)
        val retryLabel = expectedString(R.string.library_retry)

        // The first failure puts the failure Snackbar on screen.
        clickVideoCard(videoId)
        composeTestRule.onNode(hasText(failureMessage)).assertIsDisplayed()

        // The first Snackbar has to be alive when the second failure happens, otherwise the
        // second failure would not have to supersede anything. This assertion turns a silently
        // expired Snackbar into a loud failure instead of a meaningless pass.
        assertEquals(
            "The first failure Snackbar must still be visible when the second failure happens",
            1,
            countNodesWithText(failureMessage)
        )

        // The second failure happens while the first Snackbar is still on screen.
        clickVideoCard(secondVideoId)

        // The superseded Snackbar is gone, only the one of the second failure is left.
        assertEquals(
            "Only one failure Snackbar may be visible at a time",
            1,
            countNodesWithText(failureMessage)
        )
        assertEquals(
            "Only one retry action may be visible at a time",
            1,
            countNodesWithText(retryLabel)
        )

        // The Snackbar that is on screen belongs to the second failure, so its retry action has
        // to retry the second video. A Snackbar that stacked instead of superseding would still
        // belong to the first failure and would retry the first video instead.
        composeTestRule.onNode(hasText(retryLabel) and hasClickAction()).performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "The retry action must belong to the second failure, the superseded Snackbar must " +
                "not handle it",
            secondVideoId,
            failingContext.lastStartedVideoId()
        )
    }

    @Test
    fun theFailureSnackbarDisappearsOnItsOwnAfterItsShortDuration() {
        val failingContext = ServiceStartContext(composeTestRule.activity, failServiceStart = true)
        setHomeContent(failingContext)

        val failureMessage = expectedString(R.string.player_error_playback_start_failed)

        clickVideoCard()
        composeTestRule.onNode(hasText(failureMessage)).assertIsDisplayed()

        // The Snackbar must not vanish immediately, it has to stay for its whole duration.
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()
        assertEquals(
            "The failure Snackbar must stay visible while its duration has not elapsed yet",
            1,
            countNodesWithText(failureMessage)
        )

        // SnackbarDuration.Short is four seconds while SnackbarDuration.Long is ten seconds and
        // SnackbarDuration.Indefinite never expires, so a six second budget tells them apart.
        composeTestRule.mainClock.advanceTimeBy(5_000)
        composeTestRule.waitForIdle()
        assertEquals(
            "The failure Snackbar must disappear by itself once its duration elapsed",
            0,
            countNodesWithText(failureMessage)
        )
    }

    @Test
    fun navigatingAwayWhileTheSnackbarIsVisibleDoesNotCrash() {
        val failingContext = ServiceStartContext(composeTestRule.activity, failServiceStart = true)
        setHomeContent(failingContext)

        clickVideoCard()
        composeTestRule
            .onNode(hasText(expectedString(R.string.player_error_playback_start_failed)))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("library_settings_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_back_button").assertIsDisplayed()
    }

    @Test
    fun theSnackbarStillWorksAfterNavigatingAwayAndBack() {
        val failingContext = ServiceStartContext(composeTestRule.activity, failServiceStart = true)
        setHomeContent(failingContext)

        clickVideoCard()
        composeTestRule
            .onNode(hasText(expectedString(R.string.player_error_playback_start_failed)))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("library_settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()

        // The Snackbar host state must not be stuck by the dismissed Snackbar, so a new failure
        // has to show a Snackbar again instead of being silently dropped.
        clickVideoCard()

        composeTestRule
            .onNode(hasText(expectedString(R.string.player_error_playback_start_failed)))
            .assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "ar")
    fun playbackStartFailureSnackbarUsesTheArabicCopyForTheArabicLocale() {
        val failingContext = ServiceStartContext(composeTestRule.activity, failServiceStart = true)
        setHomeContent(failingContext)

        clickVideoCard()

        val arabicMessage = context.getString(R.string.player_error_playback_start_failed)

        assertEquals(
            "The Arabic locale must resolve the Arabic copy",
            "تعذر بدء تشغيل الفيديو. يرجى المحاولة مرة أخرى.",
            arabicMessage
        )
        composeTestRule.onNode(hasText(arabicMessage)).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "ar")
    fun playbackStartFailureSnackbarOffersTheArabicRetryLabel() {
        val failingContext = ServiceStartContext(composeTestRule.activity, failServiceStart = true)
        setHomeContent(failingContext)

        clickVideoCard()

        val arabicRetry = context.getString(R.string.library_retry)

        assertEquals(
            "The Arabic locale must resolve the Arabic retry label",
            "إعادة المحاولة",
            arabicRetry
        )
        composeTestRule
            .onNode(hasText(arabicRetry) and hasClickAction())
            .assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "en")
    fun playbackStartFailureSnackbarUsesTheEnglishCopyForTheEnglishLocale() {
        val failingContext = ServiceStartContext(composeTestRule.activity, failServiceStart = true)
        setHomeContent(failingContext)

        clickVideoCard()

        val englishMessage = context.getString(R.string.player_error_playback_start_failed)

        assertEquals(
            "The English locale must resolve the English copy",
            "Failed to start video playback. Please try again.",
            englishMessage
        )
        composeTestRule.onNode(hasText(englishMessage)).assertIsDisplayed()
    }
}
