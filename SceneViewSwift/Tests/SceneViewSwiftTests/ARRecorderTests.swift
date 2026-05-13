#if os(iOS)
import XCTest
import ReplayKit
@testable import SceneViewSwift

/// JVM-equivalent unit tests for `ARRecorder` — closes #1032.
///
/// Mirrors `arsceneview/src/test/java/io/github/sceneview/ar/recording/ARRecorderTest.kt`.
/// Only covers the pure-Swift surface (state machine, error mapping,
/// defaultOutputURL); `RPScreenRecorder` itself is not exercised because
/// the simulator's screen-capture stack is unavailable on iOS < 17.4
/// and on the CI runner.
@MainActor
final class ARRecorderTests: XCTestCase {

    // MARK: - State machine

    func testInitialStateIsIdle() {
        let recorder = ARRecorder()
        XCTAssertEqual(recorder.state, .idle)
        XCTAssertNil(recorder.lastOutputURL)
    }

    func testStopWhenNotRecordingThrowsAndSetsErrorState() async {
        let recorder = ARRecorder()
        do {
            _ = try await recorder.stopRecording()
            XCTFail("stopRecording() should have thrown when no recording is in progress")
        } catch let error as ARRecorderError {
            // The recorder's internal RPScreenRecorder isn't recording, so we
            // expect `.notRecording`.
            if case .notRecording = error {
                // OK
            } else {
                XCTFail("expected .notRecording, got \(error)")
            }
        } catch {
            XCTFail("expected ARRecorderError, got \(type(of: error)): \(error)")
        }
        if case .error = recorder.state {
            // OK
        } else {
            XCTFail("expected .error state after failed stop, got \(recorder.state)")
        }
    }

    // MARK: - Error mapping

    func testARRecorderErrorEquatable() {
        XCTAssertEqual(
            ARRecorderError.unavailable("foo"),
            ARRecorderError.unavailable("foo")
        )
        XCTAssertNotEqual(
            ARRecorderError.unavailable("foo"),
            ARRecorderError.unavailable("bar")
        )
        XCTAssertNotEqual(
            ARRecorderError.unavailable("foo"),
            ARRecorderError.alreadyRecording("foo")
        )
    }

    func testErrorDescriptionsExposeUnderlyingMessage() {
        let cases: [ARRecorderError] = [
            .unavailable("u-msg"),
            .alreadyRecording("a-msg"),
            .notRecording("n-msg"),
        ]
        for err in cases {
            XCTAssertNotNil(err.errorDescription)
            XCTAssertFalse(err.errorDescription!.isEmpty)
        }
    }

    // MARK: - Default output URL

    func testDefaultOutputURLIsUnderCachesDirectory() {
        let url = ARRecorder.defaultOutputURL()
        let caches = FileManager.default
            .urls(for: .cachesDirectory, in: .userDomainMask)
            .first
        if let caches {
            XCTAssertTrue(
                url.path.hasPrefix(caches.path),
                "expected default URL to be under \(caches.path); got \(url.path)"
            )
        } else {
            // Fallback path used when .cachesDirectory is unavailable —
            // accept either location so the test is robust on CI runners.
            XCTAssertTrue(
                url.path.hasPrefix(NSTemporaryDirectory()),
                "expected default URL to fall back to NSTemporaryDirectory(); got \(url.path)"
            )
        }
    }

    func testDefaultOutputURLContainsARRecorderFolder() {
        let url = ARRecorder.defaultOutputURL()
        XCTAssertTrue(
            url.path.contains("/ARRecorder/"),
            "expected default URL to live under an /ARRecorder/ subfolder; got \(url.path)"
        )
    }

    func testDefaultOutputURLEndsWithMov() {
        let url = ARRecorder.defaultOutputURL()
        XCTAssertEqual(url.pathExtension, "mov")
    }

    func testDefaultOutputURLContainsARRecordingPrefix() {
        let url = ARRecorder.defaultOutputURL()
        XCTAssertTrue(
            url.lastPathComponent.hasPrefix("ARRecording-"),
            "expected default URL to start with 'ARRecording-'; got \(url.lastPathComponent)"
        )
    }

    func testDefaultOutputURLIsUniquePerCall() {
        let a = ARRecorder.defaultOutputURL()
        let b = ARRecorder.defaultOutputURL()
        XCTAssertNotEqual(
            a.lastPathComponent,
            b.lastPathComponent,
            "two consecutive defaultOutputURL() calls should return unique UUID-based names"
        )
    }

    // MARK: - Error code mapping

    func testErrorMapperUserDeclinedMapsToPermissionDenied() {
        let err = NSError(domain: "com.apple.replaykit", code: -5803, userInfo: nil)
        let mapped = ARRecorderError.from(err)
        if case .permissionDenied = mapped {
            // OK
        } else {
            XCTFail("expected .permissionDenied for code -5803, got \(mapped)")
        }
    }

    func testErrorMapperDisabledMapsToDisabled() {
        let err = NSError(domain: "com.apple.replaykit", code: -5801, userInfo: nil)
        let mapped = ARRecorderError.from(err)
        if case .disabled = mapped {
            // OK
        } else {
            XCTFail("expected .disabled for code -5801, got \(mapped)")
        }
    }

    func testErrorMapperUnknownCodeMapsToOther() {
        let err = NSError(domain: "com.apple.replaykit", code: -999999, userInfo: nil)
        let mapped = ARRecorderError.from(err)
        if case .other(_, let code) = mapped {
            XCTAssertEqual(code, -999999, "expected .other to preserve the underlying NSError.code")
        } else {
            XCTFail("expected .other for unknown error code, got \(mapped)")
        }
    }

    // MARK: - rememberARRecorder factory

    func testRememberedFactoryReturnsRecorder() {
        let recorder = ARRecorder.remembered()
        XCTAssertEqual(recorder.state, .idle)
        XCTAssertNil(recorder.lastOutputURL)
    }
}
#endif
