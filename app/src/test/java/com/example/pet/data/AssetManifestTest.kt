package com.example.pet.data

import com.example.pet.model.PetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssetManifestTest {

    private val sample = """
        [
          {"id":"cat","name":"Orange Cat","type":"SPRITE",
           "assetUrl":"https://example.com/cat.zip","previewUrl":"https://example.com/cat.png",
           "license":"CC0","author":"LuizMelo"},
          {"id":"hiyori","name":"Hiyori","type":"LIVE2D",
           "assetUrl":"https://example.com/hiyori.zip","previewUrl":"https://example.com/hiyori.png",
           "license":"CC0","author":"Live2D Inc."}
        ]
    """.trimIndent()

    @Test
    fun load_parsesEveryField() {
        val pets = AssetManifest.load(sample)

        assertEquals(2, pets.size)
        assertEquals("cat", pets[0].id)
        assertEquals("Orange Cat", pets[0].name)
        assertEquals(PetType.SPRITE, pets[0].type)
        assertEquals("https://example.com/cat.zip", pets[0].assetUrl)
        assertEquals("https://example.com/cat.png", pets[0].previewUrl)
        assertEquals("CC0", pets[0].license)
        assertEquals("LuizMelo", pets[0].author)
        assertEquals(PetType.LIVE2D, pets[1].type)
    }

    @Test
    fun load_keepsLicenseAndAuthorOnEveryEntry() {
        assertTrue(AssetManifest.load(sample).all { it.license.isNotBlank() && it.author.isNotBlank() })
    }

    @Test
    fun load_rejectsMissingLicenseOrAuthor() {
        val noLicense = sample.replace("\"license\":\"CC0\"", "\"license\":\" \"")
        val noAuthor = sample.replace("\"author\":\"LuizMelo\"", "\"author\":\"\"")

        assertThrows(IllegalArgumentException::class.java) { AssetManifest.load(noLicense) }
        assertThrows(IllegalArgumentException::class.java) { AssetManifest.load(noAuthor) }
    }

    @Test
    fun bundledManifest_isValidCc0Set() {
        val json = File("src/main/assets/manifest.json").readText()
        val pets = AssetManifest.load(json)

        assertTrue("expected 2-3 pets, got ${pets.size}", pets.size in 2..3)
        assertTrue(pets.all { it.license.isNotBlank() && it.author.isNotBlank() })
        assertTrue(pets.filter { it.type == PetType.SPRITE }.all { it.license == "CC0" })
        assertEquals(2, pets.count { it.type == PetType.SPRITE })
        assertEquals(1, pets.count { it.type == PetType.LIVE2D })
        assertEquals(pets.size, pets.map { it.id }.distinct().size)
    }
}
