package com.example.pet.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.pet.model.Pet

/**
 * Draws a pet's preview image on the overlay: the stand-in for [Live2DRenderer] until
 * the Cubism SDK is vendored (see README). A LIVE2D model cannot be posed without it,
 * but showing the character beats the placeholder circle.
 */
class PreviewRenderer(private val bitmap: Bitmap) : PetRenderer {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun load(pet: Pet) = Unit // the image arrives with the constructor, not from disk
    override fun update(dt: Float) = Unit // a still image has no frames to advance
    override fun onTap() = Unit

    override fun draw(canvas: Canvas) {
        if (canvas.width <= 0 || canvas.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return
        val scale = minOf(
            canvas.width.toFloat() / bitmap.width,
            canvas.height.toFloat() / bitmap.height,
        )
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        // Fit-center, matching how the detail screen shows the same preview.
        canvas.drawBitmap(bitmap, (canvas.width - w) / 2f, (canvas.height - h) / 2f, paint)
    }
}
