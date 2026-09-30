import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Errors surfaced by `APIClient`. Transport failures (no network, TLS, timeouts)
/// propagate unchanged as `URLError`.
public enum APIError: Error, Equatable, Sendable {
    case invalidResponse
    case unexpectedStatus(Int)
    case decoding(String)
}

/// Anything that can report backend health. The app depends on this protocol,
/// not on `APIClient`, so previews and view-model tests can supply a simple stand-in.
public protocol HealthChecking: Sendable {
    func liveness() async throws -> Liveness
    func readiness() async throws -> Readiness
}

/// Thin, stateless REST client for the FastAPI backend in `backend/`.
public struct APIClient: HealthChecking {
    public let baseURL: URL
    private let session: URLSession

    public init(baseURL: URL, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
    }

    /// `GET /health/live` — the process is up.
    public func liveness() async throws -> Liveness {
        try await get("health/live", accepting: [200])
    }

    /// `GET /health/ready` — dependencies are reachable. The backend answers 503
    /// with the same body when not ready, so both codes decode to `Readiness`.
    public func readiness() async throws -> Readiness {
        try await get("health/ready", accepting: [200, 503])
    }

    func get<T: Decodable>(_ path: String, accepting statuses: Set<Int>) async throws -> T {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        // Matches the backend's correlation-ID header so client and server logs join up.
        request.setValue(UUID().uuidString, forHTTPHeaderField: "X-Request-ID")

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
        guard statuses.contains(http.statusCode) else { throw APIError.unexpectedStatus(http.statusCode) }
        return try Self.decode(T.self, from: data)
    }

    static func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        do {
            return try decoder.decode(type, from: data)
        } catch {
            throw APIError.decoding(String(describing: error))
        }
    }
}
