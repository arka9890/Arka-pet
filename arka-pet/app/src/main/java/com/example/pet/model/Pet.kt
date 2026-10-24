package com.example.pet.model

/** A downloadable pet skin. Task 5 owns the manifest parsing that fills this in. */
data class Pet(
    val id: String,
    val name: String,
    val type: PetType,
    val assetUrl: String,
    val previewUrl: String,
    val license: String,
    val author: String,
)

enum class PetType { SPRITE, LIVE2D }
