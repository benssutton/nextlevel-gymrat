package com.nextlevel.gymrat.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `LivenessResponse` in `backend/schemas/health.py`. */
@Serializable
data class Liveness(val status: String, @SerialName("uptime_seconds") val uptimeSeconds: Double)

/** Mirrors `ReadinessResponse` in `backend/schemas/health.py`. */
@Serializable
data class Readiness(val status: String, val checks: List<Check>) {
    val isReady: Boolean get() = status == "ready"

    /** Mirrors `CheckResult`; only the fields the app uses are decoded. */
    @Serializable
    data class Check(
        val name: String,
        val status: String,
        @SerialName("latency_ms") val latencyMs: Double? = null,
        val error: String? = null
    ) {
        val isUp: Boolean get() = status == "up"
    }
}
