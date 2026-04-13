# SceneView Best Practices and Troubleshooting

## Performance Optimization

### Model Budgets

For smooth 60fps rendering on mid-range devices:

- **Triangle count:** Keep individual models under 100K triangles. Total scene budget: 300K-500K triangles.
- **Texture size:** Use 1024x1024 for most models, 2048x2048 for hero assets. Avoid 4096x4096 on mobile.
- **Texture compression:** Use KTX2 with Basis Universal compression for Android. USDZ handles compression natively on iOS.
- **Model file size:** GLB files under 10MB load in under 2 seconds on mid-range devices. For larger assets, show a loading indicator.

### Level of Detail (LOD)

Use `scaleToUnits` to control apparent size and swap model variants based on camera distance:

```kotlin
val isClose = remember(cameraDistance) { cameraDistance < 3f }
val modelPath = if (isClose) "models/car_high.glb" else "models/car_low.glb"
val model = rememberModelInstance(modelLoader, modelPath)
```

### Scene Optimization Tips

- **Limit light count:** 1 directional + 2-3 point/spot lights maximum for mobile
- **Shadow budget:** Enable shadows only on the main light. Disable `isShadowCaster` on small/distant objects
- **Environment maps:** Use smaller HDR maps (1K or 2K). Avoid 4K+ for IBL on mobile
- **Geometry nodes:** CubeNode, SphereNode, etc. are lightweight but avoid hundreds of them. Batch similar geometry into a single model file instead
- **Dispose unused resources:** SceneView's `remember*` helpers handle cleanup automatically, but if creating resources imperatively, call `destroy()` when done

### Post-Processing Performance

Post-processing effects have GPU cost. Enable selectively:

```kotlin
// Moderate cost: bloom, vignette
view.bloomOptions = view.bloomOptions.apply { enabled = true; strength = 0.1f }

// Higher cost: SSAO, depth of field
view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply { enabled = true }
view.depthOfFieldOptions = view.depthOfFieldOptions.apply { enabled = true }
```

Disable SSAO and DoF on low-end devices. Use `Build.MODEL` or runtime frame-time measurement to adapt.

---

## Common Mistakes and Fixes

### 1. Not handling null from rememberModelInstance

**Wrong:**
```kotlin
SceneView {
    val model = rememberModelInstance(modelLoader, "models/helmet.glb")
    ModelNode(modelInstance = model!!)  // CRASH: model is null while loading
}
```

**Correct:**
```kotlin
SceneView {
    rememberModelInstance(modelLoader, "models/helmet.glb")?.let { model ->
        ModelNode(modelInstance = model, scaleToUnits = 1.0f)
    }
}
```

### 2. Using LightNode apply as a trailing lambda

**Wrong:**
```kotlin
LightNode(type = LightManager.Type.SUN) {  // ERROR: this is the content block, not apply
    intensity(100_000f)
}
```

**Correct:**
```kotlin
LightNode(
    type = LightManager.Type.SUN,
    apply = { intensity(100_000f); castShadows(true) }
)
```

### 3. Calling Filament APIs on background thread

**Wrong:**
```kotlin
LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
        val model = modelLoader.createModelInstance("models/helmet.glb")  // CRASH
    }
}
```

**Correct:**
```kotlin
LaunchedEffect(Unit) {
    val model = modelLoader.loadModelInstance("models/helmet.glb")  // suspend, thread-safe
}
// Or in composable scope:
val model = rememberModelInstance(modelLoader, "models/helmet.glb")
```

### 4. Wrong imports for math types

SceneView uses its own math types, not android.graphics or compose types:

```kotlin
import io.github.sceneview.math.Position   // NOT android.graphics.PointF
import io.github.sceneview.math.Rotation   // NOT Float3 from other libraries
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.math.Color      // Float4, NOT android.graphics.Color for materials
```

For `materialLoader.createColorInstance`, use `androidx.compose.ui.graphics.Color` or Filament `Color`.

### 5. Forgetting environment and light

A black scene usually means no environment and no light:

```kotlin
SceneView(
    environment = rememberEnvironment(environmentLoader) {
        environmentLoader.createHDREnvironment("environments/sky_2k.hdr")
            ?: createEnvironment(environmentLoader)
    },
    mainLightNode = rememberMainLightNode(engine) { intensity = 100_000f }
) { ... }
```

### 6. Creating materials outside remember

**Wrong:**
```kotlin
SceneView {
    // Creates a new MaterialInstance EVERY recomposition -- memory leak!
    val mat = materialLoader.createColorInstance(Color.Red)
    CubeNode(materialInstance = mat)
}
```

**Correct:**
```kotlin
SceneView {
    val mat = remember(materialLoader) {
        materialLoader.createColorInstance(Color.Red, metallic = 0f, roughness = 0.6f)
    }
    CubeNode(materialInstance = mat)
}
```

---

## AR Best Practices

### Camera Permission

Always handle the CAMERA permission before showing ARSceneView. Use the `onSessionFailed` callback for graceful degradation:

```kotlin
ARSceneView(
    onSessionFailed = { exception ->
        // Show fallback UI or permission request
        showARUnavailableMessage = true
    }
)
```

### Plane Detection

- Enable `planeRenderer = true` during initial placement phase so users can see detected surfaces
- Disable it after placement for a cleaner look: `planeRenderer = anchor != null`
- Use horizontal planes for furniture/objects, vertical for wall art/posters

### Lighting Estimation

Always enable environmental HDR for realistic lighting on placed objects:

```kotlin
sessionConfiguration = { session, config ->
    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
}
```

This adjusts the virtual lighting to match the real environment.

### Depth Mode

Enable automatic depth for occlusion (virtual objects hidden behind real ones):

```kotlin
config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
    Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
```

### AR Tone Mapping

Use `rememberARView(engine)` instead of `rememberView(engine)` for AR scenes. It uses a linear tone mapper that matches the camera feed brightness.

### Anchor Management

- Create anchors sparingly -- each anchor has tracking overhead
- Remove anchors when no longer needed: `anchor.detach()`
- Use `onTrackingStateChanged` to handle tracking loss gracefully

---

## Memory Management

### Lifecycle

SceneView's `remember*` helpers automatically manage lifecycle. Resources are created on first composition and destroyed on disposal.

### Manual Resource Cleanup

If creating resources imperatively (outside composables), destroy them explicitly:

```kotlin
val model = modelLoader.createModelInstance("models/helmet.glb")
// ... use model ...
model.destroy()  // when done
```

### Large Scene Tips

- Use `DisposableEffect` for imperative resources tied to composable lifecycle
- Avoid loading more than 5-10 models simultaneously
- For model galleries, load/unload models as the user scrolls
- Monitor memory with Android Studio Profiler -- Filament textures use GPU memory not tracked by JVM heap

---

## Troubleshooting: Top 10 Common Errors

| # | Problem | Cause | Fix |
|---|---------|-------|-----|
| 1 | Model not showing | `rememberModelInstance` returns null | Always null-check: `model?.let { ModelNode(...) }` |
| 2 | Black screen | No environment or no light | Add `mainLightNode` and `environment` parameters |
| 3 | Crash on background thread | Filament JNI on wrong thread | Use `rememberModelInstance` or `Dispatchers.Main` |
| 4 | AR not starting | Missing CAMERA permission or ARCore not installed | Handle `onSessionFailed`, check `ArCoreApk.checkAvailability()` |
| 5 | Model too big/small | Model units mismatch | Use `scaleToUnits` parameter (value in meters) |
| 6 | Oversaturated AR camera | Wrong tone mapper for AR | Use `rememberARView(engine)` which applies linear tone mapping |
| 7 | Crash on empty bounding box | Filament 1.70+ enforcement | Update to latest SceneView which auto-sanitizes |
| 8 | Material crash on dispose | Entity still in scene during cleanup | SceneView handles cleanup order automatically; update library |
| 9 | LightNode not working | Using `apply` as trailing lambda | Use `apply = { ... }` as a named parameter |
| 10 | Gesture not detected | Node not touchable | Set `isTouchable = true` and/or `isEditable = true` on the node |

---

## Migration from Sceneform / SceneView v2

### Key Changes in v3

1. **Declarative API:** No more `arSceneView.scene.addChild(node)`. Declare nodes as composables inside `SceneView { }` or `ARSceneView { }`.

2. **Compose-first:** SceneView v3 is built for Jetpack Compose. The old XML/View-based API is gone.

3. **Resource loading:** Replace `ModelRenderable.builder()` with `rememberModelInstance(modelLoader, path)`.

4. **Node creation:** Replace `TransformableNode(arFragment.transformationSystem)` with `ModelNode(modelInstance = ..., isEditable = true)`.

5. **AR session:** Replace `ArFragment` with `ARSceneView(sessionConfiguration = { ... })`.

6. **Anchor placement:**
   - Old: `arFragment.setOnTapArPlaneListener { hitResult, plane, event -> ... }`
   - New: `onTouchEvent = { event, hitResult -> ... }` + `AnchorNode(anchor) { ... }`

7. **Animation:** Replace `ModelAnimator.animate()` with `ModelNode(autoAnimate = true, animationName = "Walk")`.

8. **Materials:** Replace `MaterialFactory.makeOpaqueWithColor()` with `materialLoader.createColorInstance(color, metallic, roughness)`.

### Migration Checklist

- [ ] Replace `ArFragment` / `ArSceneView` XML with `ARSceneView` composable
- [ ] Replace `ModelRenderable.builder()` with `rememberModelInstance`
- [ ] Replace `scene.addChild(node)` with composable node declarations
- [ ] Replace `TransformableNode` with `ModelNode(isEditable = true)`
- [ ] Replace `ArFragment.setOnTapArPlaneListener` with `onTouchEvent`
- [ ] Replace `MaterialFactory` with `MaterialLoader`
- [ ] Replace `ViewRenderable` with `ViewNode`
- [ ] Update Gradle dependency to `io.github.sceneview:arsceneview:4.0.0`
- [ ] Add `rememberEngine()` and pass engine to loaders

### Dependency Change

```kotlin
// Old (Sceneform / SceneView v2)
implementation("io.github.sceneview:sceneview:2.x.x")

// New (SceneView v3)
implementation("io.github.sceneview:sceneview:4.0.0")   // 3D only
implementation("io.github.sceneview:arsceneview:4.0.0") // AR + 3D
```
