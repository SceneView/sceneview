/**
 * explain-api.ts
 *
 * Explains specific SceneView APIs with examples, common mistakes, and tips.
 */

export interface APIExplanation {
  name: string;
  summary: string;
  signature: string;
  platform: string;
  example: string;
  commonMistakes: string[];
  tips: string[];
  relatedAPIs: string[];
}

const API_EXPLANATIONS: Record<string, APIExplanation> = {
  rememberengine: {
    name: "rememberEngine()",
    summary: "Creates and remembers a Filament Engine tied to the Compose lifecycle. Automatically destroyed when the composable leaves the composition.",
    signature: "@Composable fun rememberEngine(): Engine",
    platform: "Android",
    example: `val engine = rememberEngine()
SceneView(engine = engine, modifier = Modifier.fillMaxSize()) {
    // Your 3D content
}`,
    commonMistakes: [
      "Creating multiple engines in different composables — wastes GPU memory. Use ONE engine per app.",
      "Calling engine.destroy() manually — rememberEngine handles this. Double-destroy causes SIGABRT.",
      "Using Engine.create() in a composable — use rememberEngine() instead for lifecycle safety.",
    ],
    tips: [
      "Create the engine at the top-level composable and pass it down to all SceneView composables.",
      "The engine is the most expensive resource in SceneView — reuse it everywhere.",
    ],
    relatedAPIs: ["rememberModelLoader()", "rememberEnvironmentLoader()", "rememberMaterialLoader()"],
  },

  remembermodelinstance: {
    name: "rememberModelInstance()",
    summary: "Asynchronously loads a GLB/glTF model and returns a ModelInstance. Returns null while loading and if the file fails to load.",
    signature: "@Composable fun rememberModelInstance(modelLoader: ModelLoader, path: String): ModelInstance?",
    platform: "Android",
    example: `val engine = rememberEngine()
val modelLoader = rememberModelLoader(engine)
val modelInstance = rememberModelInstance(modelLoader, "models/chair.glb")

SceneView(engine = engine) {
    modelInstance?.let { instance ->
        ModelNode(modelInstance = instance, scaleToUnits = 1.0f)
    }
}`,
    commonMistakes: [
      "Not null-checking the result — it's null while loading. Always use ?.let or if != null.",
      "Using modelLoader.createModelInstance() directly — blocks the main thread. Use rememberModelInstance.",
      "Putting a leading slash in the path — use 'models/file.glb' not '/models/file.glb'.",
      "Loading .gltf multi-file format — use .glb (single binary) to avoid missing texture references.",
    ],
    tips: [
      "Show a loading indicator while the model instance is null.",
      "The path is relative to src/main/assets/.",
      "For imperative (non-composable) loading, use modelLoader.loadModelInstanceAsync().",
    ],
    relatedAPIs: ["ModelNode()", "rememberModelLoader()", "ModelLoader"],
  },

  modelnode: {
    name: "ModelNode()",
    summary: "Displays a 3D model in the scene. Requires a ModelInstance from rememberModelInstance or loadModelInstanceAsync.",
    signature: "@Composable fun SceneScope.ModelNode(modelInstance: ModelInstance, scaleToUnits: Float? = null, centerOrigin: Position? = null, autoAnimate: Boolean = false, isEditable: Boolean = false, ...)",
    platform: "Android",
    example: `modelInstance?.let { instance ->
    ModelNode(
        modelInstance = instance,
        scaleToUnits = 1.0f,
        centerOrigin = Position(0f, 0f, 0f),
        autoAnimate = true,
        isEditable = true
    )
}`,
    commonMistakes: [
      "Passing a null modelInstance without null-checking — will throw at runtime.",
      "Forgetting scaleToUnits — models can be enormous or microscopic depending on their original units.",
      "Not having a light in the scene — model appears black without illumination.",
    ],
    tips: [
      "scaleToUnits normalizes the model to fit within a unit sphere of the given size.",
      "centerOrigin moves the model's pivot point to the specified position.",
      "isEditable = true enables built-in pinch-to-scale and drag-to-rotate.",
      "autoAnimate = true plays the first embedded animation in a loop.",
    ],
    relatedAPIs: ["rememberModelInstance()", "LightNode()", "AnchorNode()"],
  },

  lightnode: {
    name: "LightNode()",
    summary: "Adds a light source to the scene. Configuration is via the named parameter `apply`, NOT a trailing lambda.",
    signature: "@Composable fun SceneScope.LightNode(engine: Engine, type: LightManager.Type, apply: LightManager.Builder.() -> Unit = {}, ...)",
    platform: "Android",
    example: `LightNode(
    engine = engine,
    type = LightManager.Type.DIRECTIONAL,
    apply = {
        intensity(100_000f)
        castShadows(true)
        direction(0f, -1f, -0.5f)
    }
)`,
    commonMistakes: [
      "Using a trailing lambda: LightNode(engine, type) { ... } — the block is SILENTLY IGNORED. Must use apply = { ... }.",
      "Forgetting to add any light — PBR models appear completely black without light.",
      "Setting intensity too low — directional/sun lights typically need 100,000+ lux.",
    ],
    tips: [
      "Light types: DIRECTIONAL (sun), POINT (lamp), SPOT (flashlight), SUN (physically-based sun).",
      "For outdoor scenes, use Type.SUN with ~110,000 lux intensity.",
      "Each shadow-casting light adds a GPU render pass. Limit to 1-2 on mobile.",
    ],
    relatedAPIs: ["SceneView()", "rememberMainLightNode()", "DynamicSkyNode()"],
  },

  arscene: {
    name: "ARSceneView()",
    summary: "Composable that renders an augmented reality view with camera feed, plane detection, and light estimation.",
    signature: "@Composable fun ARSceneView(modifier: Modifier, engine: Engine, modelLoader: ModelLoader? = null, planeRenderer: Boolean = true, sessionConfiguration: (Session, Config) -> Unit = { _, _ -> }, onTouchEvent: (MotionEvent, HitResult?) -> Boolean = { _, _ -> false }, content: @Composable ARSceneScope.() -> Unit)",
    platform: "Android",
    example: `var anchor by remember { mutableStateOf<Anchor?>(null) }

ARSceneView(
    modifier = Modifier.fillMaxSize(),
    engine = engine,
    modelLoader = modelLoader,
    planeRenderer = true,
    sessionConfiguration = { session, config ->
        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
    },
    onTouchEvent = { event, hitResult ->
        if (event.action == MotionEvent.ACTION_UP && hitResult != null) {
            anchor = hitResult.createAnchor()
        }
        true
    }
) {
    anchor?.let { a ->
        AnchorNode(anchor = a) {
            // Place content at the anchor
        }
    }
}`,
    commonMistakes: [
      "Not requesting camera permission at runtime — AR crashes without it.",
      "Missing AndroidManifest entries — need CAMERA permission and com.google.ar.core meta-data.",
      "Setting worldPosition on nodes instead of using AnchorNode — causes drift.",
      "Testing on emulator — ARCore support on emulators is limited; use a real device.",
      "Enabling Geospatial / Streetscape / Cloud Anchors without the ARCore Cloud API key wired into the manifest (com.google.android.ar.API_KEY) — backend handshake silently returns no data. Also requires ACCESS_FINE_LOCATION at runtime BEFORE Session.configure(GeospatialMode.ENABLED).",
    ],
    tips: [
      "Always use AnchorNode for placing objects in AR — anchors compensate for tracking drift.",
      "planeRenderer = true shows plane detection visualization to guide the user.",
      "ENVIRONMENTAL_HDR is the most realistic lighting mode for AR objects.",
      "Use isEditable = true on ModelNode for pinch-to-scale after placement.",
    ],
    relatedAPIs: ["SceneView()", "AnchorNode()", "HitResultNode()", "rememberModelInstance()"],
  },

  scene: {
    name: "SceneView()",
    summary: "Composable that renders a 3D viewport with Filament. The main entry point for 3D rendering in SceneView.",
    signature: "@Composable fun SceneView(modifier: Modifier, engine: Engine, modelLoader: ModelLoader? = null, environment: Environment? = null, cameraManipulator: CameraManipulator? = null, mainLightNode: LightNode? = null, onFrame: (Long) -> Unit = {}, content: @Composable SceneScope.() -> Unit)",
    platform: "Android",
    example: `val engine = rememberEngine()
val modelLoader = rememberModelLoader(engine)

SceneView(
    modifier = Modifier.fillMaxSize(),
    engine = engine,
    modelLoader = modelLoader,
    cameraManipulator = rememberCameraManipulator()
) {
    // Declare nodes as composables here
}`,
    commonMistakes: [
      "Missing Modifier.fillMaxSize() — SceneView may have zero size and be invisible.",
      "Missing engine parameter — required in SceneView 3.0+.",
      "Creating nodes imperatively instead of declaratively — use composable DSL inside SceneView { }.",
    ],
    tips: [
      "Add cameraManipulator for orbit controls (drag to rotate, pinch to zoom).",
      "Use environment for HDR skybox and reflections.",
      "Use mainLightNode for the primary light source.",
      "onFrame callback runs every frame on the main thread — safe for Filament calls.",
    ],
    relatedAPIs: ["ARSceneView()", "rememberEngine()", "ModelNode()", "LightNode()"],
  },

  anchornode: {
    name: "AnchorNode()",
    summary: "An AR node anchored to a real-world position. Compensates for ARCore coordinate system changes during tracking.",
    signature: "@Composable fun ARSceneScope.AnchorNode(anchor: Anchor, content: @Composable () -> Unit)",
    platform: "Android",
    example: `anchor?.let { a ->
    AnchorNode(anchor = a) {
        modelInstance?.let { instance ->
            ModelNode(modelInstance = instance, scaleToUnits = 0.5f)
        }
    }
}`,
    commonMistakes: [
      "Using AnchorNode() without an anchor parameter — requires a valid Anchor from hitResult.createAnchor().",
      "Setting worldPosition manually instead of using AnchorNode — causes drift as ARCore remaps coordinates.",
    ],
    tips: [
      "Create anchors from hit results: hitResult.createAnchor().",
      "Nest child nodes inside AnchorNode's content block — they inherit the anchor's position.",
      "For persistent anchors across sessions, use CloudAnchorNode.",
    ],
    relatedAPIs: ["ARSceneView()", "HitResultNode()", "ModelNode()"],
  },
};

export function explainAPI(apiName: string): APIExplanation | null {
  const key = apiName.toLowerCase().replace(/[^a-z]/g, "");
  return API_EXPLANATIONS[key] || null;
}

export function listExplainableAPIs(): string[] {
  return Object.values(API_EXPLANATIONS).map((e) => e.name);
}

export function formatAPIExplanation(explanation: APIExplanation): string {
  const parts: string[] = [
    `## ${explanation.name}`,
    ``,
    `**Platform:** ${explanation.platform}`,
    ``,
    `${explanation.summary}`,
    ``,
    `### Signature`,
    ``,
    "```kotlin",
    explanation.signature,
    "```",
    ``,
    `### Example`,
    ``,
    "```kotlin",
    explanation.example,
    "```",
    ``,
  ];

  if (explanation.commonMistakes.length > 0) {
    parts.push(`### Common Mistakes`);
    explanation.commonMistakes.forEach((m, i) => parts.push(`${i + 1}. ${m}`));
    parts.push(``);
  }

  if (explanation.tips.length > 0) {
    parts.push(`### Tips`);
    explanation.tips.forEach((t, i) => parts.push(`${i + 1}. ${t}`));
    parts.push(``);
  }

  if (explanation.relatedAPIs.length > 0) {
    parts.push(`### Related APIs`);
    parts.push(explanation.relatedAPIs.map((a) => `\`${a}\``).join(", "));
  }

  return parts.join("\n");
}
