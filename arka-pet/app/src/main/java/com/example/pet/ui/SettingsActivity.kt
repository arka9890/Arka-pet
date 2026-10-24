package com.example.pet.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.pet.ai.ProviderType

class SettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        prefs = EncryptedSharedPreferences.create(
            this, "pet_settings", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        val inset = (24 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left + inset, bars.top + inset, bars.right + inset, bars.bottom + inset)
            insets
        }

        root.addView(TextView(this).apply { text = "Provider (preset name)" })
        val providerInput = EditText(this).apply { hint = "GEMINI"; setText(prefs.getString("provider", "GEMINI")) }
        root.addView(providerInput)

        root.addView(TextView(this).apply { text = "Base URL (custom/override)" })
        val urlInput = EditText(this).apply { hint = "https://..."; setText(prefs.getString("baseUrl", "")) }
        root.addView(urlInput)

        root.addView(TextView(this).apply { text = "Model" })
        val modelInput = EditText(this).apply { hint = "gemini-2.0-flash"; setText(prefs.getString("model", "")) }
        root.addView(modelInput)

        root.addView(TextView(this).apply { text = "API key" })
        val keyInput = EditText(this).apply { hint = "sk-..."; setText(prefs.getString("apiKey", "")) }
        root.addView(keyInput)

        val wanderSwitch = Switch(this).apply {
            text = "Wander when idle"
            isChecked = prefs.getBoolean("wander", true)
        }
        root.addView(wanderSwitch)

        val autoStartSwitch = Switch(this).apply {
            text = "Auto-start on boot"
            isChecked = prefs.getBoolean("autoStart", true)
        }
        root.addView(autoStartSwitch)

        val save = Button(this).apply {
            text = "Save"
            setOnClickListener {
                prefs.edit()
                    .putString("provider", providerInput.text.toString().trim())
                    .putString("baseUrl", urlInput.text.toString().trim())
                    .putString("model", modelInput.text.toString().trim())
                    .putString("apiKey", keyInput.text.toString().trim())
                    .putBoolean("wander", wanderSwitch.isChecked)
                    .putBoolean("autoStart", autoStartSwitch.isChecked)
                    .apply()
                finish()
            }
        }
        root.addView(save)

        setContentView(root)
    }
}