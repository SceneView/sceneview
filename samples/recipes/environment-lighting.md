# Recipe: Environment Lighting (HDR/IBL)

**Intent:** "Set up realistic lighting with an HDR environment"

## Android (Kotlin + Jetpack Compose)

```kotlin
@Composable
fun ModelWithEnvironment() {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val model = rememberModelInstance(modelLoader, "models/helmet.glb")

    SceneView(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        // HDR environment provides both indirect lighting (IBL) and skybox
        environment = rememberEnvironment(engine, "envs/studio.hdr"),
        cameraManipulator = rememberCameraManipulator()
    ) {
        model?.let {
            ModelNode(modelInstance = it, scaleToUnits = 1.0f)
        }

        // Optional: add a directional light for sharper shadows
        LightNode(apply = {
            type(LightManager.Type.DIRECTIONAL)
            intensity(100_000f)
            direction(0f, -1f, -1f)
            castShadows(true)
        })
    }
}
```

## iOS (SwiftUI)

```swift
SceneView(environment: .studio) {
    ModelNode(named: "helmet.usdz")
        .scaleToUnits(1.0)
    LightNode.directional(intensity: 1000)
        .direction(x: 0, y: -1, z: -1)
}
.cameraControls(.orbit)
```

## visionOS — immersive-space skybox

A windowed or volumetric `RealityView` on visionOS composites over
passthrough, so the HDR `showSkybox` only lights the scene — the background
stays passthrough. A fully immersive `ImmersiveSpace` *can* host an HDR
background: opt in with `.immersiveSpace()` and the HDR is rendered as a
`WorldComponent`-rooted inverted sphere.

```swift
@main
struct MyApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }

        ImmersiveSpace(id: "scene") {
            SceneView { root in
                root.addChild(model.entity)
            }
            .environment(.nightSky)   // showSkybox == true
            .immersiveSpace()         // render the HDR skybox on visionOS
        }
        .immersionStyle(selection: .constant(.full), in: .full)
    }
}
```

`.immersiveSpace()` is a no-op on iOS / macOS — those use the windowed
`.skybox(_:)` path automatically.

## Available Environments

| Environment | Description | Best for |
|---|---|---|
| `studio.hdr` | Neutral studio lighting | Product shots, model viewers |
| `outdoor.hdr` | Outdoor daylight | Architectural scenes |
| `sunset.hdr` | Warm golden hour | Atmospheric scenes |
| `night.hdr` | Dark environment | Dramatic lighting |

## Key Points

- `rememberEnvironment(engine, "envs/studio.hdr")` loads the HDR file from assets
- HDR environments provide **Image-Based Lighting** (IBL) for realistic reflections
- They also set the skybox (background) — set `skybox = false` to keep a solid background
- Combine IBL with `LightNode` for direct light sources (sun, lamps)
- `LightNode`'s `apply` is a **named parameter**, not a trailing lambda: `apply = { ... }`
- All HDR files should be in `src/main/assets/envs/` for Android
