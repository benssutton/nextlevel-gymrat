import GymRatKit
import Observation

@MainActor
@Observable
final class HomeViewModel {
    enum State: Equatable {
        case idle
        case loading
        case loaded(Readiness)
        case failed(String)
    }

    private(set) var state: State = .idle
    private let health: any HealthChecking

    init(health: any HealthChecking) {
        self.health = health
    }

    func refresh() async {
        state = .loading
        do {
            let readiness = try await health.readiness()
            state = .loaded(readiness)
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}
