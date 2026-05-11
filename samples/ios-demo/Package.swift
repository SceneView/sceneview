// swift-tools-version:6.0
import PackageDescription

let package = Package(
    name: "SceneViewDemo",
    platforms: [.iOS(.v18), .macOS(.v15)],
    dependencies: [
        .package(name: "SceneViewSwift", path: "../../SceneViewSwift")
    ],
    targets: [
        .executableTarget(
            name: "SceneViewDemo",
            dependencies: [
                .product(name: "SceneViewSwift", package: "SceneViewSwift")
            ],
            path: "SceneViewDemo"
        )
        // iOS snapshot tests are run via generate-ios-goldens.py + verify-ios-goldens.py
        // which use xcrun simctl screenshots + Python Pillow for pixel comparison.
        // See .claude/scripts/generate-ios-goldens.py — the swift-snapshot-testing
        // ScreenshotTests target was dropped (#882): it imported `SceneViewDemoLib`,
        // a module that never existed, was never wired in the Xcode scheme's
        // TestAction, and therefore never ran on any CI system. The Python flow
        // is the canonical iOS regression path.
    ]
)
