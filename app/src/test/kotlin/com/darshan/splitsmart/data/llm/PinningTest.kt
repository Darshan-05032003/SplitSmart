package com.darshan.splitsmart.data.llm

import com.google.common.truth.Truth.assertThat
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.CertificatePinner
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.net.ssl.SSLPeerUnverifiedException

/** Proves the pinning mechanism: a TLS server whose public key does not match the pin is rejected. */
class PinningTest {
    private lateinit var server: MockWebServer
    private lateinit var serverCert: HeldCertificate
    private lateinit var clientCerts: HandshakeCertificates

    @Before
    fun setUp() {
        serverCert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").commonName("localhost").build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(serverCert).build()
        clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(serverCert.certificate).build()
        server = MockWebServer()
        server.useHttps(serverCerts.sslSocketFactory())
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun get(pinner: CertificatePinner?): String {
        val client = LlmHttpClient.create(pinner).newBuilder()
            .sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager).build()
        return client.newCall(Request.Builder().url(server.url("/ping")).build()).execute().use { it.body.string() }
    }

    @Test
    fun `wrong pin is rejected even though the certificate is trusted`() {
        val wrong = CertificatePinner.Builder().add("localhost", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=").build()
        val ex = runCatching { get(wrong) }.exceptionOrNull()
        assertThat(ex).isInstanceOf(SSLPeerUnverifiedException::class.java)
        assertThat(ex!!.message).contains("Certificate pinning failure")
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `pin mismatch through the extractor is Insecure, never retried and sends nothing`() = kotlinx.coroutines.test.runTest {
        val wrong = CertificatePinner.Builder().add("localhost", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=").build()
        val client = LlmHttpClient.create(wrong).newBuilder().sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager).build()
        val sleeps = mutableListOf<Long>()
        val extractor = GeminiExtractor(
            client = client, apiKey = "k", baseUrl = server.url("/"), backoff = com.darshan.splitsmart.core.BackoffPolicy.LLM,
            dispatchers = com.darshan.splitsmart.testutil.TestDispatcherProvider(kotlinx.coroutines.Dispatchers.Unconfined), sleep = { sleeps += it },
        )
        val ex = runCatching { extractor.extract("Dosa 120.00") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(LlmException.Insecure::class.java)
        assertThat(ex!!.cause).isInstanceOf(SSLPeerUnverifiedException::class.java)
        assertThat((ex as LlmException).kind).isEqualTo(com.darshan.splitsmart.domain.model.LlmFailure.INSECURE)
        assertThat(sleeps).isEmpty()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `matching pin or no pinner is accepted`() {
        val right = CertificatePinner.Builder().add("localhost", CertificatePinner.pin(serverCert.certificate)).build()
        assertThat(get(right)).isEqualTo("ok")
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())
        assertThat(get(null)).isEqualTo("ok")
    }

    @Test
    fun `production pin set covers both google chains with primary and backup pins`() {
        val pins = GeminiPins.pinner().findMatchingPins(GeminiPins.HOST)
        assertThat(pins).hasSize(4)
        pins.forEach { assertThat(it.toString()).startsWith("sha256/") }
        assertThat(GeminiPins.PRIMARY).hasSize(2)
        assertThat(GeminiPins.BACKUP).hasSize(2)
        assertThat(GeminiPins.pinner().findMatchingPins("evil.example.com")).isEmpty()
        assertThat(GeminiPins.pinner().findMatchingPins("notgenerativelanguage.googleapis.com")).isEmpty()
    }

    @Test
    fun `production client has sane timeouts and pins only the gemini host`() {
        val client = LlmHttpClient.create(GeminiPins.pinner())
        assertThat(client.connectTimeoutMillis).isEqualTo(10_000)
        assertThat(client.readTimeoutMillis).isEqualTo(30_000)
        assertThat(client.callTimeoutMillis).isEqualTo(45_000)
        assertThat(client.certificatePinner.findMatchingPins(GeminiPins.HOST)).hasSize(4)
        assertThat(client.followRedirects).isFalse()
    }
}
