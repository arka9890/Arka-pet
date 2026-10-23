package com.example.pet.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.pet.overlay.PetOverlayService
import java.io.File

object BootDecisions {
    fun shouldAutoStart(autoStartEnabled: Boolean, installedCount: Int): Boolean =
        autoStartEnabled && installedCount > 0
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = readAutoStart(context)
        val count = installedPetCount(context)
        if (BootDecisions.shouldAutoStart(enabled, count)) {
            PetOverlayService.start(context)
        }
    }

    private fun readAutoStart(context: Context): Boolean = try {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            context, "pet_settings", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        prefs.getBoolean("autoStart", true)
    } catch (e: Exception) {
        true
    }

    private fun installedPetCount(context: Context): Int =
        File(context.filesDir, "pets").listFiles()
            ?.count { it.isDirectory && File(it, ".installed").isFile } ?: 0
}