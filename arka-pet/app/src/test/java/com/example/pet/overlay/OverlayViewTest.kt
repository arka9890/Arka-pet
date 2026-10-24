package com.example.pet.overlay

import android.graphics.Point
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

// android.graphics.Point needs the Android runtime; API 34 because Robolectric has no
// linux-aarch64 native runtime for 35 (same setup as OverlayPermissionHelperTest).
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
class OverlayViewTest {

    @Test
    fun clampPosition_keepsViewOnScreen() {
        val screenW = 1080
        val screenH = 1920
        val viewW = 200
        val viewH = 300

        // Inside: untouched.
        assertEquals(Point(100, 200), OverlayView.clampPosition(100, 200, screenW, screenH, viewW, viewH))
        // Off the top-left: pinned to the origin.
        assertEquals(Point(0, 0), OverlayView.clampPosition(-50, -50, screenW, screenH, viewW, viewH))
        // Off the bottom-right: right/bottom edges stay visible.
        assertEquals(Point(880, 1620), OverlayView.clampPosition(2000, 5000, screenW, screenH, viewW, viewH))
        // View bigger than the screen: pinned to the origin, no negative range crash.
        assertEquals(Point(0, 0), OverlayView.clampPosition(50, 50, 100, 100, viewW, viewH))
    }
}
