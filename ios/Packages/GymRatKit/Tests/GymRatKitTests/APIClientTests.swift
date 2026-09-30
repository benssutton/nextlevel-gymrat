import Foundation
@testable import GymRatKit
import Testing

/// Transport behaviour against real sockets — no URLProtocol stubs.
struct APIClientTests {
    @Test func unreachableHostSurfacesURLError() async {
        // Port 1 on loopback is never listening, so the connection is refused.
        let client = APIClient(baseURL: URL(string: "http://127.0.0.1:1")!)
        await #expect(throws: URLError.self) {
            _ = try await client.liveness()
        }
    }
}

private let liveBackendURL = ProcessInfo.processInfo.environment["GYMRAT_API_BASE_URL"].flatMap(URL.init(string:))

/// Contract tests against a live backend. CI starts the backend with Docker
/// Compose and sets GYMRAT_API_BASE_URL; locally:
///   GYMRAT_API_BASE_URL=http://localhost:8000 swift test
@Suite(.enabled(if: liveBackendURL != nil, "GYMRAT_API_BASE_URL not set"))
struct BackendContractTests {
    let client = APIClient(baseURL: liveBackendURL ?? URL(string: "http://localhost")!)

    @Test func livenessReportsAlive() async throws {
        let live = try await client.liveness()
        #expect(live.status == "alive")
        #expect(live.uptimeSeconds >= 0)
    }

    @Test func readinessListsEveryDependency() async throws {
        let ready = try await client.readiness()
        #expect(ready.isReady)
        #expect(Set(ready.checks.map(\.name)).isSuperset(of: ["postgres", "clickhouse", "redis", "ingest"]))
        #expect(ready.checks.allSatisfy { $0.isUp })
    }

    @Test func unknownRouteIsUnexpectedStatus() async throws {
        await #expect(throws: APIError.unexpectedStatus(404)) {
            let _: Liveness = try await client.get("health/does-not-exist", accepting: [200])
        }
    }
}
