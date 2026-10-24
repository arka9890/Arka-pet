package com.example.pet.ui

import android.net.Uri
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowSettings

// API 35 needs Robolectric's native runtime, which has no linux-aarch64 build (this CI);
// Conscrypt is off for the same reason.
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
class PermissionActivityTest {
    @Test
    fun missingPermission_routesToOverlaySettings() {
        ShadowSettings.setCanDrawOverlays(false)

        val activity = Robolectric.buildActivity(PermissionActivity::class.java).setup().get()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals(Uri.parse("package:com.example.pet"), intent.data)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun grantedPermission_finishesWithoutOpeningSettings() {
        ShadowSettings.setCanDrawOverlays(true)

        val activity = Robolectric.buildActivity(PermissionActivity::class.java).setup().get()

        assertTrue(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
