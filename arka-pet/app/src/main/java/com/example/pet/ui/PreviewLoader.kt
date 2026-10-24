package com.example.pet.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches a pet's preview image.
 *
 * Never throws and never yields an unusable bitmap: an unparsable URL, an HTTP error,
 * an empty body or undecodable bytes all come back as `null`, so the detail screen can
 * show an error line instead of a blank image box.
 */
class PreviewLoader(private val client: OkHttpClient) {

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = try {
                Request.Builder().url(url).build()
            } catch (e: IllegalArgumentException) {
                return@withContext null
            }
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                if (!hasImageSignature(bytes)) return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?.takeIf { it.width > 0 && it.height > 0 }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Bytes off the network are a trust boundary: reject anything that is not a known
     * image container before it reaches the decoder. Also keeps the contract testable
     * under Robolectric, whose shadowed BitmapFactory accepts arbitrary bytes.
     */
    private fun hasImageSignature(b: ByteArray): Boolean {
        if (b.size < MIN_BYTES) return false
        val sig = { i: Int, s: String -> (0 until s.length).all { b[i + it] == s[it].code.toByte() } }
        val png = b[0] == 0x89.toByte() && sig(1, "PNG")
        val jpeg = b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()
        val gif = sig(0, "GIF8")
        val webp = sig(0, "RIFF") && sig(8, "WEBP")
        return png || jpeg || gif || webp
    }

    private companion object {
        // Shortest plausible container (RIFF header) — real images are far longer.
        const val MIN_BYTES = 12
    }
}
