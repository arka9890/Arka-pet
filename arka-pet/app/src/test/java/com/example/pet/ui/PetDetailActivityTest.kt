package com.example.pet.ui

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

// API 34 + Conscrypt off: no linux-aarch64 native runtime (see PermissionActivityTest).
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
class PetDetailActivityTest {

    @Before
    fun cleanSlate() {
        // Keeps the two tests order-independent: an installed marker must not leak.
        File(RuntimeEnvironment.getApplication().filesDir, "pets").deleteRecursively()
    }

    @Test
    fun showsPetMetadata_andDownloadButton() {
        val activity = launch("panda")

        val shown = texts(activity)
        assertTrue("name missing from $shown", shown.any { it.contains("Panda") })
        assertTrue("author missing from $shown", shown.any { it.contains("Kenney") })
        assertEquals("Download", downloadButton(activity).text.toString())
        assertTrue("download button must be tappable", downloadButton(activity).isEnabled)
    }

    @Test
    fun installedPet_offersActivateInsteadOfDownload() {
        File(RuntimeEnvironment.getApplication().filesDir, "pets/panda/.installed")
            .apply { parentFile.mkdirs(); createNewFile() }

        assertEquals("Aktifkan", downloadButton(launch("panda")).text.toString())
    }

    @Test
    fun activatingInstalledPet_activatesWithoutDownloading() {
        File(RuntimeEnvironment.getApplication().filesDir, "pets/panda/.installed")
            .apply { parentFile.mkdirs(); createNewFile() }
        val activity = launch("panda")

        downloadButton(activity).performClick()

        assertTrue(
            "activating must close the screen instead of starting a download",
            activity.isFinishing,
        )
        assertEquals(
            "panda",
            File(RuntimeEnvironment.getApplication().filesDir, "active_pet").readText(),
        )
    }

    private fun launch(petId: String): PetDetailActivity {
        val intent = Intent(RuntimeEnvironment.getApplication(), PetDetailActivity::class.java)
            .putExtra(PetDetailActivity.EXTRA_PET_ID, petId)
        return Robolectric.buildActivity(PetDetailActivity::class.java, intent).setup().get()
    }

    private fun texts(activity: PetDetailActivity): List<String> =
        walk(activity.window.decorView)
            .filterIsInstance<TextView>()
            .map { it.text.toString() }

    private fun downloadButton(activity: PetDetailActivity): Button =
        walk(activity.window.decorView).filterIsInstance<Button>().first()

    private fun walk(view: View): List<View> {
        val children = (view as? ViewGroup)?.let { g ->
            (0 until g.childCount).flatMap { walk(g.getChildAt(it)) }
        }.orEmpty()
        return listOf(view) + children
    }
}
