package com.example

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.ui.player.PipHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PipHelperTest {

    @Test
    fun testPipSupport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowPm = shadowOf(context.packageManager)
        
        shadowPm.setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        val supported = PipHelper.isPipSupported(context)
        // On SDK 36 with feature enabled, isPipSupported must be true
        org.junit.Assert.assertTrue(supported)

        shadowPm.setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, false)
        val notSupported = PipHelper.isPipSupported(context)
        assertFalse(notSupported)
    }

    @Test
    fun testEnterPipNullActivity() {
        val result = PipHelper.enterPip(null, 1.77f)
        assertFalse(result)
    }

    @Test
    fun testUpdateAutoEnterPipNullActivity() {
        val result = PipHelper.updateAutoEnterPip(null, 1.77f, isPlaying = true)
        assertFalse(result)
    }

    @Test
    fun testDisableAutoEnterPipNullActivity() {
        // Should execute safely without throwing exceptions
        PipHelper.disableAutoEnterPip(null)
    }

    @Test
    fun testAutoEnterPipWithActivity() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertNotNull(activity)

        // Setting auto-enter should not crash
        val updated = PipHelper.updateAutoEnterPip(activity, 1.77f, isPlaying = true)
        // Robolectric activity might succeed or catch safely depending on shadow implementation
        // Either true or false, it must never throw an uncaught exception
        PipHelper.disableAutoEnterPip(activity)
    }
}
