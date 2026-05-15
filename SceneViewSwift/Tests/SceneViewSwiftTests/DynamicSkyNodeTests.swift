import XCTest
@testable import SceneViewSwift

#if os(iOS) || os(macOS) || os(visionOS)
import RealityKit

// Test classes run on the main actor: their RealityKit node factories
// (`LightNode.directional`, `node.entity`, …) are `@MainActor`. (#1054)
@MainActor
final class DynamicSkyNodeTests: XCTestCase {

    // MARK: - Initialization

    func testDefaultInitCreatesEntity() {
        let sky = DynamicSkyNode()
        XCTAssertNotNil(sky.entity)
        XCTAssertTrue(sky.entity is DirectionalLight)
    }

    func testDefaultInitUsesNoon() {
        let sky = DynamicSkyNode()
        XCTAssertEqual(sky.timeOfDay, 12, accuracy: 0.001)
    }

    func testDefaultInitTurbidity() {
        let sky = DynamicSkyNode()
        XCTAssertEqual(sky.turbidity, 2, accuracy: 0.001)
    }

    func testDefaultInitIntensity() {
        let sky = DynamicSkyNode()
        XCTAssertEqual(sky.sunIntensity, 1000, accuracy: 0.001)
    }

    func testCustomTimeInit() {
        let sky = DynamicSkyNode(timeOfDay: 6)
        XCTAssertEqual(sky.timeOfDay, 6, accuracy: 0.001)
    }

    func testTimeOfDayWrapsAt24() {
        let sky = DynamicSkyNode(timeOfDay: 25)
        XCTAssertEqual(sky.timeOfDay, 1, accuracy: 0.001)
    }

    func testTurbidityClampsToRange() {
        let low = DynamicSkyNode(turbidity: 0)
        XCTAssertEqual(low.turbidity, 1, accuracy: 0.001)

        let high = DynamicSkyNode(turbidity: 15)
        XCTAssertEqual(high.turbidity, 10, accuracy: 0.001)
    }

    // MARK: - Presets

    func testSunrisePreset() {
        let sky = DynamicSkyNode.sunrise()
        XCTAssertEqual(sky.timeOfDay, 6, accuracy: 0.001)
        XCTAssertNotNil(sky.entity)
    }

    func testNoonPreset() {
        let sky = DynamicSkyNode.noon()
        XCTAssertEqual(sky.timeOfDay, 12, accuracy: 0.001)
        XCTAssertNotNil(sky.entity)
    }

    func testSunsetPreset() {
        let sky = DynamicSkyNode.sunset()
        XCTAssertEqual(sky.timeOfDay, 18, accuracy: 0.001)
        XCTAssertNotNil(sky.entity)
    }

    func testNightPreset() {
        let sky = DynamicSkyNode.night()
        XCTAssertEqual(sky.timeOfDay, 0, accuracy: 0.001)
        XCTAssertNotNil(sky.entity)
    }

    func testPresetWithCustomIntensity() {
        let sky = DynamicSkyNode.noon(sunIntensity: 2000)
        XCTAssertEqual(sky.sunIntensity, 2000, accuracy: 0.001)
    }

    func testPresetWithCustomTurbidity() {
        let sky = DynamicSkyNode.sunset(turbidity: 8)
        XCTAssertEqual(sky.turbidity, 8, accuracy: 0.001)
    }

    // MARK: - Builder methods

    func testTimeBuilder() {
        let sky = DynamicSkyNode.noon().time(6)
        XCTAssertEqual(sky.timeOfDay, 6, accuracy: 0.001)
    }

    func testIntensityBuilder() {
        let sky = DynamicSkyNode.noon().intensity(5000)
        XCTAssertEqual(sky.sunIntensity, 5000, accuracy: 0.001)
    }

    func testPositionBuilder() {
        let sky = DynamicSkyNode.noon().position(SIMD3<Float>(1, 2, 3))
        XCTAssertEqual(sky.entity.position.x, 1.0, accuracy: 0.001)
        XCTAssertEqual(sky.entity.position.y, 2.0, accuracy: 0.001)
        XCTAssertEqual(sky.entity.position.z, 3.0, accuracy: 0.001)
    }

    func testBuilderChaining() {
        let sky = DynamicSkyNode.noon()
            .time(8)
            .intensity(2000)
            .position(SIMD3<Float>(0, 5, 0))

        XCTAssertEqual(sky.timeOfDay, 8, accuracy: 0.001)
        XCTAssertEqual(sky.sunIntensity, 2000, accuracy: 0.001)
        XCTAssertEqual(sky.entity.position.y, 5.0, accuracy: 0.001)
    }

    // MARK: - Sun state computation

    func testNoonSunIsOverhead() {
        let state = DynamicSkyNode.computeSunState(
            timeOfDay: 12, turbidity: 2, sunIntensity: 1000
        )
        // At noon, elevation should be ~1.0 (overhead)
        XCTAssertEqual(state.elevation, 1.0, accuracy: 0.01)
        // Y component of direction should be positive (pointing up)
        XCTAssertGreaterThan(state.direction.y, 0)
    }

    func testSunriseElevationIsZero() {
        let state = DynamicSkyNode.computeSunState(
            timeOfDay: 6, turbidity: 2, sunIntensity: 1000
        )
        XCTAssertEqual(state.elevation, 0.0, accuracy: 0.01)
    }

    func testSunsetElevationIsZero() {
        let state = DynamicSkyNode.computeSunState(
            timeOfDay: 18, turbidity: 2, sunIntensity: 1000
        )
        XCTAssertEqual(state.elevation, 0.0, accuracy: 0.01)
    }

    func testMidnightElevationIsNegative() {
        let state = DynamicSkyNode.computeSunState(
            timeOfDay: 0, turbidity: 2, sunIntensity: 1000
        )
        XCTAssertLessThan(state.elevation, 0)
    }

    func testNightIntensityIsZero() {
        let state = DynamicSkyNode.computeSunState(
            timeOfDay: 0, turbidity: 2, sunIntensity: 1000
        )
        XCTAssertEqual(state.intensity, 0, accuracy: 0.001)
    }

    func testNoonIntensityEqualsMax() {
        let state = DynamicSkyNode.computeSunState(
            timeOfDay: 12, turbidity: 2, sunIntensity: 1000
        )
        XCTAssertEqual(state.intensity, 1000, accuracy: 1)
    }

    func testMorningIntensityIsPartial() {
        let state = DynamicSkyNode.computeSunState(
            timeOfDay: 9, turbidity: 2, sunIntensity: 1000
        )
        XCTAssertGreaterThan(state.intensity, 0)
        XCTAssertLessThan(state.intensity, 1000)
    }

    // MARK: - Sun color

    func testNightColorIsDark() {
        let state = DynamicSkyNode.computeSunState(
            timeOfDay: 0, turbidity: 2, sunIntensity: 1000
        )
        XCTAssertLessThan(state.color.x, 0.1)
        XCTAssertLessThan(state.color.y, 0.1)
        XCTAssertLessThan(state.color.z, 0.1)
    }

    func testNoonColorIsNearWhite() {
        let state = DynamicSkyNode.computeSunState(
            timeOfDay: 12, turbidity: 2, sunIntensity: 1000
        )
        XCTAssertGreaterThan(state.color.x, 0.9)
        XCTAssertGreaterThan(state.color.y, 0.9)
        XCTAssertGreaterThan(state.color.z, 0.9)
    }

    func testSunriseColorIsWarm() {
        // Just above horizon (6.5h) should be warmer than noon
        let sunriseState = DynamicSkyNode.computeSunState(
            timeOfDay: 6.5, turbidity: 2, sunIntensity: 1000
        )
        let noonState = DynamicSkyNode.computeSunState(
            timeOfDay: 12, turbidity: 2, sunIntensity: 1000
        )
        // Sunrise should have a lower blue component relative to red
        let sunriseRatio = sunriseState.color.z / sunriseState.color.x
        let noonRatio = noonState.color.z / noonState.color.x
        XCTAssertLessThan(sunriseRatio, noonRatio,
                          "Sunrise should be warmer (less blue relative to red) than noon")
    }

    func testHighTurbidityWarmerColor() {
        let lowTurb = DynamicSkyNode.computeSunState(
            timeOfDay: 7, turbidity: 1, sunIntensity: 1000
        )
        let highTurb = DynamicSkyNode.computeSunState(
            timeOfDay: 7, turbidity: 10, sunIntensity: 1000
        )
        // Higher turbidity should produce less blue
        XCTAssertLessThanOrEqual(highTurb.color.z, lowTurb.color.z)
    }

    // MARK: - Sun direction

    func testSunDirectionIsNormalized() {
        for hour: Float in stride(from: 0, through: 24, by: 2) {
            let state = DynamicSkyNode.computeSunState(
                timeOfDay: hour, turbidity: 2, sunIntensity: 1000
            )
            let len = sqrt(
                state.direction.x * state.direction.x +
                state.direction.y * state.direction.y +
                state.direction.z * state.direction.z
            )
            XCTAssertEqual(len, 1.0, accuracy: 0.01,
                           "Direction should be normalized at hour \(hour)")
        }
    }

    // MARK: - isDaytime

    func testIsDaytimeAtNoon() {
        let sky = DynamicSkyNode.noon()
        XCTAssertTrue(sky.isDaytime)
    }

    func testIsDaytimeAtSunrise() {
        let sky = DynamicSkyNode.sunrise()
        XCTAssertTrue(sky.isDaytime)
    }

    func testIsDaytimeAtSunset() {
        let sky = DynamicSkyNode.sunset()
        XCTAssertTrue(sky.isDaytime)
    }

    func testIsNotDaytimeAtNight() {
        let sky = DynamicSkyNode.night()
        XCTAssertFalse(sky.isDaytime)
    }

    func testIsNotDaytimeAt3AM() {
        let sky = DynamicSkyNode(timeOfDay: 3)
        XCTAssertFalse(sky.isDaytime)
    }

    func testIsNotDaytimeAt21() {
        let sky = DynamicSkyNode(timeOfDay: 21)
        XCTAssertFalse(sky.isDaytime)
    }

    // MARK: - Computed properties

    func testSunDirectionProperty() {
        let sky = DynamicSkyNode.noon()
        let dir = sky.sunDirection
        // Should be a valid normalized vector
        let len = sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z)
        XCTAssertEqual(len, 1.0, accuracy: 0.01)
    }

    func testSunColorProperty() {
        let sky = DynamicSkyNode.noon()
        let color = sky.sunColor
        // Noon color should be near-white
        XCTAssertGreaterThan(color.x, 0.9)
        XCTAssertGreaterThan(color.y, 0.9)
    }

    func testEffectiveIntensityProperty() {
        let sky = DynamicSkyNode.noon()
        XCTAssertEqual(sky.effectiveIntensity, 1000, accuracy: 1)
    }

    func testEffectiveIntensityAtNight() {
        let sky = DynamicSkyNode.night()
        XCTAssertEqual(sky.effectiveIntensity, 0, accuracy: 0.001)
    }

    // MARK: - Time builder wrapping

    func testTimeBuilderWrapsAt24() {
        let sky = DynamicSkyNode.noon().time(25)
        XCTAssertEqual(sky.timeOfDay, 1, accuracy: 0.001)
    }

    func testTimeBuilderWrapsNegative() {
        let sky = DynamicSkyNode.noon().time(-1)
        // truncatingRemainder: -1.truncatingRemainder(dividingBy: 24) = -1
        XCTAssertEqual(sky.timeOfDay, -1, accuracy: 0.001)
    }

    func testTimeBuilderAt24() {
        let sky = DynamicSkyNode.noon().time(24)
        XCTAssertEqual(sky.timeOfDay, 0, accuracy: 0.001)
    }

    func testTimeBuilderAtZero() {
        let sky = DynamicSkyNode.noon().time(0)
        XCTAssertEqual(sky.timeOfDay, 0, accuracy: 0.001)
    }

    // MARK: - Intensity builder edge cases

    func testIntensityBuilderWithZero() {
        let sky = DynamicSkyNode.noon().intensity(0)
        XCTAssertEqual(sky.sunIntensity, 0, accuracy: 0.001)
        XCTAssertEqual(sky.effectiveIntensity, 0, accuracy: 0.001)
    }

    func testIntensityBuilderWithNegative() {
        let sky = DynamicSkyNode.noon().intensity(-500)
        XCTAssertEqual(sky.sunIntensity, -500, accuracy: 0.001)
    }

    func testIntensityBuilderWithVeryHigh() {
        let sky = DynamicSkyNode.noon().intensity(100_000)
        XCTAssertEqual(sky.sunIntensity, 100_000, accuracy: 0.001)
    }

    // MARK: - isDaytime at boundaries

    func testIsDaytimeAtExactly6() {
        let sky = DynamicSkyNode(timeOfDay: 6)
        XCTAssertTrue(sky.isDaytime)
    }

    func testIsDaytimeAtExactly18() {
        let sky = DynamicSkyNode(timeOfDay: 18)
        XCTAssertTrue(sky.isDaytime)
    }

    func testIsNotDaytimeAt5Point99() {
        let sky = DynamicSkyNode(timeOfDay: 5.99)
        XCTAssertFalse(sky.isDaytime)
    }

    func testIsNotDaytimeAt18Point01() {
        let sky = DynamicSkyNode(timeOfDay: 18.01)
        XCTAssertFalse(sky.isDaytime)
    }

    // MARK: - Sun state at edge times

    func testSunStateAtExactly12() {
        let state = DynamicSkyNode.computeSunState(
            timeOfDay: 12, turbidity: 2, sunIntensity: 500
        )
        XCTAssertEqual(state.intensity, 500, accuracy: 1)
        XCTAssertEqual(state.elevation, 1.0, accuracy: 0.01)
    }

    func testSunStateAtMidnightElevation() {
        // At midnight (0h), hourAngle = ((0-6)/12) * pi = -pi/2
        // elevation = sin(-pi/2) = -1
        let state = DynamicSkyNode.computeSunState(
            timeOfDay: 0, turbidity: 2, sunIntensity: 1000
        )
        XCTAssertEqual(state.elevation, -1.0, accuracy: 0.01)
    }
}
#endif
