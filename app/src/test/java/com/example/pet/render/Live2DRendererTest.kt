package com.example.pet.render

import com.example.pet.model.Pet
import com.example.pet.model.PetType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class Live2DRendererTest {
    @Test
    fun unavailable_untilSdkIsBuilt() {
        val renderer = Live2DRenderer()
        assertFalse(renderer.isAvailable())
        assertThrows(IllegalStateException::class.java) {
            renderer.load(
                Pet("cat", "Cat", PetType.LIVE2D, "https://x/cat.zip", "https://x/cat.png", "CC0", "x")
            )
        }
    }
}
