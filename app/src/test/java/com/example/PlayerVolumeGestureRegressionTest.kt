package com.example

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.example.ui.player.PlayerGestureController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

/**
 * Regression tests for the media volume gesture.
 *
 * These tests drive the real [AudioManager] through Robolectric's shadow, so every test that
 * changes the system volume restores the original value in a `finally` block to keep the
 * tests isolated from each other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerVolumeGestureRegressionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun controller() = PlayerGestureController(context = context, activity = null)

    /**
     * Runs [block] with the media volume restored to its original value afterwards, even when
     * the block fails.
     */
    private fun withRestoredVolume(block: (PlayerGestureController) -> Unit) {
        val original = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            block(controller())
        } finally {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0)
        }
    }

    private fun setVolume(index: Int) {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            index.coerceIn(0, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),
            0
        )
    }

    @Test
    fun adjustVolumeReportsTheVolumeTheSystemActuallyApplied() {
        withRestoredVolume { controller ->
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            setVolume(max / 2)

            val reported = controller.adjustVolume(0.2f)

            val actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val expectedPercent = ((actual.toFloat() / max.toFloat()) * 100f).roundToInt()
            assertEquals(
                "The reported percentage must reflect the volume the system actually applied",
                expectedPercent,
                reported
            )
        }
    }

    @Test
    fun consecutiveSmallSwipesAccumulateInsteadOfBeingDiscarded() {
        withRestoredVolume { controller ->
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            setVolume(5)

            val before = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            // 0.02 of the full scale is 0.3 of a single step at max=15, so a single swipe cannot
            // change the system volume. Repeating it must eventually move the volume.
            repeat(10) { controller.adjustVolume(0.02f) }

            val after = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            assertTrue(
                "Repeated small swipes must accumulate into a real volume change " +
                    "(before=$before, after=$after, max=$max)",
                after > before
            )
        }
    }

    @Test
    fun upwardAndDownwardSmallSwipesAreSymmetric() {
        withRestoredVolume { controller ->
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            setVolume(5)

            repeat(10) { controller.adjustVolume(0.02f) }
            val afterUp = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            repeat(10) { controller.adjustVolume(-0.02f) }
            val afterDown = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            assertEquals(
                "Ten small upward swipes must be undone by ten small downward swipes",
                5,
                afterDown
            )
            assertTrue("The upward swipes must have changed the volume", afterUp != 5)
        }
    }

    @Test
    fun aSingleTinySwipeDoesNotImmediatelyDropAWholeStep() {
        withRestoredVolume { controller ->
            setVolume(8)

            controller.adjustVolume(-0.02f)

            assertEquals(
                "A swipe smaller than one step must not drop the volume by a full step",
                8,
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            )
        }
    }

    @Test
    fun volumeIsClampedToTheSystemMinimum() {
        withRestoredVolume { controller ->
            val min = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            setVolume(min + 1)

            controller.adjustVolume(-10f)

            assertEquals(
                "The volume must never fall below the stream minimum",
                min,
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            )
        }
    }

    @Test
    fun volumeIsClampedToTheSystemMaximum() {
        withRestoredVolume { controller ->
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            setVolume(max - 1)

            controller.adjustVolume(10f)

            assertEquals(
                "The volume must never exceed the stream maximum",
                max,
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            )
        }
    }

    @Test
    fun nonFiniteDeltaIsIgnoredInsteadOfCrashing() {
        withRestoredVolume { controller ->
            setVolume(5)

            val nanResult = controller.adjustVolume(Float.NaN)
            assertEquals(
                "A NaN delta must leave the volume untouched",
                5,
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            )
            assertTrue("A NaN delta must still report a valid percentage", nanResult in 0..100)

            val infinityResult = controller.adjustVolume(Float.POSITIVE_INFINITY)
            assertEquals(
                "An infinite delta must leave the volume untouched",
                5,
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            )
            assertTrue(
                "An infinite delta must still report a valid percentage",
                infinityResult in 0..100
            )
        }
    }

    @Test
    fun zeroDeltaLeavesTheVolumeUntouched() {
        withRestoredVolume { controller ->
            setVolume(5)

            controller.adjustVolume(0f)

            assertEquals(
                5,
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            )
        }
    }

    @Test
    fun resetPendingVolumeAdjustmentDiscardsTheCarriedOverRemainder() {
        withRestoredVolume { controller ->
            setVolume(5)

            // A small swipe that does not reach a whole step leaves a pending position.
            controller.adjustVolume(0.02f)
            assertEquals(
                5,
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            )

            controller.resetPendingVolumeAdjustment()

            // The next gesture must start from the real system volume (5), not from the stale
            // position (5.3). A downward swipe of 0.75 steps lands on 4 when it starts from 5,
            // but on 5 when the stale position leaked into it.
            controller.adjustVolume(-0.05f)

            assertEquals(
                "The discarded position must not leak into a later gesture",
                4,
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            )
        }
    }

    @Test
    fun aGestureThatEndsWithoutReachingAStepDoesNotChangeTheVolume() {
        withRestoredVolume { controller ->
            setVolume(5)

            // A single small swipe does not reach a whole step.
            controller.adjustVolume(0.02f)
            controller.resetPendingVolumeAdjustment()

            assertEquals(
                "A gesture that never reached a whole step must not change the volume",
                5,
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            )
        }
    }

    @Test
    fun anExternalVolumeChangeResynchronisesTheGesturePosition() {
        withRestoredVolume { controller ->
            setVolume(5)

            // Leave a partial step pending.
            controller.adjustVolume(0.02f)
            controller.adjustVolume(0.02f)

            // The user changes the volume with the hardware buttons.
            setVolume(9)

            // The next swipe must start from the external value, not from the stale position.
            controller.adjustVolume(0.02f)

            assertEquals(
                "An external volume change must reset the carried-over position",
                9,
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            )
        }
    }

    @Test
    fun volumePercentIsAlwaysWithinTheValidRange() {
        withRestoredVolume { controller ->
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            setVolume(0)
            assertTrue(controller.currentVolumePercent in 0..100)

            setVolume(max)
            assertTrue(controller.currentVolumePercent in 0..100)

            setVolume(max / 2)
            assertTrue(controller.currentVolumePercent in 0..100)
        }
    }

    @Test
    fun thePlayerGainIsNeverTouched() {
        withRestoredVolume { controller ->
            setVolume(5)

            controller.adjustVolume(0.5f)

            // The gesture contract states that the media gesture only drives the system stream.
            // Reaching the player would require the controller to expose the player, which it
            // deliberately does not.
            assertTrue(
                "The controller must not expose the player",
                !PlayerGestureController::class.java.declaredFields.any {
                    it.type.name.contains("Player")
                }
            )
        }
    }
}
