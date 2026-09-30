import Foundation
@testable import GymRatKit
import Testing

/// Decoding against payloads captured from the real backend's JSON shape.
struct HealthDecodingTests {
    @Test func decodesLiveness() throws {
        let json = Data(#"{"status":"alive","uptime_seconds":12.5}"#.utf8)
        let live = try APIClient.decode(Liveness.self, from: json)
        #expect(live == Liveness(status: "alive", uptimeSeconds: 12.5))
    }

    @Test func decodesReadinessIgnoringUnknownFields() throws {
        let json = Data("""
        {"status":"not_ready","checks":[
          {"name":"postgres","status":"up","latency_ms":1.2},
          {"name":"redis","status":"down","latency_ms":2000.0,"error":"unavailable"},
          {"name":"ingest","status":"up","transport":"flight","connection_state":"connected","thread_alive":true}
        ]}
        """.utf8)
        let ready = try APIClient.decode(Readiness.self, from: json)
        #expect(!ready.isReady)
        #expect(ready.checks.map(\.name) == ["postgres", "redis", "ingest"])
        #expect(ready.checks[1] == .init(name: "redis", status: "down", latencyMs: 2000.0, error: "unavailable"))
        #expect(ready.checks[2].latencyMs == nil)
    }

    @Test func malformedPayloadThrowsDecodingError() {
        #expect(throws: APIError.self) {
            try APIClient.decode(Liveness.self, from: Data("{}".utf8))
        }
    }
}
