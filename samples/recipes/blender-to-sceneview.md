# Recipe: Blender → SceneView Asset Pipeline

**Intent:** "Author a custom 3D model in Blender and ship it in a SceneView app on iOS and Android"

> This recipe is adapted from [@radcli14](https://github.com/radcli14)'s comprehensive
> Blender → RealityKit tutorial ([`radcli14/blender-to-realitykit`](https://github.com/radcli14/blender-to-realitykit),
> MIT, 17⭐). **Read the original first** for the full Blender-side detail — modelling,
> texturing, materials, displacement. This page is the **SceneView-specific adapter**:
> it covers the Apple-tool and Android-tool steps and how to load the result through the
> SceneView API.

SceneView consumes two model formats:

- **`.glb`** — glTF binary. Native on Android (and Web). The canonical interchange format.
- **`.usdz`** — Universal Scene Description archive. Native on Apple (RealityKit).

A single Blender model feeds both. The two platforms diverge only at the final
conversion step.

```
                    ┌─────────────────────┐
                    │   Blender model     │
                    │  (textured, posed)  │
                    └──────────┬──────────┘
                               │  export glTF 2.0
                               ▼
                    ┌─────────────────────┐
                    │       car.glb       │
                    └────┬───────────┬────┘
            Android      │           │      iOS / macOS / visionOS
                         ▼           ▼
              assets/models/   Reality Converter
              car.glb               │  → car.usdz
                         │           ▼
                         │   Reality Composer Pro
                         │   (fix materials + lighting)
                         ▼           ▼
              rememberModelInstance   ModelNode.load("car.usdz")
```

## Step 1 — Export `.glb` from Blender (both platforms)

In Blender: **File → Export → glTF 2.0 (.glb/.gltf)**. Choose the **`glTF Binary (.glb)`**
format so textures are embedded in a single file.

> ⚠️ **Do NOT use Blender's USDZ exporter.** Blender can export `.usdz` directly, but the
> material round-trip is broken — PBR inputs land on the wrong slots and the model renders
> untextured or flat-shaded in RealityKit. Eliott's tutorial documents this in detail. The
> reliable path is **glb → Reality Converter**, never **Blender → usdz**.

This single `car.glb` is the source of truth for every platform.

## Step 2a — Android: drop the `.glb` in and load it

`.glb` is native on Android. There is **no Reality Converter step** — copy the file
straight into your app's `assets/` directory, e.g.:

```
src/main/assets/models/car.glb
```

> The path above is illustrative — `car.glb` is the file you exported in Step 1, not a
> model that ships with this repo. SceneView's own samples live under
> `samples/android-demo/src/main/assets/models/` (`khronos_toy_car.glb`, `shiba.glb`, …).

Load it in a composable with `rememberModelInstance`:

```kotlin
@Composable
fun CarViewer() {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val model = rememberModelInstance(modelLoader, "models/car.glb")

    SceneView(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        cameraManipulator = rememberCameraManipulator()
    ) {
        model?.let {
            ModelNode(modelInstance = it, scaleToUnits = 1.0f)
        }
    }
}
```

That's the whole Android path. The same `.glb` also works unchanged on the **Web** target
(`sceneview-web`), which loads glTF via Filament.js.

## Step 2b — iOS: convert `.glb` to `.usdz` with Reality Converter

Apple's renderer (RealityKit) wants `.usdz`. Convert with the two free Apple tools from
the [Apple AR tools page](https://developer.apple.com/augmented-reality/tools/):

1. **[Reality Converter](https://developer.apple.com/augmented-reality/tools/)** —
   drag `car.glb` into the window, inspect the preview, then **File → Export → USDZ**.
   Reality Converter handles glTF → USD natively and is the tool Apple intends for this
   job. It produces `car.usdz`.

2. **[Reality Composer Pro](https://developer.apple.com/augmented-reality/tools/)** —
   open `car.usdz` here to **clean up materials, lighting, and animations**. This is the
   step that fixes anything the conversion didn't carry perfectly:
   - Re-seat PBR texture maps if a slot looks wrong.
   - Adjust metallic / roughness values.
   - Bake or trim the lighting rig that ships inside the USD scene.
   - Trim or rename animation clips.

   Export the cleaned scene back to `car.usdz`.

Drop the result into the iOS demo's bundle and load it in SwiftUI:

```swift
struct CarViewer: View {
    @State private var model: ModelNode?

    var body: some View {
        SceneView { content in
            if let model {
                content.add(model.entity)
            }
        }
        .cameraControls(.orbit)
        .environment(.studio)
        .task {
            model = try? await ModelNode.load("car.usdz")?
                .scaleToUnits(1.0)
        }
    }
}
```

## Step 3 — PBR textures from iPhone photos (optional)

For photorealistic surfaces you need a full PBR texture set: **albedo + roughness +
normal + displacement**. Eliott's tutorial recommends [PolyCam](https://poly.cam/) —
photograph a real surface with an iPhone and PolyCam generates the maps from the
captured photos. Import those maps as image textures in Blender's Shader Editor before
you export the `.glb`.

## Pitfalls (from @radcli14's tutorial)

| Pitfall | Fix |
|---|---|
| Blender's `.usdz` exporter produces broken materials | Export `.glb`, convert with **Reality Converter** instead. |
| Conversion drops or mis-seats a material | Open the `.usdz` in **Reality Composer Pro** and re-seat the slots. |
| Displacement maps don't survive the export | Displacement is **Blender-preview only** (Cycles + Subdivide Surface modifier). For shipped models, bake the detail into a **normal map** instead — normal maps survive both glTF and USD. |
| Need realistic textures fast | Capture a real surface with [PolyCam](https://poly.cam/) → import the generated albedo / roughness / normal maps. |

## SceneView gotcha — Android Filament threading

This one is **not in Eliott's tutorial** — it's specific to SceneView's Android renderer.

Filament (the Android rendering engine) is a JNI library, and its model/material calls
**must run on the main thread**. Loading a model off a background coroutine directly
will crash or corrupt the renderer:

```kotlin
// ❌ WRONG — Filament JNI call off the main thread
viewModelScope.launch(Dispatchers.IO) {
    val instance = modelLoader.createModelInstance("models/car.glb")
}

// ✅ CORRECT — rememberModelInstance marshals to the main thread for you
@Composable
fun CarViewer() {
    val model = rememberModelInstance(modelLoader, "models/car.glb")
    // ...
}
```

`rememberModelInstance` (and `loadModelInstanceAsync` for imperative code) handle the
thread hop correctly — always prefer them. The unsafe APIs are `modelLoader.createModel*`
and any `materialLoader.*` call from a background thread.

**iOS has no equivalent constraint** — RealityKit's `ModelNode.load(...)` is `async` and
safely callable from any task; the renderer itself is thread-confined internally.

## Key concepts

| Concept | Android | iOS |
|---|---|---|
| Source format | Blender `.glb` export | Blender `.glb` export |
| Conversion step | none — `.glb` is native | Reality Converter → `.usdz` |
| Material cleanup | in Blender before export | Reality Composer Pro after conversion |
| App location | `assets/models/car.glb` | app bundle, `car.usdz` |
| Load API | `rememberModelInstance(loader, "models/car.glb")` | `ModelNode.load("car.usdz")` |
| Threading | Filament JNI = main thread (handled by `remember*`) | no constraint |

## See also

- [`radcli14/blender-to-realitykit`](https://github.com/radcli14/blender-to-realitykit) —
  the full Blender-side tutorial this recipe adapts.
- [`radcli14/DAE-to-RealityKit`](https://github.com/radcli14/DAE-to-RealityKit) and
  [`radcli14/ModelIO-to-RealityKit`](https://github.com/radcli14/ModelIO-to-RealityKit) —
  Eliott's converters for `.dae` and for `.stl` / `.obj` / `.ply` / `.abc` via Apple's ModelIO.
- [Model Viewer recipe](model-viewer.md) — minimal orbit-camera viewer.
- [Animated Model recipe](animated-model.md) — playing baked animations.

---

*This recipe is adapted from [@radcli14](https://github.com/radcli14)'s
[`blender-to-realitykit`](https://github.com/radcli14/blender-to-realitykit) tutorial
(MIT licensed). Thanks to Eliott Radcliffe for the careful Blender-side write-up.*
