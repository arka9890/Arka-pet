package com.example.pet.data

import com.example.pet.model.Pet
import com.example.pet.model.PetType
import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PetRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var client: OkHttpClient
    private lateinit var repository: PetRepository
    private lateinit var root: File

    private val sprite = pet("panda", PetType.SPRITE, "https://example.com/panda.zip")
    private val live2d = pet("arch-chan", PetType.LIVE2D, "https://example.com/arch/model.model3.json")

    @Before
    fun setUp() {
        root = tmp.newFolder("files")
        client = mockk()
        repository = PetRepository(client, root, listOf(sprite, live2d))
    }

    // Review Focus #3: an interrupted download must never leave a selectable pet behind.
    @Test
    fun install_interruptedDownload_leavesPetNotInstalled() {
        mockClient(sprite.assetUrl to truncatingBody(zipBytes("a.txt" to "hello world")))

        val result = repository.install(sprite)

        assertTrue(result.isFailure)
        assertTrue(repository.listInstalled().isEmpty())
        assertFalse(File(petsDir(), sprite.id).exists())
        assertEquals(emptyList<File>(), petsDir().listFiles().orEmpty().toList())
    }

    @Test
    fun install_zip_extractsIntoFinalDir() {
        mockClient(sprite.assetUrl to zipBytes("panda/run.png" to "png", "panda/idle.png" to "png").toResponseBody())

        val result = repository.install(sprite)

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        assertEquals(listOf(sprite), repository.listInstalled())
        assertEquals("png", File(petsDir(), "panda/panda/run.png").readText())
        assertTrue(File(petsDir(), "panda/.installed").isFile)
    }

    @Test
    fun install_replacesExistingPetAndRemovesOldCopy() {
        mockClient(sprite.assetUrl to zipBytes("panda/run.png" to "v1").toResponseBody())
        assertTrue(repository.install(sprite).isSuccess)

        mockClient(sprite.assetUrl to zipBytes("panda/run.png" to "v2").toResponseBody())
        assertTrue(repository.install(sprite).isSuccess)

        assertEquals(listOf(sprite), repository.listInstalled())
        assertEquals("v2", File(petsDir(), "panda/panda/run.png").readText())
        assertTrue(File(petsDir(), "panda/.installed").isFile)
        // No leftover temp dirs for this pet.
        assertTrue(petsDir().listFiles().orEmpty().none { it.name.startsWith(".tmp-") })
    }

    @Test
    fun install_live2d_downloadsSiblingsIntoFinalDir() {
        val base = "https://example.com/arch/"
        val model = """
            {"Version":3,"FileReferences":{
              "Moc":"arch.moc3",
              "Textures":["textures/texture_00.png"],
              "Physics":"arch.physics3.json",
              "Motions":{"Idle":[{"File":"motions/idle.motion3.json"}]},
              "Expressions":[{"Name":"smile","File":"expressions/smile.exp3.json"}]
            }}
        """.trimIndent()
        mockClient(
            "${base}model.model3.json" to model.toResponseBody(),
            "${base}arch.moc3" to "moc".toResponseBody(),
            "${base}textures/texture_00.png" to "png".toResponseBody(),
            "${base}arch.physics3.json" to "{}".toResponseBody(),
            "${base}motions/idle.motion3.json" to "{}".toResponseBody(),
            "${base}expressions/smile.exp3.json" to "{}".toResponseBody(),
        )

        val result = repository.install(live2d)

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        val dir = File(petsDir(), live2d.id)
        assertEquals("moc", File(dir, "arch.moc3").readText())
        assertEquals("png", File(dir, "textures/texture_00.png").readText())
        assertTrue(File(dir, "arch.physics3.json").isFile)
        assertTrue(File(dir, "motions/idle.motion3.json").isFile)
        assertTrue(File(dir, "expressions/smile.exp3.json").isFile)
        assertTrue(File(dir, ".installed").isFile)
        assertEquals(listOf(live2d), repository.listInstalled())
    }

    @Test
    fun install_missingSibling_leavesPetNotInstalled() {
        mockClient("https://example.com/arch/model.model3.json" to
            """{"FileReferences":{"Moc":"arch.moc3"}}""".toResponseBody())

        val result = repository.install(live2d)

        assertTrue(result.isFailure)
        assertTrue(repository.listInstalled().isEmpty())
        assertEquals(emptyList<File>(), petsDir().listFiles().orEmpty().toList())
    }

    @Test
    fun install_rejectsZipEntryOutsidePetDir() {
        mockClient(sprite.assetUrl to zipBytes("../evil.txt" to "pwn").toResponseBody())

        val result = repository.install(sprite)

        assertTrue(result.isFailure)
        assertTrue(repository.listInstalled().isEmpty())
        assertFalse(File(root, "evil.txt").exists())
    }

    @Test
    fun listInstalled_ignoresDirectoriesWithoutMarker() {
        val broken = File(petsDir(), sprite.id).apply { mkdirs() }
        File(broken, "run.png").writeText("png")

        assertTrue(repository.listInstalled().isEmpty())
    }

    @Test
    fun setActive_persistsInstalledPetAndRejectsUnknown() {
        assertNull(repository.activePetId())
        mockClient(sprite.assetUrl to zipBytes("run.png" to "png").toResponseBody())
        assertTrue(repository.install(sprite).isSuccess)

        repository.setActive(sprite.id)

        assertEquals(sprite.id, repository.activePetId())
        assertThrows(IllegalArgumentException::class.java) { repository.setActive("not-installed") }
    }

    private fun petsDir() = File(root, "pets")

    private fun pet(id: String, type: PetType, url: String) = Pet(
        id = id,
        name = id,
        type = type,
        assetUrl = url,
        previewUrl = "https://example.com/preview.png",
        license = "CC0",
        author = "test",
    )

    private fun mockClient(vararg routes: Pair<String, ResponseBody>) {
        val byUrl = routes.toMap()
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            val call = mockk<Call>()
            every { call.execute() } answers {
                val body = byUrl[request.url.toString()]
                    ?: throw IOException("unexpected request: ${request.url}")
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            call
        }
    }

    /** Fails after four bytes, simulating a connection dropped mid-download. */
    private fun truncatingBody(bytes: ByteArray) = object : ResponseBody() {
        override fun contentType() = null
        override fun contentLength() = bytes.size.toLong()
        override fun source(): BufferedSource = object : okio.Source {
            private val buffer = Buffer().write(bytes)
            private var served = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                if (served >= 4) throw IOException("connection reset mid-download")
                val read = buffer.read(sink, minOf(byteCount, 4 - served))
                served += read
                return read
            }

            override fun timeout() = Timeout.NONE
            override fun close() = Unit
        }.buffer()
    }

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
