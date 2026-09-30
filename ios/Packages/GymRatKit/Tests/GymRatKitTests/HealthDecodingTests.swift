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
          {"name":"postgres","status":"down","latency_ms":2000.0,"error":"unavailable"},
          {"name":"future_dependency","status":"up","region":"eu-west-1"}
        ]}
        """.utf8)
        let ready = try APIClient.decode(Readiness.self, from: json)
        #expect(!ready.isReady)
        #expect(ready.checks.map(\.name) == ["postgres", "future_dependency"])
        #expect(ready.checks[0] == .init(name: "postgres", status: "down", latencyMs: 2000.0, error: "unavailable"))
        #expect(ready.checks[1].latencyMs == nil)
    }

    @Test func malformedPayloadThrowsDecodingError() {
        #expect(throws: APIError.self) {
            try APIClient.decode(Liveness.self, from: Data("{}".utf8))
        }
    }
}
