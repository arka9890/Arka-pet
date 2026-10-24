package com.example.pet.overlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowSettings

// API 35 needs Robolectric's native runtime, which has no linux-aarch64 build (this CI);
// Conscrypt is off for the same reason.
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
class OverlayPermissionHelperTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun hasOverlayPermission_falseWhenNotGranted() {
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(OverlayPermissionHelper.hasOverlayPermission(context))
    }

    @Test
    fun hasOverlayPermission_trueWhenGranted() {
        ShadowSettings.setCanDrawOverlays(true)
        assertTrue(OverlayPermissionHelper.hasOverlayPermission(context))
    }
}
