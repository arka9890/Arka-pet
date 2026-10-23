package com.example.pet.ui

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.example.pet.R
import com.example.pet.overlay.OverlayPermissionHelper

/**
 * Gate for the overlay. Never assumes the permission is held (Review Focus #1):
 * without it, it sends the user to the system overlay settings and finishes once granted.
 */
class PermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (OverlayPermissionHelper.hasOverlayPermission(this)) {
            finish()
            return
        }
        setContentView(TextView(this).apply {
            setPadding(64, 64, 64, 64)
            text = getString(R.string.overlay_permission_explanation)
        })
        if (savedInstanceState == null) {
            OverlayPermissionHelper.requestOverlayPermission(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (OverlayPermissionHelper.hasOverlayPermission(this)) finish()
    }
}