package com.example.pet.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.example.pet.data.PetRepository
import com.example.pet.overlay.OverlayPermissionHelper
import com.example.pet.overlay.PetOverlayService
import okhttp3.OkHttpClient
import java.io.File

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OverlayPermissionHelper.hasOverlayPermission(this)) {
            startActivity(Intent(this, PermissionActivity::class.java))
            finish()
            return
        }
        val manifest = assets.open("manifest.json").bufferedReader().readText()
        val pets = com.example.pet.data.AssetManifest.load(manifest)
        val repo = PetRepository(OkHttpClient(), filesDir, pets)
        if (repo.listInstalled().isEmpty()) {
            startActivity(Intent(this, GalleryActivity::class.java))
        } else {
            repo.listInstalled().firstOrNull()?.let { repo.setActive(it.id) }
            PetOverlayService.start(this)
        }
        finish()
    }
}