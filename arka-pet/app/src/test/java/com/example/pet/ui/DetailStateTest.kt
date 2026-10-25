package com.example.pet.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailStateTest {

    @Test
    fun labels_switchByInstallAndDownloadState() {
        assertEquals("Download", DetailState.buttonLabel(installed = false, downloading = false))
        assertEquals("Downloading…", DetailState.buttonLabel(installed = false, downloading = true))
        assertEquals("Aktifkan", DetailState.buttonLabel(installed = true, downloading = false))
        assertEquals("Aktifkan", DetailState.buttonLabel(installed = true, downloading = true))
    }

    @Test
    fun buttonDisablesWhileDownloading() {
        assertTrue(DetailState.buttonEnabled(downloading = false))
        assertFalse(DetailState.buttonEnabled(downloading = true))
    }
}
