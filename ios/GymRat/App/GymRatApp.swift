import GymRatKit
import SwiftUI

@main
struct GymRatApp: App {
    @State private var home = HomeViewModel(health: APIClient(baseURL: AppConfiguration.current.apiBaseURL))

    var body: some Scene {
        WindowGroup {
            HomeView(model: home)
        }
    }
}
