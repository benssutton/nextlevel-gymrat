package com.nextlevel.gymrat.core

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Errors surfaced by [ApiClient]. Transport failures (no network, TLS, timeouts)
 * propagate unchanged as [IOException]. Mirrors `APIError` in GymRatKit.
 */
sealed class ApiException(message: String) : Exception(message) {
    class UnexpectedStatus(val code: Int) : ApiException("Unexpected HTTP status $code")

    class Decoding(detail: String) : ApiException("Could not decode response: $detail")
}

/**
 * Anything that can report backend health. The app depends on this interface, not on
 * [ApiClient], so previews and view-model tests can supply a simple stand-in.
 */
interface HealthChecking {
    suspend fun liveness(): Liveness

    suspend fun readiness(): Readiness
}

/** Thin, stateless REST client for the FastAPI backend in `backend/`. Mirrors GymRatKit's `APIClient`. */
class ApiClient(val baseUrl: HttpUrl, private val client: OkHttpClient = OkHttpClient()) :
    HealthChecking {
    constructor(baseUrl: String) : this(baseUrl.toHttpUrl())

    /** `GET /health/live` — the process is up. */
    override suspend fun liveness(): Liveness =
        get("health/live", Liveness.serializer(), accepting = setOf(200))

    /**
     * `GET /health/ready` — dependencies are reachable. The backend answers 503 with the
     * same body when not ready, so both codes decode to [Readiness].
     */
    override suspend fun readiness(): Readiness =
        get("health/ready", Readiness.serializer(), accepting = setOf(200, 503))

    internal suspend fun <T> get(
        path: String,
        deserializer: DeserializationStrategy<T>,
        accepting: Set<Int>
    ): T {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(path).build())
            .header("Accept", "application/json")
            // Matches the backend's correlation-ID header so client and server logs join up.
            .header("X-Request-ID", UUID.randomUUID().toString())
            .build()

        // runInterruptible: coroutine cancellation interrupts the blocking call.
        val body = runInterruptible(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (response.code !in accepting) throw ApiException.UnexpectedStatus(response.code)
                response.body.string()
            }
        }
        return decode(deserializer, body)
    }

    companion object {
        internal val json = Json { ignoreUnknownKeys = true }

        internal fun <T> decode(deserializer: DeserializationStrategy<T>, body: String): T = try {
            json.decodeFromString(deserializer, body)
        } catch (e: SerializationException) {
            throw ApiException.Decoding(e.message ?: e::class.java.simpleName)
        } catch (e: IllegalArgumentException) {
            throw ApiException.Decoding(e.message ?: e::class.java.simpleName)
        }
    }
}
