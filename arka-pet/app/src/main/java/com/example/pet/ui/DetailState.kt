package com.example.pet.ui

/**
 * Pure decision for the detail screen's primary button. Kept free of Android types so
 * the double-tap guard (Review Focus #3) is testable without launching a screen.
 */
object DetailState {

    /** An installed pet never downloads, so `installed` takes precedence over `downloading`. */
    fun buttonLabel(installed: Boolean, downloading: Boolean): String = when {
        installed -> "Aktifkan"
        downloading -> "Downloading…"
        else -> "Download"
    }

    fun buttonEnabled(downloading: Boolean): Boolean = !downloading
}
