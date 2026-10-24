package com.example.pet.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Sends a chat turn to the configured provider and returns the reply text.
 *
 * The name is historical: OpenAI, Groq, DeepSeek and custom endpoints speak the OpenAI
 * chat-completions format, while Gemini and Claude use their own wire formats internally.
 *
 * Blocking network work runs on [Dispatchers.IO]. The API key is only ever placed in request
 * headers; it is never logged or included in error messages (Review Focus #4).
 */
class OpenAiCompatibleClient(
    private val config: ProviderConfig,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(150, java.util.concurrent.TimeUnit.SECONDS)
        .build(),
) {
    suspend fun chat(messages: List<AiMessage>): Result<String> = withContext(Dispatchers.IO) {
        configError()?.let { return@withContext Result.failure(it) }
        try {
            httpClient.newCall(buildRequest(messages)).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Result.success(parseReply(body))
                } else {
                    Result.failure(IOException(httpError(response.code, body)))
                }
            }
        } catch (e: IOException) {
            Result.failure(
                IOException(
                    "Could not reach ${config.type.displayName}. Check your connection and try again.",
                    e,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException("Unexpected response from ${config.type.displayName}. Try again.", e),
            )
        }
    }

    private fun configError(): IllegalArgumentException? {
        val name = config.type.displayName
        return when {
            config.apiKey.isBlank() ->
                IllegalArgumentException("Add your $name API key in settings.")
            config.baseUrl.isBlank() || config.baseUrl.trimEnd('/').toHttpUrlOrNull() == null ->
                IllegalArgumentException("Add a valid $name endpoint URL in settings.")
            config.model.isBlank() ->
                IllegalArgumentException("Choose a model for $name in settings.")
            else -> null
        }
    }

    private fun buildRequest(messages: List<AiMessage>): Request {
        val base = config.baseUrl.trimEnd('/')
        val (path, body) = when (config.type) {
            ProviderType.GEMINI -> "/models/${config.model}:generateContent" to geminiBody(messages)
            ProviderType.CLAUDE -> "/messages" to claudeBody(messages)
            else -> "/chat/completions" to openAiBody(messages)
        }
        val url = (base + path).toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Add a valid ${config.type.displayName} endpoint URL in settings.")

        return Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .apply {
                when (config.type) {
                    ProviderType.GEMINI -> header("x-goog-api-key", config.apiKey)
                    ProviderType.CLAUDE -> {
                        header("x-api-key", config.apiKey)
                        header("anthropic-version", ANTHROPIC_VERSION)
                    }
                    else -> header("Authorization", "Bearer ${config.apiKey}")
                }
            }
            .build()
    }

    private fun openAiBody(messages: List<AiMessage>): String = JsonObject().apply {
        addProperty("model", config.model)
        add("messages", JsonArray().apply {
            messages.forEach { message ->
                add(JsonObject().apply {
                    addProperty("role", message.role)
                    addProperty("content", message.content)
                })
            }
        })
    }.toString()

    private fun geminiBody(messages: List<AiMessage>): String = JsonObject().apply {
        add("contents", JsonArray().apply {
            messages.filterNot { it.role == "system" }.forEach { message ->
                add(JsonObject().apply {
                    addProperty("role", if (message.role == "assistant") "model" else "user")
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", message.content) })
                    })
                })
            }
        })
        systemText(messages)?.let { text ->
            add("systemInstruction", JsonObject().apply {
                add("parts", JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", text) })
                })
            })
        }
    }.toString()

    private fun claudeBody(messages: List<AiMessage>): String = JsonObject().apply {
        addProperty("model", config.model)
        addProperty("max_tokens", MAX_TOKENS)
        systemText(messages)?.let { addProperty("system", it) }
        add("messages", JsonArray().apply {
            messages.filterNot { it.role == "system" }.forEach { message ->
                add(JsonObject().apply {
                    addProperty("role", message.role)
                    addProperty("content", message.content)
                })
            }
        })
    }.toString()

    private fun systemText(messages: List<AiMessage>): String? =
        messages.filter { it.role == "system" }
            .joinToString("\n") { it.content }
            .ifBlank { null }

    private fun parseReply(body: String): String {
        val json = JsonParser.parseString(body).asJsonObject
        val text = when (config.type) {
            ProviderType.GEMINI -> json.getAsJsonArray("candidates")?.firstOrNull()
                ?.asJsonObject?.getAsJsonObject("content")?.getAsJsonArray("parts")
                ?.joinToString("") { it.asJsonObject.get("text")?.asString.orEmpty() }
            ProviderType.CLAUDE -> json.getAsJsonArray("content")
                ?.joinToString("") { part ->
                    part.asJsonObject.takeIf { it.get("type")?.asString == "text" }
                        ?.get("text")?.asString.orEmpty()
                }
            else -> json.getAsJsonArray("choices")?.firstOrNull()
                ?.asJsonObject?.getAsJsonObject("message")?.get("content")
                ?.takeIf { it.isJsonPrimitive }?.asString
        }
        return text?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("empty reply")
    }

private fun httpError(code: Int, body: String): String {
        val name = config.type.displayName
        // Gemini reports an invalid API key as HTTP 400 with API_KEY_INVALID in the body.
        if (config.type == ProviderType.GEMINI && code == 400 &&
            (body.contains("API_KEY_INVALID", ignoreCase = true) ||
                body.contains("API key not valid", ignoreCase = true))
        ) {
            return "$name rejected the API key. Check it in settings."
        }
        return name + when (code) {
            401, 403 -> " rejected the API key. Check it in settings."
            429 -> " is rate limiting requests. Try again in a moment."
            in 400..499 -> " returned HTTP $code. Check your model and endpoint in settings."
            in 500..599 -> " is having trouble right now. Try again later."
            else -> " returned HTTP $code. Try again later."
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MAX_TOKENS = 1024
    }
}
