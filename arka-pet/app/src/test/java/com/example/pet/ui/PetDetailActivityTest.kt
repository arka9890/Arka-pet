package com.example.pet.ui

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class PetDetailActivityTest {

    @Before
    fun cleanSlate() {
        // Keeps the tests order-independent: an installed marker or an in-flight
        // download entry must not leak between them.
        File(RuntimeEnvironment.getApplication().filesDir, "pets").deleteRecursively()
        ActiveDownloads.clear()
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

    @Test
    fun detailInsets_pushContentClearOfSystemBars() {
        val activity = launch("panda")
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val root = content?.getChildAt(0)
        assertTrue("content view missing", root != null)

        val insets = android.view.WindowInsets.Builder()
            .setInsets(android.view.WindowInsets.Type.systemBars(), android.graphics.Insets.of(0, 100, 0, 60))
            .build()
        root!!.dispatchApplyWindowInsets(insets)

        assertTrue(
            "content must clear the status bar (paddingTop=${root.paddingTop})",
            root.paddingTop >= 100,
        )
    }

    @Test
    fun backgroundedScreen_doesNotStartForegroundService() {
        File(RuntimeEnvironment.getApplication().filesDir, "pets/panda/.installed")
            .apply { parentFile.mkdirs(); createNewFile() }
        val controller = Robolectric.buildActivity(
            PetDetailActivity::class.java,
            Intent(RuntimeEnvironment.getApplication(), PetDetailActivity::class.java)
                .putExtra(PetDetailActivity.EXTRA_PET_ID, "panda"),
        ).setup()
        val activity = controller.get()

        controller.stop()
        downloadButton(activity).performClick()

        assertNull(
            "a stopped screen must not start a foreground service (crashes on Android 12+)",
            shadowOf(activity.application).nextStartedService,
        )
    }

    @Test
    fun contentIsScrollable_soTheActionButtonCannotBeClipped() {
        val button = downloadButton(launch("panda"))
        var current: android.view.View? = button
        var insideScroll = false
        while (current != null) {
            if (current is android.widget.ScrollView) {
                insideScroll = true
                break
            }
            current = current.parent as? android.view.View
        }
        assertTrue("the download button must live inside a ScrollView", insideScroll)
    }

    @Test
    fun backButton_has48dpTouchTarget() {
        val back = walk(launch("panda").window.decorView).firstOrNull { it.contentDescription == "Kembali" }
        assertTrue("back affordance missing", back != null)
        val min = (48 * back!!.resources.displayMetrics.density).toInt()
        assertTrue("back target is ${back.minimumWidth}x${back.minimumHeight}, want >= ${min}px",
            back.minimumWidth >= min && back.minimumHeight >= min)
    }

    @Test
    fun downloadingElsewhere_disablesButton() {
        ActiveDownloads.begin("panda")
        try {
            val button = downloadButton(launch("panda"))
            assertFalse(
                "a download already running for this pet must not be startable again",
                button.isEnabled,
            )
            assertEquals("Downloading…", button.text.toString())
        } finally {
            ActiveDownloads.end("panda")
        }
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
