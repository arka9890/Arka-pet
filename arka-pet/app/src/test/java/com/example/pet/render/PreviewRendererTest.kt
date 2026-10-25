package com.example.pet.render

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.pet.model.Pet
import com.example.pet.model.PetType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

// API 34 + Conscrypt off: no linux-aarch64 native runtime (see SpriteRendererTest).
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
class PreviewRendererTest {

    private val pet = Pet("arch-chan", "Arch-chan", PetType.LIVE2D, "https://x/m", "https://x/p.png", "CC0", "x")

    @Test
    fun drawsPreviewWithoutCrashing() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val renderer = PreviewRenderer(bitmap)

        assertTrue(renderer.isAvailable())
        renderer.load(pet)
        renderer.update(0.5f)
        renderer.draw(Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)))
        renderer.onTap()
        assertTrue(renderer.isAvailable())
    }
}
