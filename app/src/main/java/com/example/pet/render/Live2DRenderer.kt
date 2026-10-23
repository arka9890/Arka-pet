package com.example.pet.render

import android.graphics.Canvas
import com.example.pet.model.Pet

/**
 * Placeholder for the Cubism Native SDK renderer. The SDK needs registration and
 * NDK/CMake, neither available here, so every call fails loudly instead of drawing
 * nothing. Callers must check [isAvailable] first (see scripts/fetch_cubism.sh).
 */
class Live2DRenderer : PetRenderer {
    override fun isAvailable(): Boolean = false

    override fun load(pet: Pet) = unavailable()

    override fun update(dt: Float) = unavailable()

    override fun draw(canvas: Canvas) = unavailable()

    override fun onTap() = unavailable()

    private fun unavailable(): Nothing = throw IllegalStateException(
        "Live2D unavailable: Cubism Native SDK is not built. Run scripts/fetch_cubism.sh, " +
            "then wire NDK/CMake (TODO in app/build.gradle.kts)."
    )
}
