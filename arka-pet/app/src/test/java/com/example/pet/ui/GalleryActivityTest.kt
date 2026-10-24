package com.example.pet.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.pet.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

// API 34 + Conscrypt off: no linux-aarch64 native runtime (see PermissionActivityTest).
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
class GalleryActivityTest {

    @Before
    fun cleanSlate() {
        File(RuntimeEnvironment.getApplication().filesDir, "pets").deleteRecursively()
    }

    @Test
    fun showsAllThreePetsAsRows() {
        val activity = launch()
        val min = activity.resources.getDimensionPixelSize(R.dimen.row_min_height)

        for (name in PET_NAMES) {
            val row = rows(activity).firstOrNull { it.text.contains(name) }
            assertTrue("missing row for $name", row != null)
            assertTrue("$name row is below the touch-target height", row!!.minimumHeight >= min)
        }
    }

    @Test
    fun gearButton_opensSettings() {
        val activity = launch()

        val gear = walk(activity.window.decorView).firstOrNull { it.contentDescription == "Pengaturan" }
        assertTrue("settings entry missing from the header", gear != null)
        gear!!.performClick()

        assertEquals(
            SettingsActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )
    }

    @Test
    fun rowClick_opensDetailWithThatPetId() {
        val activity = launch()

        rows(activity).first { it.text.contains("Panda") }.performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(PetDetailActivity::class.java.name, intent.component?.className)
        assertEquals("panda", intent.getStringExtra(PetDetailActivity.EXTRA_PET_ID))
    }

    @Test
    fun onResume_reflectsNewlyInstalledPet() {
        val controller = Robolectric.buildActivity(GalleryActivity::class.java).setup()
        val activity = controller.get()
        // Write through the activity's own filesDir so a path mismatch can't mask the result.
        File(activity.filesDir, "pets/panda/.installed")
            .apply { parentFile.mkdirs(); createNewFile() }

        // Re-enter onResume the way returning from the detail screen does.
        controller.pause().stop().start().resume()

        val row = rows(activity).first { it.text.contains("Panda") }
        assertTrue("status not refreshed on resume: ${row.text}", row.text.contains("Terpasang"))
    }

    private fun launch(): GalleryActivity =
        Robolectric.buildActivity(GalleryActivity::class.java).setup().get()

    private fun rows(activity: GalleryActivity): List<TextView> =
        walk(activity.window.decorView)
            .filterIsInstance<TextView>()
            .filter { row -> PET_NAMES.any { row.text.contains(it) } }

    private fun walk(view: View): List<View> {
        val children = (view as? ViewGroup)?.let { g ->
            (0 until g.childCount).flatMap { walk(g.getChildAt(it)) }
        }.orEmpty()
        return listOf(view) + children
    }

    private companion object {
        val PET_NAMES = listOf("Panda", "Rabbit", "Arch-chan")
    }
}
