package com.example.pet.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import com.example.pet.model.Pet
import com.example.pet.render.PetRenderer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
        // Off the bottom-right: pinned to the screen edge.
        assertEquals(Point(880, 1620), OverlayView.clampPosition(2000, 5000, screenW, screenH, viewW, viewH))
        // View bigger than the screen: pinned to the origin, no negative range crash.
        assertEquals(Point(0, 0), OverlayView.clampPosition(50, 50, 100, 100, viewW, viewH))
    }

    @Test
    fun draw_drivesTheRenderer() {
        val renderer = CountingRenderer(available = true)
        val view = OverlayView(RuntimeEnvironment.getApplication(), renderer)

        view.draw(canvas())

        assertEquals("each draw must advance the animation", 1, renderer.updates)
        assertEquals("each draw must render a frame", 1, renderer.draws)
    }

    @Test
    fun draw_withoutRenderer_showsPlaceholderInsteadOfCrashing() {
        val view = OverlayView(RuntimeEnvironment.getApplication(), null)

        view.draw(canvas()) // no exception = the placeholder path
    }

    @Test
    fun rendererCanBeSwappedAfterAttach() {
        val view = OverlayView(RuntimeEnvironment.getApplication(), null)
        view.draw(canvas())

        val renderer = CountingRenderer(available = true)
        view.renderer = renderer
        view.draw(canvas())

        assertEquals(1, renderer.updates)
    }

    private fun canvas() = Canvas(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))

    private class CountingRenderer(private val available: Boolean) : PetRenderer {
        var updates = 0
        var draws = 0

        override fun isAvailable(): Boolean = available
        override fun load(pet: Pet) = Unit
        override fun update(dt: Float) {
            updates++
        }

        override fun draw(canvas: Canvas) {
            draws++
        }

        override fun onTap() = Unit
    }
}
