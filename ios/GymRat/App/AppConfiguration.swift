import Foundation

/// Build-time configuration injected via `Config/*.xcconfig` → `Info.plist`.
struct AppConfiguration: Equatable, Sendable {
    let apiBaseURL: URL

    static let current = AppConfiguration(bundle: .main)

    init(apiBaseURL: URL) {
        self.apiBaseURL = apiBaseURL
    }

    /// Reads `APIBaseURL` from the bundle's Info.plist. A missing or malformed
    /// value is a build misconfiguration, so fail fast rather than limp along.
    init(bundle: Bundle) {
        guard let raw = bundle.object(forInfoDictionaryKey: "APIBaseURL") as? String,
              let url = URL(string: raw), url.scheme != nil else {
            preconditionFailure("APIBaseURL missing or invalid in Info.plist — check Config/*.xcconfig")
        }
        self.init(apiBaseURL: url)
    }
}
