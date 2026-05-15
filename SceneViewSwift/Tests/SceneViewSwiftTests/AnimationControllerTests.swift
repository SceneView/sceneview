import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit

// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class AnimationControllerTests: XCTestCase {

    // MARK: - Initialization

    func testInitialStateIsIdle() {
        let entity = ModelEntity()
        let controller = AnimationController(entity: entity)
        XCTAssertEqual(controller.state, .idle)
    }

    func testInitialSpeedIsOne() {
        let controller = AnimationController(entity: ModelEntity())
        XCTAssertEqual(controller.speed, 1.0, accuracy: 0.001)
    }

    func testCurrentAnimationNameIsNilWhenIdle() {
        let controller = AnimationController(entity: ModelEntity())
        XCTAssertNil(controller.currentAnimationName)
    }

    // MARK: - Available animations

    func testAvailableAnimationsEmptyOnBlankEntity() {
        let controller = AnimationController(entity: ModelEntity())
        XCTAssertTrue(controller.availableAnimations.isEmpty)
    }

    func testAnimationCountOnBlankEntity() {
        let controller = AnimationController(entity: ModelEntity())
        XCTAssertEqual(controller.animationCount, 0)
    }

    // MARK: - Play by name (no animations available)

    func testPlayByNameReturnsFalseWhenNoAnimations() {
        let controller = AnimationController(entity: ModelEntity())
        let result = controller.play("walk")
        XCTAssertFalse(result)
    }

    func testPlayByIndexReturnsFalseWhenOutOfBounds() {
        let controller = AnimationController(entity: ModelEntity())
        let result = controller.play(at: 0)
        XCTAssertFalse(result)
    }

    // MARK: - Stop

    func testStopResetsToIdle() {
        let controller = AnimationController(entity: ModelEntity())
        controller.stop()
        XCTAssertEqual(controller.state, .idle)
    }

    // MARK: - Speed control

    func testSetSpeed() {
        let controller = AnimationController(entity: ModelEntity())
        controller.setSpeed(2.0)
        XCTAssertEqual(controller.speed, 2.0, accuracy: 0.001)
    }

    // MARK: - State observation

    func testOnStateChangedCallbackFires() {
        let controller = AnimationController(entity: ModelEntity())
        var receivedState: AnimationController.State?

        controller.onStateChanged { state in
            receivedState = state
        }

        // Stop should trigger idle (it's already idle, but internally it sets it again)
        controller.stop()
        // Since state was already idle, callback should not fire
        XCTAssertNil(receivedState)
    }

    // MARK: - PlayAll

    func testPlayAllDoesNotCrashWithNoAnimations() {
        let controller = AnimationController(entity: ModelEntity())
        controller.playAll()
        XCTAssertEqual(controller.state, .playing(name: "all"))
    }

    // MARK: - Crossfade

    func testCrossfadeReturnsFalseWithNoAnimations() {
        let controller = AnimationController(entity: ModelEntity())
        let result = controller.crossfade(to: "run")
        XCTAssertFalse(result)
    }

    // MARK: - Pause and Resume

    func testPauseAndResumeDoNotCrash() {
        let controller = AnimationController(entity: ModelEntity())
        controller.pause()
        controller.resume()
        XCTAssertEqual(controller.state, .idle)
    }

    // MARK: - State equality

    func testStateEquality() {
        XCTAssertEqual(AnimationController.State.idle, .idle)
        XCTAssertEqual(
            AnimationController.State.playing(name: "walk"),
            .playing(name: "walk")
        )
        XCTAssertNotEqual(
            AnimationController.State.playing(name: "walk"),
            .playing(name: "run")
        )
        XCTAssertNotEqual(
            AnimationController.State.playing(name: "walk"),
            .paused(name: "walk")
        )
    }
}

#endif // os(iOS) || os(macOS) || os(visionOS)
