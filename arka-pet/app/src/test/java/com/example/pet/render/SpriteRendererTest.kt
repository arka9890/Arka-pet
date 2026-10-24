package com.example.pet.render

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.pet.model.Pet
import com.example.pet.model.PetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.File

class SpriteAnimationTest {

    private val frames = List(4) { FrameRect(it * 4, 0, 4, 4) }

    @Test
    fun frameAt_advancesOneFramePerFrameDuration() {
        val anim = SpriteAnimation(frames, fps = 10f)
        assertEquals(0, anim.frameAt(0f))
        assertEquals(0, anim.frameAt(0.099f))
        assertEquals(1, anim.frameAt(0.1f))
        assertEquals(2, anim.frameAt(0.25f))
        assertEquals(3, anim.frameAt(0.39f))
    }

    @Test
    fun frameAt_wrapsWhenLooping() {
        val anim = SpriteAnimation(frames, fps = 10f)
        assertEquals(0, anim.frameAt(0.4f))
        assertEquals(1, anim.frameAt(0.5f))
        assertEquals(0, anim.frameAt(100f))
    }

    @Test
    fun frameAt_holdsLastFrameWhenNotLooping() {
        val anim = SpriteAnimation(frames, fps = 10f, loop = false)
        assertEquals(3, anim.frameAt(0.4f))
        assertEquals(3, anim.frameAt(100f))
    }

    @Test
    fun frameAt_treatsNegativeTimeAsStart() {
        assertEquals(0, SpriteAnimation(frames, fps = 10f).frameAt(-5f))
    }

    @Test
    fun animation_rejectsEmptyFramesAndNonPositiveFps() {
        assertThrows(IllegalArgumentException::class.java) {
            SpriteAnimation(emptyList(), fps = 10f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpriteAnimation(frames, fps = 0f)
        }
    }
}

class SpriteSheetConfigTest {

    @Test
    fun parse_readsFramesAndDefaultsToLooping() {
        val config = SpriteSheetConfig.parse(
            """{"image":"sheet.png","fps":8,
                "frames":[{"x":0,"y":0,"w":32,"h":32},{"x":32,"y":0,"w":32,"h":32}]}"""
        )
        assertEquals("sheet.png", config.image)
        assertEquals(8f, config.fps, 0f)
        assertTrue(config.loop)
        assertEquals(FrameRect(32, 0, 32, 32), config.frames[1])
        assertEquals(2, config.animation().frames.size)
    }

    @Test
    fun parse_honoursExplicitNoLoop() {
        val config = SpriteSheetConfig.parse(
            """{"image":"sheet.png","fps":4,"loop":false,"frames":[{"x":0,"y":0,"w":8,"h":8}]}"""
        )
        assertFalse(config.loop)
    }

    @Test
    fun parse_rejectsImageOutsidePetDir() {
        listOf("../sheet.png", "/etc/passwd", "tiles/../../sheet.png", "  ").forEach { image ->
            assertThrows(IllegalArgumentException::class.java) {
                SpriteSheetConfig.parse(
                    """{"image":"$image","fps":8,"frames":[{"x":0,"y":0,"w":8,"h":8}]}"""
                )
            }
        }
    }

    @Test
    fun parse_rejectsBadFramesAndMissingFps() {
        assertThrows(IllegalArgumentException::class.java) {
            SpriteSheetConfig.parse("""{"image":"sheet.png","fps":8,"frames":[]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpriteSheetConfig.parse("""{"image":"sheet.png","fps":8,"frames":[{"x":0,"y":0,"w":0,"h":8}]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpriteSheetConfig.parse("""{"image":"sheet.png","frames":[{"x":0,"y":0,"w":8,"h":8}]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpriteSheetConfig.parse("not json")
        }
    }
}

@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
class SpriteRendererTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val pet = Pet("panda", "Panda", PetType.SPRITE, "https://x/p.zip", "https://x/p.png", "CC0", "x")

    @Test
    fun load_throwsWhenPetHasNoConfig() {
        val petsDir = tmp.newFolder("pets")
        assertThrows(IllegalStateException::class.java) {
            SpriteRenderer(petsDir).load(pet)
        }
    }

    @Test
    fun load_throwsWhenFrameIsOutsideTheSheet() {
        val petsDir = tmp.newFolder("pets")
        writePet(petsDir, sheet = 8, json = config(image = "sheet.png", x = 4, w = 8))
        assertThrows(IllegalStateException::class.java) {
            SpriteRenderer(petsDir).load(pet)
        }
    }

    @Test
    fun loadUpdateDraw_rendersWithoutCrashing() {
        val petsDir = tmp.newFolder("pets")
        writePet(petsDir, sheet = 8, json = config(image = "sheet.png", x = 0, w = 4))
        val renderer = SpriteRenderer(petsDir)

        renderer.draw(Canvas(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))) // no-op before load
        renderer.load(pet)
        renderer.update(0.5f)
        renderer.draw(Canvas(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)))
        renderer.onTap()
        assertTrue(renderer.isAvailable())
    }

    private fun config(image: String, x: Int, w: Int) =
        """{"image":"$image","fps":2,"frames":[{"x":$x,"y":0,"w":$w,"h":4}]}"""

    private fun writePet(petsDir: File, sheet: Int, json: String) {
        val dir = File(petsDir, pet.id).apply { mkdirs() }
        File(dir, "sheet.png").outputStream().use {
            Bitmap.createBitmap(sheet, sheet, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(dir, SpriteSheetConfig.FILE_NAME).writeText(json)
    }
}
