package com.darshan.splitsmart.data.llm

import okhttp3.CertificatePinner
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Certificate pins for the Gemini host. Google serves two chains depending on the negotiated signature algorithm:
 * ECDSA (leaf → `WE2` → `GTS Root R4`) and RSA (leaf → `WR2` → `GTS Root R1`). Leaf certificates rotate every few weeks,
 * so the app pins the intermediates (primary) and the roots (backup) of both chains. OkHttp accepts the connection if
 * ANY certificate in the served chain matches ANY pin.
 *
 * Fetched on 2026-10-30 with:
 * ```
 * openssl s_client -connect generativelanguage.googleapis.com:443 -servername generativelanguage.googleapis.com -showcerts </dev/null
 * # for each certificate in the chain:
 * openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
 * ```
 * Roots cross-checked against https://pki.goog/repo/certs/gtsr1.pem and gtsr4.pem.
 */
object GeminiPins {
    const val HOST = "generativelanguage.googleapis.com"

    /** Current intermediates: GTS WE2 (ECDSA chain) and GTS WR2 (RSA chain). */
    val PRIMARY = listOf(
        "sha256/vh78KSg1Ry4NaqGDV10w/cTb9VH3BQUZoCWNa93W/EY=",
        "sha256/YPtHaftLw6/0vnc2BnNKGF54xiCA28WFcccjkA4ypCM=",
    )

    /** Roots: GTS Root R4 (ECDSA) and GTS Root R1 (RSA); valid until 2028, survive an intermediate rotation. */
    val BACKUP = listOf(
        "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
        "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=",
    )

    fun pinner(): CertificatePinner = CertificatePinner.Builder().apply {
        (PRIMARY + BACKUP).forEach { add(HOST, it) }
    }.build()
}

/** The one OkHttp client used for the LLM. [pinner] is injectable so tests can talk to MockWebServer unpinned. */
object LlmHttpClient {
    fun create(pinner: CertificatePinner?, dispatcher: Dispatcher? = null): OkHttpClient = OkHttpClient.Builder().apply {
        // The one measured real call (gemini-3.8-flash, 2026-10-30) took 147 s; these are the longest waits the review
        // screen tolerates before the on-device parser takes over ("Try AI again" stays available).
        connectTimeout(10, TimeUnit.SECONDS)
        readTimeout(60, TimeUnit.SECONDS)
        writeTimeout(15, TimeUnit.SECONDS)
        callTimeout(90, TimeUnit.SECONDS)
        followRedirects(false)
        followSslRedirects(false)
        retryOnConnectionFailure(false) // GeminiExtractor owns retries (backoff + jitter)
        if (pinner != null) certificatePinner(pinner)
        if (dispatcher != null) dispatcher(dispatcher)
    }.build()
}
