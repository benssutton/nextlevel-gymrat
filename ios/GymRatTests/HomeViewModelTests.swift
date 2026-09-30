import Foundation
@testable import GymRat
import GymRatKit
import Testing

@MainActor
struct HomeViewModelTests {
    @Test func startsIdle() {
        let model = HomeViewModel(health: FixedHealth(result: .success(.init(status: "ready", checks: []))))
        #expect(model.state == .idle)
    }

    @Test func refreshLoadsReadiness() async {
        let readiness = Readiness(status: "ready", checks: [.init(name: "postgres", status: "up")])
        let model = HomeViewModel(health: FixedHealth(result: .success(readiness)))
        await model.refresh()
        #expect(model.state == .loaded(readiness))
    }

    @Test func refreshAgainstUnreachableBackendFails() async {
        // A real APIClient pointed at a closed loopback port: exercises the true error path.
        let model = HomeViewModel(health: APIClient(baseURL: URL(string: "http://127.0.0.1:1")!))
        await model.refresh()
        guard case .failed = model.state else {
            Issue.record("expected .failed, got \(model.state)")
            return
        }
    }
}

struct AppConfigurationTests {
    @Test func appBundleProvidesAbsoluteAPIBaseURL() {
        // Unit tests are hosted in the app, so Bundle.main is the real app bundle
        // with the xcconfig-substituted Info.plist.
        #expect(AppConfiguration.current.apiBaseURL.scheme != nil)
    }
}

/// Deterministic HealthChecking that returns a canned result — used where a live
/// backend is not available (macOS CI runners have no Docker).
private struct FixedHealth: HealthChecking {
    let result: Result<Readiness, APIError>
    func liveness() async throws -> Liveness { Liveness(status: "alive", uptimeSeconds: 0) }
    func readiness() async throws -> Readiness { try result.get() }
}
