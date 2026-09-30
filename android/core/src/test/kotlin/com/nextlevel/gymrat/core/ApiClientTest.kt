package com.nextlevel.gymrat.core

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Transport behaviour against real sockets — no interceptors or fake servers. Mirrors GymRatKit's APIClientTests. */
class ApiClientTest {
    @Test(expected = IOException::class)
    fun unreachableHostSurfacesIOException() = runTest {
        // Port 1 on loopback is never listening, so the connection is refused.
        ApiClient("http://127.0.0.1:1").liveness()
    }
}

/**
 * Contract tests against a live backend. CI starts the backend with Docker Compose and sets
 * GYMRAT_API_BASE_URL (see .github/workflows/_contract.yml); locally:
 *   GYMRAT_API_BASE_URL=http://localhost:8000 ./gradlew :core:test
 * Mirrors GymRatKit's BackendContractTests, so both clients are held to the same contract.
 */
class BackendContractTest {
    private val baseUrl = System.getenv("GYMRAT_API_BASE_URL").orEmpty()
    private val client by lazy { ApiClient(baseUrl) }

    private fun requireBackend() = assumeTrue("GYMRAT_API_BASE_URL not set", baseUrl.isNotBlank())

    @Test
    fun livenessReportsAlive() = runTest {
        requireBackend()
        val live = client.liveness()
        assertEquals("alive", live.status)
        assertTrue(live.uptimeSeconds >= 0)
    }

    @Test
    fun readinessListsEveryDependency() = runTest {
        requireBackend()
        val ready = client.readiness()
        assertTrue(ready.isReady)
        assertTrue(ready.checks.map { it.name }.contains("postgres"))
        assertTrue(ready.checks.all { it.isUp })
    }

    @Test
    fun unknownRouteIsUnexpectedStatus() = runTest {
        requireBackend()
        val error = runCatching {
            client.get("health/does-not-exist", Liveness.serializer(), accepting = setOf(200))
        }
            .exceptionOrNull()
        assertTrue(
            "expected UnexpectedStatus(404), got $error",
            error is ApiException.UnexpectedStatus && error.code == 404
        )
    }
}
