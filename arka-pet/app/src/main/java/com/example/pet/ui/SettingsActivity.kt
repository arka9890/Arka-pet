package com.example.pet.ui

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.pet.R

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

        val form = SettingsForm(
            context = this,
            values = SettingsValues(
                provider = prefs.getString("provider", "GEMINI") ?: "GEMINI",
                baseUrl = prefs.getString("baseUrl", "") ?: "",
                model = prefs.getString("model", "") ?: "",
                apiKey = prefs.getString("apiKey", "") ?: "",
                wander = prefs.getBoolean("wander", true),
                autoStart = prefs.getBoolean("autoStart", true),
            ),
            onBack = { finish() },
            onSave = { v ->
                prefs.edit()
                    .putString("provider", v.provider)
                    .putString("baseUrl", v.baseUrl)
                    .putString("model", v.model)
                    .putString("apiKey", v.apiKey)
                    .putBoolean("wander", v.wander)
                    .putBoolean("autoStart", v.autoStart)
                    .apply()
                finish()
            },
        )

        val inset = resources.getDimensionPixelSize(R.dimen.screen_padding)
        ViewCompat.setOnApplyWindowInsetsListener(form.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left + inset, bars.top + inset, bars.right + inset, bars.bottom + inset)
            insets
        }
        setContentView(form.root)
    }
}
