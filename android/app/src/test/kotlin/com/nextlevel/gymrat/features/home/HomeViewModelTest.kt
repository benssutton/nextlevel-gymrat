package com.nextlevel.gymrat.features.home

import com.nextlevel.gymrat.core.ApiClient
import com.nextlevel.gymrat.core.ApiException
import com.nextlevel.gymrat.core.HealthChecking
import com.nextlevel.gymrat.core.Liveness
import com.nextlevel.gymrat.core.Readiness
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors ios/GymRatTests/HomeViewModelTests.swift. */
class HomeViewModelTest {
    @Test
    fun startsIdle() {
        val model =
            HomeViewModel(
                FixedHealth(Result.success(Readiness(status = "ready", checks = emptyList())))
            )
        assertEquals(HomeViewModel.State.Idle, model.state.value)
    }

    @Test
    fun refreshLoadsReadiness() = runTest {
        val readiness =
            Readiness(
                status = "ready",
                checks = listOf(Readiness.Check(name = "postgres", status = "up"))
            )
        val model = HomeViewModel(FixedHealth(Result.success(readiness)))
        model.load()
        assertEquals(HomeViewModel.State.Loaded(readiness), model.state.value)
    }

    @Test
    fun refreshSurfacesApiErrors() = runTest {
        val model = HomeViewModel(FixedHealth(Result.failure(ApiException.UnexpectedStatus(500))))
        model.load()
        assertEquals(HomeViewModel.State.Failed("Unexpected HTTP status 500"), model.state.value)
    }

    @Test
    fun refreshAgainstUnreachableBackendFails() = runTest {
        // A real ApiClient pointed at a closed loopback port: exercises the true error path.
        val model = HomeViewModel(ApiClient("http://127.0.0.1:1"))
        model.load()
        assertTrue(
            "expected Failed, got ${model.state.value}",
            model.state.value is HomeViewModel.State.Failed
        )
    }
}

/**
 * Deterministic HealthChecking that returns a canned result — used where a live backend
 * is not available (the same rationale as FixedHealth in the iOS tests).
 */
private class FixedHealth(private val result: Result<Readiness>) : HealthChecking {
    override suspend fun liveness() = Liveness(status = "alive", uptimeSeconds = 0.0)

    override suspend fun readiness() = result.getOrThrow()
}
