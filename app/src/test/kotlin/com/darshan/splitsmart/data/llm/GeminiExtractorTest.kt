package com.darshan.splitsmart.data.llm

import com.darshan.splitsmart.core.BackoffPolicy
import com.darshan.splitsmart.testutil.SAMPLE_LLM_JSON
import com.darshan.splitsmart.testutil.SAMPLE_OCR_TEXT
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Every Gemini network path against MockWebServer: success, 4xx, 401, 429 (+Retry-After), 5xx, timeout, malformed. */
class GeminiExtractorTest {
    private lateinit var server: MockWebServer
    private val sleeps = mutableListOf<Long>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS).readTimeout(500, TimeUnit.MILLISECONDS).callTimeout(2, TimeUnit.SECONDS).build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun extractor(
        apiKey: String = "test-key",
        policy: BackoffPolicy = BackoffPolicy(10, 100, 2.0, maxAttempts = 3),
        models: List<String> = GeminiExtractor.MODELS,
        cache: ModelCache = InMemoryModelCache(),
        httpClient: OkHttpClient = client,
    ) = GeminiExtractor(
        client = httpClient, apiKey = apiKey, baseUrl = server.url("/"), models = models, modelCache = cache, backoff = policy,
        dispatchers = TestDispatcherProvider(Dispatchers.Unconfined), random = Random(1), sleep = { sleeps += it },
    )

    private fun candidate(text: String) = """{"candidates":[{"content":{"parts":[{"text":${Json.encodeToString(kotlinx.serialization.serializer<String>(), text)}}],"role":"model"},"finishReason":"STOP"}]}"""

    @Test
    fun `success sends a strict json request with the key in a header and returns the model text`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(candidate(SAMPLE_LLM_JSON)).build())
        val text = extractor().extract(SAMPLE_OCR_TEXT)
        assertThat(text).isEqualTo(SAMPLE_LLM_JSON)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/v1beta/models/${GeminiExtractor.DEFAULT_MODEL}:generateContent")
        assertThat(request.url.query).isNull() // never the key in the URL
        assertThat(request.headers[GeminiExtractor.API_KEY_HEADER]).isEqualTo("test-key")
        assertThat(request.headers["Content-Type"]).startsWith("application/json")
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        val config = body["generationConfig"]!!.jsonObject
        assertThat(config["responseMimeType"]!!.jsonPrimitive.content).isEqualTo("application/json")
        assertThat(config["responseSchema"]!!.jsonObject["properties"]!!.jsonObject.keys).containsExactly("items", "taxPaise", "tipPaise", "totalPaise")
        assertThat(config["temperature"]!!.jsonPrimitive.content).isEqualTo("0")
        val prompt = body["contents"]!!.jsonArrayFirstText()
        assertThat(prompt).contains("Paneer Tikka 250.00")
        assertThat(prompt).contains("paise")
    }

    @Test
    fun `request disables thinking as far as the model allows and bounds the output`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(candidate("{}")).build())
        extractor().extract(SAMPLE_OCR_TEXT)
        val config = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject["generationConfig"]!!.jsonObject
        assertThat(config["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content).isEqualTo("minimal")
        assertThat(config["maxOutputTokens"]!!.jsonPrimitive.content).isEqualTo("8192")
        assertThat(GeminiExtractor.DEFAULT_MODEL).isEqualTo("gemini-3.1-flash-lite")
    }

    @Test
    fun `404 model not found falls back to the next model once and caches the one that answered`() = runTest {
        val cache = InMemoryModelCache()
        server.enqueue(MockResponse.Builder().code(404).body("""{"error":{"message":"models/a is not found"}}""").build())
        server.enqueue(MockResponse.Builder().code(200).body(candidate(SAMPLE_LLM_JSON)).build())
        assertThat(extractor(models = listOf("a", "b", "c"), cache = cache).extract("x")).isEqualTo(SAMPLE_LLM_JSON)
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/v1beta/models/a:generateContent")
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/v1beta/models/b:generateContent")
        assertThat(cache.get()).isEqualTo("b")
        assertThat(sleeps).isEmpty() // a 404 is not a transient failure: no backoff

        // next extraction goes straight to the cached model
        server.enqueue(MockResponse.Builder().code(200).body(candidate("{}")).build())
        extractor(models = listOf("a", "b", "c"), cache = cache).extract("x")
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/v1beta/models/b:generateContent")
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `cached model that disappears falls back to the list in order`() = runTest {
        val cache = InMemoryModelCache("b")
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().code(200).body(candidate("{}")).build())
        extractor(models = listOf("a", "b"), cache = cache).extract("x")
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/v1beta/models/b:generateContent")
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/v1beta/models/a:generateContent")
        assertThat(cache.get()).isEqualTo("a")
    }

    @Test
    fun `a cached id that is no longer in the list is ignored`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(candidate("{}")).build())
        extractor(models = listOf("a"), cache = InMemoryModelCache("retired-model")).extract("x")
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/v1beta/models/a:generateContent")
    }

    @Test
    fun `every model 404 ends in ClientError after trying each exactly once`() = runTest {
        repeat(3) { server.enqueue(MockResponse.Builder().code(404).build()) }
        val cache = InMemoryModelCache()
        val ex = runCatching { extractor(models = listOf("a", "b", "c"), cache = cache).extract("x") }.exceptionOrNull() as LlmException.ClientError
        assertThat(ex.code).isEqualTo(404)
        assertThat(server.requestCount).isEqualTo(3)
        assertThat(cache.get()).isNull()
    }

    @Test
    fun `other client errors do not switch models`() = runTest {
        server.enqueue(MockResponse.Builder().code(400).build())
        assertThat(runCatching { extractor(models = listOf("a", "b")).extract("x") }.exceptionOrNull()).isInstanceOf(LlmException.ClientError::class.java)
        server.enqueue(MockResponse.Builder().code(401).build())
        assertThat(runCatching { extractor(models = listOf("a", "b")).extract("x") }.exceptionOrNull()).isInstanceOf(LlmException.Unauthorized::class.java)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `unknown host is Offline and not retried`() = runTest {
        val offline = client.newBuilder().dns { throw java.net.UnknownHostException("no network") }.build()
        val ex = runCatching { extractor(httpClient = offline).extract("x") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(LlmException.Offline::class.java)
        assertThat((ex as LlmException).kind).isEqualTo(com.darshan.splitsmart.domain.model.LlmFailure.OFFLINE)
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun `missing key fails fast without a network call`() = runTest {
        val ex = runCatching { extractor(apiKey = "").extract("x") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(LlmException.KeyMissing::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `4xx client errors are not retried`() = runTest {
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":{"message":"bad"}}""").build())
        val ex = runCatching { extractor().extract("x") }.exceptionOrNull() as LlmException.ClientError
        assertThat(ex.code).isEqualTo(400)
        assertThat(server.requestCount).isEqualTo(1)
        server.enqueue(MockResponse.Builder().code(404).build())
        // with a single model a 404 is terminal (with several, the next model is tried: see the fallback tests)
        assertThat(runCatching { extractor(models = listOf("only-model")).extract("x") }.exceptionOrNull()).isInstanceOf(LlmException.ClientError::class.java)
    }

    @Test
    fun `401 and 403 are Unauthorized`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).build())
        assertThat(runCatching { extractor().extract("x") }.exceptionOrNull()).isInstanceOf(LlmException.Unauthorized::class.java)
        server.enqueue(MockResponse.Builder().code(403).build())
        assertThat(runCatching { extractor().extract("x") }.exceptionOrNull()).isInstanceOf(LlmException.Unauthorized::class.java)
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun `429 honours Retry-After then succeeds`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "2").build())
        server.enqueue(MockResponse.Builder().code(200).body(candidate("{}")).build())
        assertThat(extractor(policy = BackoffPolicy(10, 5_000, 2.0, 3)).extract("x")).isEqualTo("{}")
        assertThat(sleeps).containsExactly(2_000L)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `429 storm gives up after maxAttempts with RateLimited`() = runTest {
        repeat(3) { server.enqueue(MockResponse.Builder().code(429).build()) }
        val ex = runCatching { extractor().extract("x") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(LlmException.RateLimited::class.java)
        assertThat(server.requestCount).isEqualTo(3)
        assertThat(sleeps).hasSize(2)
        sleeps.forEach { assertThat(it).isAtMost(100L) }
    }

    @Test
    fun `5xx is retried with backoff and recovers`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().code(200).body(candidate("{}")).build())
        assertThat(extractor().extract("x")).isEqualTo("{}")
        assertThat(server.requestCount).isEqualTo(3)
        assertThat(sleeps).hasSize(2)
    }

    @Test
    fun `5xx storm ends in ServerError`() = runTest {
        repeat(3) { server.enqueue(MockResponse.Builder().code(502).build()) }
        val ex = runCatching { extractor().extract("x") }.exceptionOrNull() as LlmException.ServerError
        assertThat(ex.code).isEqualTo(502)
    }

    @Test
    fun `read timeout becomes Timeout after retries`() = runTest {
        repeat(2) { server.enqueue(MockResponse.Builder().code(200).body(candidate("{}")).bodyDelay(3, TimeUnit.SECONDS).build()) }
        val ex = runCatching { extractor(policy = BackoffPolicy(1, 1, 1.0, 2)).extract("x") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(LlmException.Timeout::class.java)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `connection dropped is a Network failure`() = runTest {
        repeat(3) { server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()) }
        assertThat(runCatching { extractor().extract("x") }.exceptionOrNull()).isInstanceOf(LlmException.Network::class.java)
    }

    @Test
    fun `malformed or empty responses are Malformed`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("<html>oops</html>").build())
        assertThat(runCatching { extractor().extract("x") }.exceptionOrNull()).isInstanceOf(LlmException.Malformed::class.java)
        server.enqueue(MockResponse.Builder().code(200).body("""{"candidates":[]}""").build())
        assertThat(runCatching { extractor().extract("x") }.exceptionOrNull()).isInstanceOf(LlmException.Malformed::class.java)
        server.enqueue(MockResponse.Builder().code(200).body("""{"promptFeedback":{"blockReason":"SAFETY"}}""").build())
        assertThat(runCatching { extractor().extract("x") }.exceptionOrNull()).isInstanceOf(LlmException.Malformed::class.java)
        assertThat(server.requestCount).isEqualTo(3) // malformed bodies are not retried
    }

    @Test
    fun `oversized ocr text is truncated before it is sent`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(candidate("{}")).build())
        extractor().extract("x".repeat(100_000))
        assertThat(server.takeRequest().body!!.size).isLessThan(60_000)
    }
}

private fun JsonElement.jsonArrayFirstText(): String =
    jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
