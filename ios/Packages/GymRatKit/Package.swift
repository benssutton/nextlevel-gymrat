// swift-tools-version: 6.0
// GymRatKit holds everything that does not need UIKit/SwiftUI: API client,
// models and domain logic. Keeping it a plain Swift package means it builds and
// tests on Linux CI runners (cheap) as well as inside the Xcode app (macOS).
import PackageDescription

let package = Package(
    name: "GymRatKit",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [
        .library(name: "GymRatKit", targets: ["GymRatKit"]),
    ],
    targets: [
        .target(name: "GymRatKit"),
        .testTarget(name: "GymRatKitTests", dependencies: ["GymRatKit"]),
    ]
)
