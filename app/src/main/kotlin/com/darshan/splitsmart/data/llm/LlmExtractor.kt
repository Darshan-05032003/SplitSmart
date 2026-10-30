package com.darshan.splitsmart.data.llm

import com.darshan.splitsmart.domain.model.LlmFailure

/** Sends normalised OCR text to a language model and returns the raw model text (expected to be JSON). */
interface LlmExtractor {
    suspend fun extract(ocrText: String): String
}

/** Typed failures of the LLM path; every one maps to an [LlmFailure] the UI can explain. */
sealed class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    abstract val kind: LlmFailure
    open val retryable: Boolean get() = false

    class KeyMissing : LlmException("LLM API key not configured") { override val kind get() = LlmFailure.KEY_MISSING }
    class Network(cause: Throwable) : LlmException("network failure", cause) { override val kind get() = LlmFailure.NETWORK; override val retryable get() = true }
    class Timeout(cause: Throwable) : LlmException("timeout", cause) { override val kind get() = LlmFailure.TIMEOUT; override val retryable get() = true }
    class Unauthorized(val code: Int, detail: String? = null) : LlmException("unauthorized ($code)${detail.suffix()}") { override val kind get() = LlmFailure.UNAUTHORIZED }
    class RateLimited(val retryAfterMs: Long?) : LlmException("rate limited") { override val kind get() = LlmFailure.RATE_LIMITED; override val retryable get() = true }
    class ClientError(val code: Int, detail: String? = null) : LlmException("client error ($code)${detail.suffix()}") { override val kind get() = LlmFailure.CLIENT_ERROR }
    class ServerError(val code: Int, detail: String? = null) : LlmException("server error ($code)${detail.suffix()}") { override val kind get() = LlmFailure.SERVER_ERROR; override val retryable get() = true }
    class Malformed(reason: String) : LlmException("malformed response: $reason") { override val kind get() = LlmFailure.MALFORMED }
    /** DNS lookup failed: the device is offline (or has no DNS). Not retried: the on-device draft stays and "Try AI again" is offered. */
    class Offline(cause: Throwable) : LlmException("offline", cause) { override val kind get() = LlmFailure.OFFLINE }
    /** TLS handshake or certificate-pin check failed. Never retried; nothing was sent (OkHttp checks pins before writing the request). */
    class Insecure(cause: Throwable) : LlmException("insecure connection: ${cause.javaClass.simpleName}", cause) { override val kind get() = LlmFailure.INSECURE }

    private companion object {
        /** Server error messages are kept short and free of control characters; they never contain the key. */
        fun String?.suffix(): String = this?.takeIf { it.isNotBlank() }?.let { ": " + it.replace(Regex("[\\p{Cntrl}]"), " ").take(200) } ?: ""
    }
}
