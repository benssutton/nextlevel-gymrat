package com.nextlevel.gymrat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Decoding against payloads with the real backend's JSON shape. Mirrors GymRatKit's HealthDecodingTests. */
class HealthDecodingTest {
    @Test
    fun decodesLiveness() {
        val live = ApiClient.decode(
            Liveness.serializer(),
            """{"status":"alive","uptime_seconds":12.5}"""
        )
        assertEquals(Liveness(status = "alive", uptimeSeconds = 12.5), live)
    }

    @Test
    fun decodesReadinessIgnoringUnknownFields() {
        val body = """
            {"status":"not_ready","checks":[
              {"name":"postgres","status":"down","latency_ms":2000.0,"error":"unavailable"},
              {"name":"future_dependency","status":"up","region":"eu-west-1"}
            ]}
        """.trimIndent()
        val ready = ApiClient.decode(Readiness.serializer(), body)
        assertFalse(ready.isReady)
        assertEquals(listOf("postgres", "future_dependency"), ready.checks.map { it.name })
        assertEquals(
            Readiness.Check(
                name = "postgres",
                status = "down",
                latencyMs = 2000.0,
                error = "unavailable"
            ),
            ready.checks[0]
        )
        assertNull(ready.checks[1].latencyMs)
    }

    @Test(expected = ApiException.Decoding::class)
    fun malformedPayloadThrowsDecodingError() {
        ApiClient.decode(Liveness.serializer(), "{}")
    }
}
