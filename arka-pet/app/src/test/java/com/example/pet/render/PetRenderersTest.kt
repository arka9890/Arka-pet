package com.example.pet.render

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.pet.model.Pet
import com.example.pet.model.PetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.File

// API 34 + Conscrypt off: no linux-aarch64 native runtime (see SpriteRendererTest).
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
class PetRenderersTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val sprite = Pet("panda", "Panda", PetType.SPRITE, "https://x/p.zip", "https://x/p.png", "CC0", "x")
    private val live2d = Pet("arch-chan", "Arch-chan", PetType.LIVE2D, "https://x/m.model3.json", "https://x/preview.png", "CC0", "x")

    @Test
    fun spritePet_loadsFromItsDirectory() = runBlocking {
        val petsDir = tmp.newFolder("pets")
        writeSprite(petsDir, "panda")

        val renderer = PetRenderers.create(sprite, petsDir) {
            throw AssertionError("sprite pets must not fetch a preview")
        }

        assertTrue("want a sprite renderer, got $renderer", renderer is SpriteRenderer)
        assertTrue("loaded sprite must be drawable", renderer!!.isAvailable())
    }

    @Test
    fun spritePet_withMissingFiles_yieldsPlaceholder() = runBlocking {
        assertNull(PetRenderers.create(sprite, tmp.newFolder("pets")) { null })
    }

    @Test
    fun live2dPet_fallsBackToItsPreviewImage() = runBlocking {
        val petsDir = tmp.newFolder("pets")
        val preview = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

        val renderer = PetRenderers.create(live2d, petsDir) { preview }

        assertTrue("want the preview renderer, got $renderer", renderer is PreviewRenderer)
        assertTrue("preview bitmap must make the renderer drawable", renderer!!.isAvailable())
        renderer.draw(Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)))
        renderer.update(0.5f)
        renderer.onTap()
    }

    @Test
    fun live2dPet_whenPreviewFails_yieldsPlaceholder() = runBlocking {
        assertNull(PetRenderers.create(live2d, tmp.newFolder("pets")) { null })
    }

    @Test
    fun ensureSpriteConfig_fillsInTheMissingConfigSoThePetCanRender() {
        val petsDir = tmp.newFolder("pets")
        val petDir = File(petsDir, sprite.id).apply { mkdirs() }
        // The installed pet has its sheet (from the zip) but no sprite.json — the device state.
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(
            Bitmap.CompressFormat.PNG, 100, File(petDir, "sheet.png").outputStream(),
        )
        val bundled = """{"image":"sheet.png","fps":2,"frames":[{"x":0,"y":0,"w":4,"h":4}]}"""

        PetRenderers.ensureSpriteConfig(sprite, petsDir) { name ->
            assertEquals("sprites/${sprite.id}.json", name)
            bundled
        }

        assertEquals(bundled, File(petDir, SpriteSheetConfig.FILE_NAME).readText())
        // The point of the config: the pet becomes renderable.
        runBlocking {
            assertTrue("want a sprite renderer", PetRenderers.create(sprite, petsDir) { null } is SpriteRenderer)
        }
    }

    @Test
    fun ensureSpriteConfig_neverOverwritesAnExistingConfig() {
        val petsDir = tmp.newFolder("pets")
        val petDir = File(petsDir, sprite.id).apply { mkdirs() }
        val installed = """{"image":"sheet.png","fps":2,"frames":[{"x":0,"y":0,"w":4,"h":4}]}"""
        File(petDir, SpriteSheetConfig.FILE_NAME).writeText(installed)

        PetRenderers.ensureSpriteConfig(sprite, petsDir) { throw AssertionError("must not read") }

        assertEquals(installed, File(petDir, SpriteSheetConfig.FILE_NAME).readText())
    }

    @Test
    fun ensureSpriteConfig_swallowsMissingAssetsAndSkipsNonSpritePets() {
        val petsDir = tmp.newFolder("pets")
        File(petsDir, live2d.id).mkdirs()

        // Missing bundled config: no file, no crash (the overlay keeps its placeholder).
        PetRenderers.ensureSpriteConfig(sprite, petsDir) { null }
        PetRenderers.ensureSpriteConfig(sprite, petsDir) { throw java.io.IOException("no asset") }
        PetRenderers.ensureSpriteConfig(live2d, petsDir) { "should not be requested" }

        assertFalse(File(File(petsDir, sprite.id), SpriteSheetConfig.FILE_NAME).exists())
        assertFalse(File(File(petsDir, live2d.id), SpriteSheetConfig.FILE_NAME).exists())
    }

    private fun writeSprite(petsDir: File, id: String) {
        val dir = File(petsDir, id).apply { mkdirs() }
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(
            Bitmap.CompressFormat.PNG, 100, File(dir, "sheet.png").outputStream(),
        )
        File(dir, SpriteSheetConfig.FILE_NAME)
            .writeText("""{"image":"sheet.png","fps":2,"frames":[{"x":0,"y":0,"w":4,"h":4}]}""")
    }
}
