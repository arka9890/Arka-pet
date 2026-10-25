package com.example.pet.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pet.R
import com.example.pet.data.AssetManifest
import com.example.pet.data.PetRepository
import com.example.pet.model.Pet
import com.example.pet.overlay.PetOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    /** False while backgrounded: `startForegroundService` would crash on Android 12+. */
    private var inForeground = false

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
            // Horizontal padding comes from the ScrollView below, which also owns the insets.
            setPadding(0, pad, 0, pad)
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
                        val ripple = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                        background = ripple.getDrawable(0)
                        ripple.recycle()
                        minimumWidth = resources.getDimensionPixelSize(R.dimen.touch_target)
                        minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target)
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

        // Content is ~540dp tall — without a scroll the action button clips on short screens.
        val scroll = ScrollView(this).apply { addView(root) }
        // Edge-to-edge (enforced at targetSdk 35) draws us behind the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(pad, bars.top, pad, bars.bottom)
            insets
        }
        setContentView(scroll)
        loadPreview()
    }

    override fun onStart() {
        super.onStart()
        inForeground = true
    }

    override fun onStop() {
        super.onStop()
        inForeground = false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshButton()
    }

    private fun refreshButton() {
        if (!::button.isInitialized || !::pet.isInitialized) return
        val installed = repository.listInstalled().any { it.id == pet.id }
        val downloading = ActiveDownloads.contains(pet.id)
        button.text = DetailState.buttonLabel(installed, downloading)
        button.isEnabled = DetailState.buttonEnabled(downloading)
    }

    /**
     * The service must only start while this screen is visible — `startForegroundService`
     * from the background throws `ForegroundServiceStartNotAllowedException` on Android 12+.
     * [MainActivity] starts it on next launch, so skipping here never leaves the pet absent.
     */
    private fun startOverlay() {
        if (inForeground) PetOverlayService.start(this)
    }

    /**
     * "Aktifkan" activates an installed pet without touching the network; "Download"
     * installs first. Both paths end with the overlay service running and the screen closing,
     * matching what the gallery's old inline `select()` did.
     */
    private fun onPrimaryClick() {
        if (!DetailState.buttonEnabled(ActiveDownloads.contains(pet.id))) return
        if (repository.listInstalled().any { it.id == pet.id }) {
            repository.setActive(pet.id)
            startOverlay()
            finish()
            return
        }
        ActiveDownloads.begin(pet.id)
        refreshButton()
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repository.install(pet) }
                result.onSuccess {
                    withContext(Dispatchers.IO) { repository.setActive(pet.id) }
                    startOverlay()
                    finish()
                }.onFailure { e ->
                    ActiveDownloads.end(pet.id)
                    status.text = e.message ?: getString(R.string.download_failed)
                    refreshButton()
                }
            } finally {
                // Covers cancellation too (screen destroyed mid-download).
                ActiveDownloads.end(pet.id)
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
