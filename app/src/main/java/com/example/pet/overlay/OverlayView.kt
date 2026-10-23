package com.example.pet.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.example.pet.render.PetRenderer

/**
 * The floating pet. Draws the active renderer and turns touches into either a tap
 * (renderer.onTap) or a drag (onDrag with the pixel delta since the last event).
 */
class OverlayView(
    context: Context,
    private val renderer: PetRenderer,
) : View(context) {

    /** Called with the drag delta; the owner clamps and moves the window. */
    var onDrag: ((dx: Int, dy: Int) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY }

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (renderer.isAvailable()) {
            renderer.draw(canvas)
        } else {
            // No renderer yet (Task 7): keep the window visible so drag/tap can be used.
            canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 3f, placeholderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging &&
                    (kotlin.math.abs(event.rawX - downRawX) > touchSlop ||
                        kotlin.math.abs(event.rawY - downRawY) > touchSlop)
                ) {
                    dragging = true
                }
                if (dragging) {
                    val dx = (event.rawX - lastRawX).toInt()
                    val dy = (event.rawY - lastRawY).toInt()
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    if (dx != 0 || dy != 0) onDrag?.invoke(dx, dy)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    performClick()
                    if (renderer.isAvailable()) renderer.onTap()
                }
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        /**
         * Keeps a [viewW] x [viewH] window fully inside a [screenW] x [screenH] screen.
         * A view larger than the screen pins to the top-left instead of going negative.
         */
        fun clampPosition(x: Int, y: Int, screenW: Int, screenH: Int, viewW: Int, viewH: Int): Point =
            Point(
                x.coerceIn(0, maxOf(0, screenW - viewW)),
                y.coerceIn(0, maxOf(0, screenH - viewH)),
            )
    }
}
