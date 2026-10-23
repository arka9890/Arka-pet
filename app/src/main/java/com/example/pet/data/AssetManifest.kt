package com.example.pet.data

import com.example.pet.model.Pet
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Parses the bundled pet catalog (`assets/manifest.json`). */
object AssetManifest {
    private val gson = Gson()
    private val listType = object : TypeToken<List<Pet>>() {}.type

    fun load(json: String): List<Pet> {
        val pets: List<Pet>? = gson.fromJson(json, listType)
        requireNotNull(pets) { "manifest must be a JSON array" }

        pets.forEach { pet ->
            require(!pet.id.isNullOrBlank()) { "manifest entry has a blank id" }
            // Gson leaves absent fields null even though the Kotlin type is non-null.
            require(!pet.license.isNullOrBlank()) { "pet '${pet.id}' has no license" }
            require(!pet.author.isNullOrBlank()) { "pet '${pet.id}' has no author" }
        }
        return pets
    }
}
