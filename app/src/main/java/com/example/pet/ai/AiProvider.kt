package com.example.pet.ai

/**
 * A provider preset with its default endpoint and model. OpenAI, Groq, DeepSeek and custom
 * endpoints share the OpenAI chat-completions wire format; Gemini and Claude differ.
 */
enum class ProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta/", "gemini-2.0-flash"),
    OPENAI("OpenAI", "https://api.openai.com/v1/", "gpt-4o-mini"),
    CLAUDE("Claude", "https://api.anthropic.com/v1/", "claude-3-5-haiku-latest"),
    GROQ("Groq", "https://api.groq.com/openai/v1/", "llama-3.3-70b-versatile"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/", "deepseek-chat"),
    CUSTOM("Custom (OpenAI-compatible)", "", ""),
    ;
}

data class ProviderConfig(
    val type: ProviderType,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
)

/** One turn of a conversation; [role] is `user`, `assistant` or `system`. */
data class AiMessage(
    val role: String,
    val content: String,
)

/**
 * Builds a config from the preset defaults for [type]; pass [baseUrl]/[model] to configure
 * [ProviderType.CUSTOM].
 */
fun providerConfig(
    type: ProviderType,
    apiKey: String,
    baseUrl: String = type.defaultBaseUrl,
    model: String = type.defaultModel,
) = ProviderConfig(type, baseUrl, model, apiKey)
