// swift-tools-version: 5.10

import PackageDescription

let package = Package(
    name: "SceneViewSwift",
    platforms: [
        .iOS("18.0"),
        .macOS("15.0"),
        .visionOS(.v1)
    ],
    products: [
        .library(
            name: "SceneViewSwift",
            targets: ["SceneViewSwift"]
        )
    ],
    dependencies: [
        // DocC generation — `swift package generate-documentation --target SceneViewSwift`
        // produces a browsable .doccarchive. CI publishes it alongside the SPM tag so
        // Apple-side consumers get a real Apple-style docs site instead of just KDoc.
        // (#945)
        .package(url: "https://github.com/apple/swift-docc-plugin", from: "1.4.0")
    ],
    targets: [
        .target(
            name: "SceneViewSwift",
            dependencies: [],
            path: "Sources/SceneViewSwift"
        ),
        .testTarget(
            name: "SceneViewSwiftTests",
            dependencies: ["SceneViewSwift"],
            path: "Tests/SceneViewSwiftTests"
        )
    ]
)
