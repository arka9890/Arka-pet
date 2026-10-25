package com.example.pet.ui

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.drawable.StateListDrawable
import com.example.pet.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

// The Activity itself cannot launch here: Robolectric ships no AndroidKeyStore for the
// encrypted prefs, so the view tree is built and asserted through SettingsForm.
// API 34 + Conscrypt off: no linux-aarch64 native runtime (see PermissionActivityTest).
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
class SettingsActivityTest {

    private var backed = false
    private var saved: SettingsValues? = null
    private lateinit var form: SettingsForm

    @Before
    fun buildForm() {
        val ctx = RuntimeEnvironment.getApplication().apply { setTheme(R.style.Theme_ArkaPet) }
        form = SettingsForm(
            context = ctx,
            values = SettingsValues(
                provider = "GEMINI",
                baseUrl = "",
                model = "gemini-2.0-flash",
                apiKey = "sk-...",
                wander = true,
                autoStart = true,
            ),
            onBack = { backed = true },
            onSave = { saved = it },
        )
    }

    @Test
    fun header_showsTitleAndBackButtonInvokesCallback() {
        val titles = walk(form.root).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("title missing from $titles", titles.contains("Pengaturan"))

        val back = walk(form.root).firstOrNull { it.contentDescription == "Kembali" }
        assertTrue("back affordance missing", back != null)
        back!!.performClick()
        assertTrue("back must invoke its callback", backed)
    }

    @Test
    fun fieldsAreGroupedInsideClayCards() {
        val fields = walk(form.root).filterIsInstance<EditText>()
        assertEquals("expected all four settings fields", 4, fields.size)

        for (field in fields) {
            val inClayCard = generateSequence(field.parent) { (it as? View)?.parent }
                .filterIsInstance<View>()
                .any { it.background is StateListDrawable }
            assertTrue("field '${field.hint}' sits outside every clay card", inClayCard)
        }
    }

    @Test
    fun contentScrollsSoTheKeyboardCannotHideFields() {
        val save = walk(form.root).filterIsInstance<Button>().first { it.text.toString() == "Save" }
        var current: View? = save
        var insideScroll = false
        while (current != null) {
            if (current is ScrollView) {
                insideScroll = true
                break
            }
            current = current.parent as? View
        }
        assertTrue("the form must live inside a ScrollView", insideScroll)
    }

    @Test
    fun saveButton_reportsTheEditedValues() {
        val fields = walk(form.root).filterIsInstance<EditText>()
        // Order matches the form: provider, baseUrl, model, apiKey.
        fields[2].setText("gemini-2.5-pro")

        // Switch also extends Button, so pick the save action by its label.
        walk(form.root).filterIsInstance<Button>().first { it.text.toString() == "Save" }.performClick()

        assertEquals("gemini-2.5-pro", saved?.model)
        assertEquals("GEMINI", saved?.provider)
        assertTrue("switch state must travel with the values", saved?.wander == true)
    }

    private fun walk(view: View): List<View> {
        val children = (view as? ViewGroup)?.let { g ->
            (0 until g.childCount).flatMap { walk(g.getChildAt(it)) }
        }.orEmpty()
        return listOf(view) + children
    }
}
