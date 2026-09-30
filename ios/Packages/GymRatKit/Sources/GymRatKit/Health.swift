/// Mirrors `LivenessResponse` in `backend/schemas/health.py`.
public struct Liveness: Decodable, Equatable, Sendable {
    public let status: String
    public let uptimeSeconds: Double

    public init(status: String, uptimeSeconds: Double) {
        self.status = status
        self.uptimeSeconds = uptimeSeconds
    }
}

/// Mirrors `ReadinessResponse` in `backend/schemas/health.py`.
public struct Readiness: Decodable, Equatable, Sendable {
    public let status: String
    public let checks: [Check]

    public var isReady: Bool { status == "ready" }

    public init(status: String, checks: [Check]) {
        self.status = status
        self.checks = checks
    }

    /// Mirrors `CheckResult`; only the fields the app uses are decoded.
    public struct Check: Decodable, Equatable, Sendable, Identifiable {
        public let name: String
        public let status: String
        public let latencyMs: Double?
        public let error: String?

        public var id: String { name }
        public var isUp: Bool { status == "up" }

        public init(name: String, status: String, latencyMs: Double? = nil, error: String? = nil) {
            self.name = name
            self.status = status
            self.latencyMs = latencyMs
            self.error = error
        }
    }
}
