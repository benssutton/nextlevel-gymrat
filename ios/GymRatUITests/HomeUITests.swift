import XCTest

final class HomeUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testHomeScreenShowsBackendStatus() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.navigationBars["GymRat"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["refreshButton"].exists)
        // Resolves to Ready/Not ready with a backend running, or an error without one.
        XCTAssertTrue(app.descendants(matching: .any)["backendStatus"].waitForExistence(timeout: 15))
    }

    @MainActor
    func testLaunchPerformance() throws {
        measure(metrics: [XCTApplicationLaunchMetric()]) {
            XCUIApplication().launch()
        }
    }
}
