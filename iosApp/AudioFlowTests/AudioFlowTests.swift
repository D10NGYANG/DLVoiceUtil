import XCTest

final class AudioFlowTests: XCTestCase {
    func testRecordPlaySaveAndRestart() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        defer { app.terminate() }
        let record = app.buttons["开始录音"]
        XCTAssertTrue(record.waitForExistence(timeout: 10))
        record.tap()
        XCTAssertTrue(app.buttons["停止录音"].waitForExistence(timeout: 5))
        let elapsed = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "录音时长:")).firstMatch
        let hasSamples = NSPredicate { _, _ in
            let seconds = Int(elapsed.label.split(separator: ":").last ?? "0") ?? 0
            return seconds >= 2
        }
        expectation(for: hasSamples, evaluatedWith: elapsed)
        waitForExpectations(timeout: 10)
        app.buttons["停止录音"].tap()
        XCTAssertTrue(record.waitForExistence(timeout: 5))
        app.buttons["保存文件到缓存目录"].tap()
        XCTAssertTrue(app.staticTexts["已保存 audio.pcm"].waitForExistence(timeout: 5))
        app.buttons["播放录音"].tap()
        XCTAssertTrue(app.buttons["停止播放"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["播放录音"].waitForExistence(timeout: 10), app.debugDescription)
        for _ in 0..<3 {
            record.tap()
            XCTAssertTrue(app.buttons["停止录音"].waitForExistence(timeout: 5))
            app.buttons["停止录音"].tap()
            XCTAssertTrue(record.waitForExistence(timeout: 5))
        }
        record.tap()
        XCTAssertTrue(app.buttons["停止录音"].waitForExistence(timeout: 5))
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(record.waitForExistence(timeout: 5))
    }
}
