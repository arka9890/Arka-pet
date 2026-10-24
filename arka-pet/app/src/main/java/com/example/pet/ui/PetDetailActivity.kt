package com.example.pet.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.pet.R
import com.example.pet.data.AssetManifest
import com.example.pet.data.PetRepository
import com.example.pet.model.Pet
import com.example.pet.overlay.PetOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * One pet: its preview image with the download button directly underneath.
 *
 * Reached from the gallery with [EXTRA_PET_ID]; unknown ids finish immediately.
 */
class PetDetailActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var repository: PetRepository
    private lateinit var pet: Pet
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var button: Button
    private var downloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pets = AssetManifest.load(assets.open("manifest.json").bufferedReader().readText())
        val id = intent.getStringExtra(EXTRA_PET_ID)
        val found = pets.firstOrNull { it.id == id }
        if (found == null) {
            finish()
            return
        }
        pet = found
        repository = PetRepository(OkHttpClient(), filesDir, pets)

        val pad = resources.getDimensionPixelSize(R.dimen.screen_padding)
        val cardPad = resources.getDimensionPixelSize(R.dimen.card_padding)
        val cardMargin = resources.getDimensionPixelSize(R.dimen.card_margin)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        // Header: back affordance + pet name.
        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(
                    ImageButton(this@PetDetailActivity).apply {
                        setImageResource(android.R.drawable.ic_menu_revert)
                        contentDescription = "Kembali"
                        background = null
                        setOnClickListener { finish() }
                    },
                )
                addView(
                    TextView(this@PetDetailActivity).apply {
                        text = pet.name
                        textSize = 20f
                        setTextColor(getColor(R.color.clay_text))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    },
                )
            },
        )

        // Preview block: image plus a status line that is never empty until the image lands.
        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(getColor(R.color.clay_card))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.preview_height),
            ).apply { topMargin = cardMargin }
        }
        root.addView(preview)

        status = TextView(this).apply {
            text = getString(R.string.preview_loading)
            textSize = 14f
            setTextColor(getColor(R.color.clay_text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = cardMargin / 2 }
        }
        root.addView(status)

        // Meta card.
        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.clay_card_bg)
                setPadding(cardPad, cardPad, cardPad, cardPad)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = cardMargin }
                listOf(
                    getString(R.string.meta_type, pet.type.name),
                    getString(R.string.meta_license, pet.license),
                    getString(R.string.meta_author, pet.author),
                ).forEach { line ->
                    addView(
                        TextView(this@PetDetailActivity).apply {
                            text = line
                            textSize = 14f
                            setTextColor(getColor(R.color.clay_text_secondary))
                        },
                    )
                }
            },
        )

        // Primary action sits directly below the preview's supporting info.
        button = Button(this).apply {
            background = resources.getDrawable(R.drawable.clay_button_bg, null)
            setTextColor(getColor(R.color.clay_on_accent))
            minHeight = resources.getDimensionPixelSize(R.dimen.button_min_height)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = cardMargin * 2 }
            setOnClickListener { onPrimaryClick() }
        }
        root.addView(button)
        refreshButton()

        setContentView(root)
        loadPreview()
    }

    override fun onResume() {
        super.onResume()
        refreshButton()
    }

    private fun refreshButton() {
        if (!::button.isInitialized || !::pet.isInitialized) return
        val installed = repository.listInstalled().any { it.id == pet.id }
        button.text = DetailState.buttonLabel(installed, downloading)
        button.isEnabled = DetailState.buttonEnabled(downloading)
    }

    /**
     * "Aktifkan" activates an installed pet without touching the network; "Download"
     * installs first. Both paths end with the overlay service running and the screen closing,
     * matching what the gallery's old inline `select()` did.
     */
    private fun onPrimaryClick() {
        if (!DetailState.buttonEnabled(downloading)) return
        if (repository.listInstalled().any { it.id == pet.id }) {
            repository.setActive(pet.id)
            PetOverlayService.start(this)
            finish()
            return
        }
        downloading = true
        refreshButton()
        scope.launch {
            val result = withContext(Dispatchers.IO) { repository.install(pet) }
            result.onSuccess {
                withContext(Dispatchers.IO) { repository.setActive(pet.id) }
                PetOverlayService.start(this@PetDetailActivity)
                finish()
            }.onFailure { e ->
                downloading = false
                status.text = e.message ?: getString(R.string.download_failed)
                refreshButton()
            }
        }
    }

    private fun loadPreview() {
        scope.launch {
            val bitmap = PreviewLoader(OkHttpClient()).load(pet.previewUrl)
            if (bitmap != null) {
                preview.setImageBitmap(bitmap)
                status.text = ""
            } else {
                status.text = getString(R.string.preview_unavailable)
            }
        }
    }

    companion object {
        const val EXTRA_PET_ID = "pet_id"
    }
}
