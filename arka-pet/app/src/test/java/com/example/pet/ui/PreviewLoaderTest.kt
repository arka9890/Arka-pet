package com.example.pet.ui

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

// Robolectric is required: the test builds a real PNG via android.graphics.
// LEGACY graphics is the only mode available on linux-aarch64, and its shadowed
// BitmapFactory does not validate bytes — PreviewLoader checks image signatures
// itself so this test still pins the "undecodable body -> null" contract.
// API 34 + Conscrypt off: no native runtime (see PermissionActivityTest).
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
class PreviewLoaderTest {

    private val client = mockk<OkHttpClient>()
    private lateinit var loader: PreviewLoader

    /** Read lazily by the stub, so each test sets it before calling `load`. */
    private var body: ByteArray = ByteArray(0)

    @Before
    fun setUp() {
        loader = PreviewLoader(client)
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            val call = mockk<Call>()
            every { call.execute() } returns Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody())
                .build()
            call
        }
    }

    @Test
    fun unrecognizedBytes_returnNull() = runBlocking {
        body = ByteArray(64) { 0x7f } // plausible-looking but not an image
        assertNull(loader.load("https://example.test/preview.png"))
    }

    @Test
    fun emptyBody_returnNull() = runBlocking {
        body = ByteArray(0)
        assertNull(loader.load("https://example.test/preview.png"))
    }

    @Test
    fun validPngBytes_decodeToBitmap() = runBlocking {
        body = pngBytes()
        assertNotNull(loader.load("https://example.test/preview.png"))
    }

    @Test
    fun malformedUrl_returnNullWithoutThrowing() = runBlocking {
        body = pngBytes()
        assertNull(loader.load("not-a-url"))
    }

    private fun pngBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            .compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}
