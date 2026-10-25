package com.example.pet.render

import android.graphics.Bitmap
import com.example.pet.model.Pet
import com.example.pet.model.PetType
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * Builds the *loaded* renderer for an installed pet, or `null` when its files or preview
 * are unusable — the caller then shows the placeholder circle.
 *
 * SPRITE pets animate from their sheet; LIVE2D pets fall back to their preview image
 * until the Cubism SDK is vendored (README).
 *
 * Blocking (disk + network): call from `Dispatchers.IO`.
 */
object PetRenderers {

    suspend fun create(
        pet: Pet,
        petsDir: File,
        loadPreview: suspend (Pet) -> Bitmap?,
    ): PetRenderer? = try {
        when (pet.type) {
            PetType.SPRITE -> SpriteRenderer(petsDir).apply { load(pet) }
            PetType.LIVE2D -> loadPreview(pet)?.let(::PreviewRenderer)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
