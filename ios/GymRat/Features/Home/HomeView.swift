import GymRatKit
import SwiftUI

struct HomeView: View {
    let model: HomeViewModel

    var body: some View {
        NavigationStack {
            List {
                Section("Backend") {
                    content
                }
            }
            .navigationTitle("GymRat")
            .toolbar {
                Button("Refresh", systemImage: "arrow.clockwise") {
                    Task { await model.refresh() }
                }
                .accessibilityIdentifier("refreshButton")
            }
            .refreshable { await model.refresh() }
            .task { await model.refresh() }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch model.state {
        case .idle, .loading:
            ProgressView()
                .accessibilityIdentifier("loadingIndicator")
        case .loaded(let readiness):
            Label(readiness.isReady ? "Ready" : "Not ready",
                  systemImage: readiness.isReady ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                .foregroundStyle(readiness.isReady ? Color.green : Color.orange)
                .accessibilityIdentifier("backendStatus")
            ForEach(readiness.checks) { check in
                LabeledContent(check.name, value: check.status)
            }
        case .failed(let message):
            Label(message, systemImage: "wifi.exclamationmark")
                .foregroundStyle(.red)
                .accessibilityIdentifier("backendStatus")
        }
    }
}

#Preview("Ready") {
    HomeView(model: HomeViewModel(health: PreviewHealth()))
}

/// Fixed, in-memory health source for SwiftUI previews.
private struct PreviewHealth: HealthChecking {
    func liveness() async throws -> Liveness { Liveness(status: "alive", uptimeSeconds: 1) }
    func readiness() async throws -> Readiness {
        Readiness(status: "ready", checks: [.init(name: "postgres", status: "up"), .init(name: "redis", status: "up")])
    }
}
