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

    /**
     * Copies the bundled `sprites/<id>.json` into an installed SPRITE pet's directory when
     * it is missing: upstream packs (Kenney) ship raw PNGs with no pet config, so without
     * this the sprite renderer always fails and the placeholder shows. Never throws — a
     * failed heal just leaves the placeholder in place.
     */
    fun ensureSpriteConfig(pet: Pet, petsDir: File, readAsset: (String) -> String?) {
        if (pet.type != PetType.SPRITE) return
        val petDir = File(petsDir, pet.id)
        val configFile = File(petDir, SpriteSheetConfig.FILE_NAME)
        if (configFile.isFile || !petDir.isDirectory) return
        val json = try {
            readAsset("sprites/${pet.id}.json")
        } catch (e: Exception) {
            null
        } ?: return
        configFile.writeText(json)
    }

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
