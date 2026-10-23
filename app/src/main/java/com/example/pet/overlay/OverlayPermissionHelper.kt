package com.example.pet.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Overlay permission (app-op, not runtime): checked on launch, granted only from system settings. */
object OverlayPermissionHelper {
    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun requestOverlayPermission(activity: Activity) {
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
        )
    }
}
