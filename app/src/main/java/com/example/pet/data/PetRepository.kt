package com.example.pet.data

import com.example.pet.model.Pet
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Downloads pets into app-private storage (`<filesDir>/pets/<id>/`) and reports what is installed.
 *
 * A pet only becomes selectable once its complete contents and `.installed` marker are renamed
 * into place, so an interrupted download can never leave a half-installed pet (Review Focus #3).
 */
class PetRepository(
    private val client: OkHttpClient,
    private val filesDir: File,
    private val catalog: List<Pet> = emptyList(),
) {
    private val petsDir = File(filesDir, "pets")
    private val activeFile = File(filesDir, "active_pet")

    fun listInstalled(): List<Pet> = catalog.filter { isInstalled(it.id) }

    fun activePetId(): String? =
        activeFile.takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }

    fun setActive(petId: String) {
        require(isInstalled(petId)) { "pet '$petId' is not installed" }
        activeFile.writeText(petId)
    }

    /** Blocking (network + disk); call off the main thread. */
    fun install(pet: Pet): Result<Unit> {
        val buildDir = File(petsDir, ".tmp-${UUID.randomUUID()}")
        val zipFile = File(petsDir, "${buildDir.name}.zip")
        return try {
            if (!petsDir.isDirectory) check(petsDir.mkdirs() || petsDir.isDirectory) { "cannot create $petsDir" }
            check(buildDir.mkdirs()) { "cannot create $buildDir" }

            val url = pet.assetUrl.toHttpUrlOrNull()
                ?: throw IOException("bad asset url: ${pet.assetUrl}")
            if (url.pathSegments.last().endsWith(".model3.json")) {
                installLive2D(url, buildDir)
            } else {
                installZip(url, buildDir, zipFile)
            }

            check(buildDir.walkTopDown().any { it.isFile }) { "asset for '${pet.id}' is empty" }
            check(File(buildDir, MARKER).createNewFile()) { "cannot mark '${pet.id}' installed" }
            publish(pet.id, buildDir)
            Result.success(Unit)
        } catch (e: Exception) {
            buildDir.deleteRecursively()
            Result.failure(e)
        } finally {
            zipFile.delete()
        }
    }

    private fun installZip(url: HttpUrl, buildDir: File, zipFile: File) {
        download(url, zipFile)
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = safeChild(buildDir, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun installLive2D(url: HttpUrl, buildDir: File) {
        val modelName = url.pathSegments.last()
        if (modelName.isEmpty()) throw IOException("asset url has no file name: $url")
        val modelFile = safeChild(buildDir, modelName)
        download(url, modelFile)

        val references = live2dReferences(JsonParser.parseString(modelFile.readText()).asJsonObject)
        for (reference in references.distinct()) {
            val sibling = url.resolve(reference)
                ?: throw IOException("bad asset reference: $reference")
            download(sibling, safeChild(buildDir, reference))
        }
    }

    /** File paths referenced by a Cubism `.model3.json` (moc, textures, physics, motions, expressions). */
    private fun live2dReferences(model: JsonObject): List<String> {
        val refs = model.getAsJsonObject("FileReferences")
            ?: throw IOException("model3.json has no FileReferences")
        val files = mutableListOf<String>()

        fun add(member: String) = refs.get(member)?.asString?.let(files::add)
        add("Moc")
        add("Physics")
        add("Pose")
        refs.getAsJsonArray("Textures")?.forEach { files += it.asString }
        refs.getAsJsonArray("Expressions")?.forEach {
            it.asJsonObject.get("File")?.asString?.let(files::add)
        }
        refs.getAsJsonObject("Motions")?.entrySet()?.forEach { (_, motions) ->
            motions.asJsonArray.forEach { it.asJsonObject.get("File")?.asString?.let(files::add) }
        }
        return files
    }

    private fun download(url: HttpUrl, target: File) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body ?: throw IOException("no body for $url")
            target.parentFile?.mkdirs()
            body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (target.length() == 0L) throw IOException("empty download for $url")
    }

    /** Renames the finished build dir into place, keeping the old copy until the swap succeeds. */
    private fun publish(petId: String, buildDir: File) {
        val finalDir = petDir(petId)
        val oldDir = File("${buildDir.path}.old")
        oldDir.deleteRecursively()
        if (finalDir.exists() && !finalDir.renameTo(oldDir)) {
            throw IOException("cannot replace '${petId}'")
        }
        if (!buildDir.renameTo(finalDir)) {
            oldDir.renameTo(finalDir)
            throw IOException("cannot publish '${petId}'")
        }
        oldDir.deleteRecursively()
    }

    private fun isInstalled(petId: String) = File(petDir(petId), MARKER).isFile

    private fun petDir(petId: String) = safeChild(petsDir, petId)

    private fun safeChild(dir: File, relative: String): File {
        val child = File(dir, relative.replace('\\', '/'))
        require(child.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
            "unsafe path '$relative'"
        }
        return child
    }

    companion object {
        private const val MARKER = ".installed"
    }
}
