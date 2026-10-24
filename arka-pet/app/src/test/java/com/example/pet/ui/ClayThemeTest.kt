package com.example.pet.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.pet.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

// API 34 + Conscrypt off: no linux-aarch64 native runtime on this CI (see PermissionActivityTest).
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
class ClayThemeTest {

    @Test
    fun theme_isNoActionBar_andUsesClayWindowBackground() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.setTheme(R.style.Theme_ArkaPet)

        val action = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.windowActionBar))
        assertFalse("theme must be NoActionBar", action.getBoolean(0, true))
        action.recycle()

        val background = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        assertEquals(ContextCompat.getColor(ctx, R.color.clay_bg), background.getColor(0, 0))
        background.recycle()
    }

    @Test
    fun clayCard_drawable_isLayeredWithNonZeroCornerRadius() {
        val ctx = RuntimeEnvironment.getApplication()
        val d = ResourcesCompat.getDrawable(ctx.resources, R.drawable.clay_card, null)

        assertTrue("clay_card must be a layer-list", d is LayerDrawable)
        val layers = d as LayerDrawable
        assertTrue("layer-list must have 3 layers", layers.numberOfLayers >= 3)
        // Layer order: 0 = dark extrusion, 1 = light rim, 2 = face.
        val face = layers.getDrawable(2)
        assertTrue("face layer must be a shape", face is GradientDrawable)
        assertTrue("face needs a clay corner radius", (face as GradientDrawable).cornerRadius > 0f)
    }

    @Test
    fun clayPalette_meets45Contrast() {
        assertTrue("clay_text on clay_card", contrast(R.color.clay_text, R.color.clay_card) >= 4.5)
        assertTrue("clay_text on clay_bg", contrast(R.color.clay_text, R.color.clay_bg) >= 4.5)
        assertTrue(
            "clay_on_accent on clay_accent",
            contrast(R.color.clay_on_accent, R.color.clay_accent) >= 4.5,
        )
    }

    private fun contrast(fgRes: Int, bgRes: Int): Double {
        val ctx = RuntimeEnvironment.getApplication()
        val l1 = luminance(ContextCompat.getColor(ctx, fgRes))
        val l2 = luminance(ContextCompat.getColor(ctx, bgRes))
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }
}
