package com.example.pet.ui

import android.app.Activity
import android.os.Bundle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

class GalleryActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var container: LinearLayout
    private lateinit var pets: List<Pet>
    private lateinit var repository: PetRepository
    private var downloading = false

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loaded = AssetManifest.load(assets.open("manifest.json").bufferedReader().readText())
        pets = loaded
        repository = PetRepository(OkHttpClient(), filesDir, loaded)

        val scroll = ScrollView(this)
        container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(container)
        // Edge-to-edge (enforced at targetSdk 35) draws us behind the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top + 8.dp(), bars.right, bars.bottom + 8.dp())
            insets
        }
        setContentView(scroll)

        render(pets)
    }

    private fun render(pets: List<Pet>) {
        container.removeAllViews()
        pets.forEach { pet ->
            val installed = repository.listInstalled().any { it.id == pet.id }
            val row = TextView(this).apply {
                text = "${pet.name} (${pet.type}) - ${if (installed) "installed" else "not installed"}"
                textSize = 16f
                setPadding(16.dp(), 18.dp(), 16.dp(), 18.dp())
                setOnClickListener { if (!downloading) select(pet) }
            }
            container.addView(row)
        }
    }

    private fun select(pet: Pet) {
        if (!repository.listInstalled().any { it.id == pet.id }) {
            downloading = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { repository.install(pet) }
                downloading = false
                if (result.isSuccess) {
                    repository.setActive(pet.id)
                    PetOverlayService.start(this@GalleryActivity)
                    render(pets)
                } else {
                    TextView(this@GalleryActivity).apply { text = "Download failed: ${result.exceptionOrNull()?.message}" }
                }
            }
        } else {
            repository.setActive(pet.id)
            PetOverlayService.start(this)
            finish()
        }
    }
}