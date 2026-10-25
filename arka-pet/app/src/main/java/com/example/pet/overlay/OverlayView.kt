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
    /**
     * The renderer to draw; the service swaps in a loaded one once the pet's files are
     * ready, so it starts as null and shows the placeholder until then.
     */
    var renderer: PetRenderer?,
) : View(context) {

    /** Called with the drag delta; the owner clamps and moves the window. */
    var onDrag: ((dx: Int, dy: Int) -> Unit)? = null

    /** Optional chat panel hosted by this overlay; toggled on tap. */
    var chatPanel: com.example.pet.ui.ChatPanel? = null

    // ... (existing)

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY }

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var accX = 0f
    private var accY = 0f
    private var dragging = false
    private var lastFrameNanos = 0L

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val active = renderer
        if (active != null && active.isAvailable()) {
            active.update(elapsedSinceLastFrame())
            active.draw(canvas)
            // Keep the animation running while the window is visible.
            // shortcut: redraws every frame even for still renderers (battery cost);
            // upgrade to a per-renderer `needsFrames` flag if that ever matters.
            postInvalidateOnAnimation()
        } else {
            // Renderer not loaded yet: keep the window visible so drag/tap still work.
            canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 3f, placeholderPaint)
        }
    }

    private fun elapsedSinceLastFrame(): Float {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        return dt.coerceAtLeast(0f)
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
                    accX += event.rawX - lastRawX
                    accY += event.rawY - lastRawY
                    val dx = accX.toInt()
                    val dy = accY.toInt()
                    accX -= dx
                    accY -= dy
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    if (dx != 0 || dy != 0) onDrag?.invoke(dx, dy)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    performClick()
                    val panel = chatPanel
                    if (panel != null) {
                        if (panel.visibility == View.VISIBLE) panel.hide() else panel.show()
                    } else if (renderer?.isAvailable() == true) {
                        renderer?.onTap()
                    }
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
