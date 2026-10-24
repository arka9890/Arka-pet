package com.example.pet.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pet.R
import com.example.pet.data.AssetManifest
import com.example.pet.data.PetRepository
import com.example.pet.model.Pet
import okhttp3.OkHttpClient

class GalleryActivity : Activity() {

    private lateinit var container: LinearLayout
    private lateinit var pets: List<Pet>
    private lateinit var repository: PetRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loaded = AssetManifest.load(assets.open("manifest.json").bufferedReader().readText())
        pets = loaded
        repository = PetRepository(OkHttpClient(), filesDir, loaded)

        val screenPad = resources.getDimensionPixelSize(R.dimen.screen_padding)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(screenPad, 0, screenPad, 0)
        }
        // Edge-to-edge (enforced at targetSdk 35) draws us behind the system bars; the
        // listener sits on the root because the header above the scroll would hide otherwise.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(screenPad, bars.top, screenPad, bars.bottom)
            insets
        }

        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, screenPad, 0, screenPad)
                addView(
                    TextView(this@GalleryActivity).apply {
                        text = getString(R.string.app_name)
                        textSize = 22f
                        setTextColor(getColor(R.color.clay_text))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    },
                )
                addView(
                    ImageButton(this@GalleryActivity).apply {
                        setImageResource(android.R.drawable.ic_menu_manage)
                        contentDescription = "Pengaturan"
                        background = null
                        setOnClickListener {
                            startActivity(Intent(this@GalleryActivity, SettingsActivity::class.java))
                        }
                    },
                )
            },
        )

        val scroll = ScrollView(this)
        container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(container)
        root.addView(scroll, LinearLayout.LayoutParams(0, 0, 1f))
        setContentView(root)

        render()
    }

    override fun onResume() {
        super.onResume()
        // Returning from the detail screen may have installed a pet (Review Focus #1).
        render()
    }

    private fun render() {
        val cardPad = resources.getDimensionPixelSize(R.dimen.card_padding)
        val cardMargin = resources.getDimensionPixelSize(R.dimen.card_margin)
        val rowMinHeight = resources.getDimensionPixelSize(R.dimen.row_min_height)

        container.removeAllViews()
        pets.forEach { pet ->
            val installed = repository.listInstalled().any { it.id == pet.id }
            val row = TextView(this).apply {
                text = "${pet.name} (${pet.type})\n${if (installed) "Terpasang" else "Belum diunduh"}"
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(getColor(R.color.clay_text))
                background = resources.getDrawable(R.drawable.clay_card_bg, null)
                setPadding(cardPad, cardPad, cardPad, cardPad)
                minimumHeight = rowMinHeight
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, cardMargin, 0, cardMargin) }
                setOnClickListener {
                    startActivity(
                        Intent(this@GalleryActivity, PetDetailActivity::class.java)
                            .putExtra(PetDetailActivity.EXTRA_PET_ID, pet.id),
                    )
                }
            }
            container.addView(row)
        }
    }
}
