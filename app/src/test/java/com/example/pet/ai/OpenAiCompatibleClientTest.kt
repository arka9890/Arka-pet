package com.example.pet.ai

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(
        type: ProviderType = ProviderType.OPENAI,
        apiKey: String = "sk-test",
        model: String = "test-model",
    ) = OpenAiCompatibleClient(
        providerConfig(type, apiKey, baseUrl = server.url("/v1/").toString(), model = model),
    )

    @Test
    fun chat_openAi_sendsRequestAndParsesReply() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"role":"assistant","content":"hello!"}}]}""",
            ),
        )

        val result = client().chat(listOf(AiMessage("user", "hi")))

        assertEquals("hello!", result.getOrThrow())
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("test-model", body["model"].asString)
        val message = body["messages"].asJsonArray[0].asJsonObject
        assertEquals("user", message["role"].asString)
        assertEquals("hi", message["content"].asString)
    }

    @Test
    fun chat_gemini_usesGoogleWireFormat() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"content":{"parts":[{"text":"hi from gemini"}]}}]}""",
            ),
        )
        val config = providerConfig(
            ProviderType.GEMINI,
            "google-key",
            baseUrl = server.url("/v1beta/").toString(),
            model = "gemini-test",
        )

        val result = OpenAiCompatibleClient(config).chat(
            listOf(AiMessage("user", "hi"), AiMessage("assistant", "hey"), AiMessage("user", "bye")),
        )

        assertEquals("hi from gemini", result.getOrThrow())
        val request = server.takeRequest()
        assertEquals("/v1beta/models/gemini-test:generateContent", request.path)
        assertEquals("google-key", request.getHeader("x-goog-api-key"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        val contents = body["contents"].asJsonArray
        assertEquals("user", contents[0].asJsonObject["role"].asString)
        assertEquals("hi", contents[0].asJsonObject["parts"].asJsonArray[0].asJsonObject["text"].asString)
        assertEquals("model", contents[1].asJsonObject["role"].asString)
    }

    @Test
    fun chat_claude_usesAnthropicWireFormat() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"content":[{"type":"text","text":"hi from claude"}]}""",
            ),
        )
        val config = providerConfig(
            ProviderType.CLAUDE,
            "claude-key",
            baseUrl = server.url("/v1/").toString(),
            model = "claude-test",
        )

        val result = OpenAiCompatibleClient(config).chat(listOf(AiMessage("user", "hi")))

        assertEquals("hi from claude", result.getOrThrow())
        val request = server.takeRequest()
        assertEquals("/v1/messages", request.path)
        assertEquals("claude-key", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("claude-test", body["model"].asString)
        assertTrue(body["max_tokens"].asInt > 0)
        assertEquals("hi", body["messages"].asJsonArray[0].asJsonObject["content"].asString)
    }

    // Review Focus #4: a missing key must be a friendly failure, never a crash or a network call.
    @Test
    fun chat_emptyKey_failsFriendlyWithoutNetwork() = runBlocking {
        val result = client(apiKey = "   ").chat(listOf(AiMessage("user", "hi")))

        val error = result.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("API key"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun chat_invalidKey_failsFriendly() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad key"}"""))

        val result = client(apiKey = "sk-wrong").chat(listOf(AiMessage("user", "hi")))

        val error = result.exceptionOrNull()!!
        assertTrue(error.message!!.contains("API key"))
        assertFalse(error.message!!.contains("sk-wrong"))
    }

    @Test
    fun chat_networkFailure_failsFriendly() = runBlocking {
        server.shutdown()

        val result = client().chat(listOf(AiMessage("user", "hi")))

        assertTrue(result.exceptionOrNull()!!.message!!.contains("connection"))
    }

    @Test
    fun presets_haveDefaultsForEveryProvider() {
        val presets = ProviderType.entries.map { providerConfig(it, "key") }

        assertEquals(6, presets.size)
        assertEquals(ProviderType.CUSTOM, presets.last().type)
        presets.dropLast(1).forEach {
            assertTrue("${it.type} needs a base URL", it.baseUrl.startsWith("https://"))
            assertTrue("${it.type} needs a model", it.model.isNotBlank())
        }
        assertEquals("", presets.last().baseUrl)
        assertEquals("", presets.last().model)
    }
}
