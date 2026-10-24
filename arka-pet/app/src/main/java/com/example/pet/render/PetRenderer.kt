package com.example.pet.render

import android.graphics.Canvas
import com.example.pet.model.Pet

/** Draws the active pet onto the overlay canvas. */
interface PetRenderer {
    /** False when this renderer's native/runtime backing is missing; callers must not use it then. */
    fun isAvailable(): Boolean = true

    fun load(pet: Pet)
    fun update(dt: Float)
    fun draw(canvas: Canvas)
    fun onTap()
}
