---
title: API Cheatsheet — SceneView for iOS, macOS, visionOS
description: "Complete API reference for SceneViewSwift node types, views, materials, physics, and AR features on Apple platforms."
---

# API Cheatsheet — Apple Platforms

A quick reference for SceneViewSwift's most-used APIs. Print it, pin it, keep it next to your keyboard.

!!! tip "Building for Android?"
    See the [Android API Cheatsheet](cheatsheet.md) for Jetpack Compose equivalents.

---

## Setup

```swift
// Package.swift or Xcode SPM
.package(url: "https://github.com/sceneview/sceneview-swift.git", from: "4.0.2")
```

```swift
import SceneViewSwift
```

---

## SceneView (3D)

```swift
// Declarative (recommended — v4.0.0+)
SceneView {
    GeometryNode.cube(size: 0.3, color: .red)
        .position(.init(x: -1, y: 0, z: -2))
    GeometryNode.sphere(radius: 0.2, color: .blue)
        .position(.init(x: 1, y: 0, z: -2))
}
.environment(.studio)
.cameraControls(.orbit)

// Imperative (still supported)
SceneView { root in
    root.addChild(model.entity)
}
.environment(.studio)              // IBL lighting preset
.cameraControls(.orbit)            // orbit / pan / zoom gestures
.onEntityTapped { entity in }      // tap handler
.autoRotate(speed: 0.3)            // turntable auto-rotation
```

---

## ARSceneView (AR — iOS only)

```swift
ARSceneView(
    planeDetection: .horizontal,       // .horizontal, .vertical, .both, .none
    showPlaneOverlay: true,            // visualize detected planes
    showCoachingOverlay: true,         // ARKit coaching UI
    onTapOnPlane: { position, arView in
        let anchor = AnchorNode.world(position: position)
        anchor.add(model.entity)
        arView.scene.addAnchor(anchor.entity)
    }
)
.onSessionStarted { arView in }       // called when AR session begins
```

---

## Node Types — 3D

| Node | Factory / Init | Key Parameters |
|---|---|---|
| `ModelNode` | `ModelNode.load("file.usdz")` | `async throws`, `.scaleToUnits()`, `.position()`, `.rotation()` |
| `GeometryNode` | `.cube(size:color:)` | `size`, `color`, `cornerRadius` |
| | `.sphere(radius:color:)` | `radius`, `color` |
| | `.cylinder(radius:height:color:)` | `radius`, `height`, `color` |
| | `.plane(width:depth:color:)` | `width`, `depth`, `color` |
| | `.cone(height:radius:color:)` | `height`, `radius`, `color` |
| `LightNode` | `.directional(color:intensity:castsShadow:)` | `.position()`, `.lookAt()`, `.shadowMaximumDistance()` |
| | `.point(color:intensity:attenuationRadius:)` | `.position()`, `.attenuationRadius()` |
| | `.spot(color:intensity:innerAngle:outerAngle:)` | `.position()`, `.lookAt()` |
| `TextNode` | `TextNode(text:fontSize:color:depth:)` | `.position()`, `.centered()`, `.withText()` |
| `ImageNode` | `ImageNode(named:size:)` | `.position()`, `.billboard()` |
| `BillboardNode` | `BillboardNode(named:width:height:)` | always faces camera |
| `VideoNode` | `VideoNode(url:size:)` | `.play()`, `.pause()` |
| `LineNode` | `LineNode(start:end:color:)` | `SIMD3<Float>` endpoints |
| `PathNode` | `PathNode(points:closed:color:)` | `[SIMD3<Float>]` path |
| `PhysicsNode` | `.dynamic(entity, mass:restitution:)` | `.static(entity)`, `.kinematic(entity)` |
| `DynamicSkyNode` | `DynamicSkyNode(timeOfDay:turbidity:)` | `0...24` time cycle |
| `FogNode` | `FogNode(density:color:)` | atmospheric fog |
| `ReflectionProbeNode` | `ReflectionProbeNode(position:radius:)` | zone-based IBL |

---

## Node Types — AR (iOS)

| Node | Usage |
|---|---|
| `AnchorNode.world(position:)` | Anchor at a world coordinate |
| `AnchorNode.plane(alignment:minimumBounds:)` | Anchor on a detected plane |
| `AugmentedImageNode` | Overlay content on a detected reference image |

---

## Common Patterns

### Load a model

```swift
@State private var model: ModelNode?

SceneView { root in
    if let model {
        root.addChild(model.entity)
    }
}
.task {
    model = try? await ModelNode.load("models/car.usdz")
    model?.scaleToUnits(1.0)
    model?.playAllAnimations()
}
```

### Add a light

```swift
SceneView { root in
    let sun = LightNode.directional(
        color: .warm,
        intensity: 1500,
        castsShadow: true
    )
    sun.entity.look(at: .zero, from: [2, 4, 2], relativeTo: nil)
    root.addChild(sun.entity)
}
```

### Create geometry

```swift
SceneView { root in
    let cube = GeometryNode.cube(size: 0.5, color: .red)
        .position(.init(x: -1, y: 0.25, z: -2))
    root.addChild(cube.entity)

    let metalSphere = GeometryNode.sphere(
        radius: 0.3,
        material: .pbr(color: .gray, metallic: 1.0, roughness: 0.2)
    )
    root.addChild(metalSphere.entity)
}
```

### AR tap-to-place

```swift
ARSceneView(
    planeDetection: .horizontal,
    onTapOnPlane: { position, arView in
        let anchor = AnchorNode.world(position: position)
        let cube = GeometryNode.cube(size: 0.1, color: .blue)
        anchor.add(cube.entity)
        arView.scene.addAnchor(anchor.entity)
    }
)
```

### Physics

```swift
SceneView { root in
    // Falling ball
    let ball = GeometryNode.sphere(radius: 0.1, color: .red)
    PhysicsNode.dynamic(ball.entity, mass: 1.0, restitution: 0.8)
    ball.position = .init(x: 0, y: 3, z: -2)
    root.addChild(ball.entity)

    // Static floor
    let floor = GeometryNode.plane(width: 10, depth: 10, color: .gray)
    PhysicsNode.static(floor.entity)
    root.addChild(floor.entity)
}
```

### 3D text

```swift
SceneView { root in
    let label = TextNode(
        text: "Hello 3D!",
        fontSize: 0.1,
        color: .white
    )
    .position(.init(x: 0, y: 1, z: -2))
    .centered()
    root.addChild(label.entity)
}
```

### Per-entity gestures (v4.0.0+)

```swift
// Fluent API on Entity
let cube = GeometryNode.cube(size: 0.3, color: .blue)
cube.entity
    .onTap { print("Tapped!") }
    .onDrag { translation in cube.position += translation }
    .onScale { factor in cube.scale *= .init(repeating: factor) }
    .onRotate { angle in /* handle rotation */ }

// Or via static NodeGesture methods
NodeGesture.onTap(entity) { print("Tapped!") }
NodeGesture.onLongPress(entity) { print("Long pressed!") }
NodeGesture.removeAll(from: entity)  // cleanup
```

---

## Environment Presets

```swift
.environment(.studio)    // neutral studio (default)
.environment(.outdoor)   // warm daylight
.environment(.sunset)    // golden hour
.environment(.night)     // dark, moody
.environment(.warm)      // cozy tungsten
.environment(.autumn)    // soft outdoor
```

Custom environment:

```swift
.environment(.custom(name: "My HDR", hdrFile: "custom.hdr", intensity: 1.5))
```

---

## Materials

```swift
// Simple color
GeometryNode.cube(size: 0.5, color: .red)

// PBR with metallic/roughness
GeometryNode.sphere(radius: 0.3, material: .pbr(
    color: .gray, metallic: 1.0, roughness: 0.2
))

// Textured PBR
let texture = try await GeometryMaterial.loadTexture("brick_diffuse.png")
let node = GeometryNode.cube(
    size: 1.0,
    material: .textured(baseColor: texture, roughness: 0.8)
)

// Unlit (no lighting response)
GeometryNode.plane(width: 1, depth: 1, color: .white)  // use .unlit for no shading
```

---

## Transform Helpers

```swift
model.position = SIMD3<Float>(x: 1, y: 0, z: -2)     // meters
model.rotation = simd_quatf(angle: .pi / 4, axis: [0, 1, 0])
model.scale = SIMD3<Float>(repeating: 2.0)             // uniform

// Fluent API
model.position(.init(x: 1, y: 0, z: -2))
     .scale(0.5)
     .rotation(angle: .pi, axis: [0, 1, 0])
```

---

## Animation

```swift
// Play all animations (looping)
model?.playAllAnimations(loop: true, speed: 1.0)

// Play specific animation by index
model?.playAnimation(at: 0, loop: true, speed: 1.5)

// Stop
model?.stopAllAnimations()
```

---

## Threading Rules

| Safe | Unsafe |
|---|---|
| `ModelNode.load(...)` in `.task` | Mutating entities off `@MainActor` |
| `GeometryNode.*` factory methods | Accessing RealityKit components from background threads |
| Any code in SwiftUI view body | Direct `Entity` manipulation from `DispatchQueue.global()` |

**Rule:** RealityKit entities are `@MainActor`-isolated. Use `await MainActor.run { }` if you need to modify entities from a background context. SwiftUI's `.task` modifier runs on the main actor by default for view-related work.

---

## Android ↔ Apple API Mapping

| Android (Compose) | Apple (SwiftUI) |
|---|---|
| `SceneView { }` | `SceneView { root in }` |
| `ARSceneView { }` | `ARSceneView(...)` |
| `rememberModelInstance(loader, path)` | `ModelNode.load(path)` |
| `ModelNode(modelInstance, scaleToUnits)` | `model.scaleToUnits(units)` |
| `CubeNode(size, material)` | `GeometryNode.cube(size:color:)` |
| `SphereNode(radius, material)` | `GeometryNode.sphere(radius:color:)` |
| `LightNode(type, apply = { })` | `LightNode.directional(...)` / `.point(...)` / `.spot(...)` |
| `rememberEnvironmentLoader` | `.environment(.studio)` view modifier |
| `rememberCameraManipulator()` | `.cameraControls(.orbit)` view modifier |
| `AnchorNode(anchor)` | `AnchorNode.world(position:)` |
| `PhysicsNode(node, mass)` | `PhysicsNode.dynamic(entity, mass:)` |

---

## Android

Building for Android with Jetpack Compose? See the [Android API Cheatsheet](cheatsheet.md).
