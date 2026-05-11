import { describe, it, expect } from "vitest";
import { validateCode, formatValidationReport } from "./validator.js";

// ─── Helpers ─────────────────────────────────────────────────────────────────

function ruleIds(code: string) {
  return validateCode(code).map((i) => i.rule);
}

function hasRule(code: string, rule: string) {
  return ruleIds(code).includes(rule);
}

// ─── threading/filament-off-main-thread ──────────────────────────────────────

describe("threading/filament-off-main-thread", () => {
  const RULE = "threading/filament-off-main-thread";

  it("fires when modelLoader.createModel* used near Dispatchers.IO", () => {
    const code = `
      withContext(Dispatchers.IO) {
        val model = modelLoader.createModelInstance("models/chair.glb")
      }
    `;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("fires when Texture.Builder used near Dispatchers.Default", () => {
    const code = `
      launch(Dispatchers.Default) {
        val texture = Texture.Builder().width(4).build(engine)
      }
    `;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire without a background dispatcher", () => {
    const code = `val model = modelLoader.createModelInstance("models/chair.glb")`;
    expect(hasRule(code, RULE)).toBe(false);
  });

  it("does NOT fire when Dispatchers.Main is used", () => {
    const code = `
      withContext(Dispatchers.Main) {
        val model = modelLoader.createModelInstance("models/chair.glb")
      }
    `;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── ar/node-not-anchor ───────────────────────────────────────────────────────

describe("ar/node-not-anchor", () => {
  const RULE = "ar/node-not-anchor";

  it("fires when worldPosition is set inside an ARScene", () => {
    const code = `
      ARSceneView(engine = engine) {
        node.worldPosition = Position(0f, 0f, -1f)
      }
    `;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire for worldPosition outside ARScene", () => {
    const code = `node.worldPosition = Position(0f, 0f, -1f)`;
    expect(hasRule(code, RULE)).toBe(false);
  });

  it("does NOT fire when AnchorNode is used correctly", () => {
    const code = `
      ARSceneView(engine = engine) {
        AnchorNode(anchor = hitResult.createAnchor()) { }
      }
    `;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── composable/model-instance-null-check ────────────────────────────────────

describe("composable/model-instance-null-check", () => {
  const RULE = "composable/model-instance-null-check";

  it("fires when rememberModelInstance result used without null guard", () => {
    const code = `
      val instance = rememberModelInstance(modelLoader, "models/chair.glb")
      ModelNode(modelInstance = instance, scaleToUnits = 1f)
    `;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when guarded with ?.", () => {
    const code = `
      val instance = rememberModelInstance(modelLoader, "models/chair.glb")
      instance?.let { ModelNode(modelInstance = it) }
    `;
    expect(hasRule(code, RULE)).toBe(false);
  });

  it("does NOT fire when guarded with !!", () => {
    const code = `
      val instance = rememberModelInstance(modelLoader, "models/chair.glb")
      ModelNode(modelInstance = instance!!, scaleToUnits = 1f)
    `;
    expect(hasRule(code, RULE)).toBe(false);
  });

  it("does NOT fire when rememberModelInstance is not assigned to a var", () => {
    const code = `ModelNode(modelInstance = someOtherInstance, scaleToUnits = 1f)`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── api/light-node-trailing-lambda ──────────────────────────────────────────

describe("api/light-node-trailing-lambda", () => {
  const RULE = "api/light-node-trailing-lambda";

  it("fires on trailing lambda syntax", () => {
    const code = `LightNode(engine = engine, type = LightManager.Type.SUN) { intensity(100_000f) }`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when apply = { } is used correctly", () => {
    const code = `LightNode(engine = engine, type = LightManager.Type.SUN, apply = { intensity(100_000f) })`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── lifecycle/manual-engine-create ──────────────────────────────────────────

describe("lifecycle/manual-engine-create", () => {
  const RULE = "lifecycle/manual-engine-create";

  it("fires on Engine.create()", () => {
    const code = `val engine = Engine.create(eglContext)`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when rememberEngine() is used", () => {
    const code = `val engine = rememberEngine()`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── lifecycle/manual-engine-destroy ─────────────────────────────────────────

describe("lifecycle/manual-engine-destroy", () => {
  const RULE = "lifecycle/manual-engine-destroy";

  it("fires when engine.destroy() called alongside rememberEngine", () => {
    const code = `
      val engine = rememberEngine()
      DisposableEffect(Unit) { onDispose { engine.destroy() } }
    `;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when rememberEngine is absent (imperative code)", () => {
    const code = `engine.destroy()`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── lifecycle/texture-destroy-order ─────────────────────────────────────────

describe("lifecycle/texture-destroy-order", () => {
  const RULE = "lifecycle/texture-destroy-order";

  it("fires when texture is destroyed before material instance", () => {
    const code = `
      engine.safeDestroyTexture(texture)
      materialLoader.destroyMaterialInstance(instance)
    `;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when material instance destroyed first", () => {
    const code = `
      materialLoader.destroyMaterialInstance(instance)
      engine.safeDestroyTexture(texture)
    `;
    expect(hasRule(code, RULE)).toBe(false);
  });

  it("does NOT fire when only one of the two is present", () => {
    expect(hasRule(`engine.safeDestroyTexture(texture)`, RULE)).toBe(false);
    expect(hasRule(`materialLoader.destroyMaterialInstance(instance)`, RULE)).toBe(false);
  });
});

// ─── composable/prefer-remember-model-instance ────────────────────────────────

describe("composable/prefer-remember-model-instance", () => {
  const RULE = "composable/prefer-remember-model-instance";

  it("fires when createModelInstance is used directly", () => {
    const code = `val m = modelLoader.createModelInstance("models/chair.glb")`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire for rememberModelInstance", () => {
    const code = `val m = rememberModelInstance(modelLoader, "models/chair.glb")`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── ar/anchor-node-missing-anchor ───────────────────────────────────────────

describe("ar/anchor-node-missing-anchor", () => {
  const RULE = "ar/anchor-node-missing-anchor";

  it("fires on empty AnchorNode()", () => {
    const code = `val node = AnchorNode()`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when anchor param is provided", () => {
    const code = `AnchorNode(anchor = hitResult.createAnchor())`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── migration/old-api ────────────────────────────────────────────────────────

describe("migration/old-api", () => {
  const RULE = "migration/old-api";

  it("does NOT fire on SceneView composable (correct 3.0 name)", () => {
    expect(hasRule(`SceneView(modifier = Modifier.fillMaxSize())`, RULE)).toBe(false);
  });

  it("fires on ArSceneView composable", () => {
    expect(hasRule(`ArSceneView(modifier = Modifier.fillMaxSize())`, RULE)).toBe(true);
  });

  // ── 4.0 rename: `Scene { }` / `ARScene { }` → `SceneView { }` / `ARSceneView { }`
  it("fires on legacy Scene { } trailing-lambda call (4.0 rename — #939)", () => {
    expect(hasRule(`Scene { ModelNode(...) }`, RULE)).toBe(true);
  });

  it("fires on legacy ARScene { } trailing-lambda call (4.0 rename — #939)", () => {
    expect(hasRule(`ARScene { ModelNode(...) }`, RULE)).toBe(true);
  });

  it("does NOT false-positive on Filament's `Scene` class (followed by `.` or `(`)", () => {
    // Filament has its own `Scene` class which is used as `Scene()` or
    // `scene.addEntity(...)`. The 4.0-rename rule must NOT fire there.
    expect(hasRule(`val s = Scene()`, RULE)).toBe(false);
    expect(hasRule(`scene.addEntity(entity)`, RULE)).toBe(false);
  });

  it("fires on TransformableNode", () => {
    expect(hasRule(`val node = TransformableNode(system)`, RULE)).toBe(true);
  });

  it("fires on PlacementNode", () => {
    expect(hasRule(`val node = PlacementNode()`, RULE)).toBe(true);
  });

  it("fires on ViewRenderable", () => {
    expect(hasRule(`ViewRenderable.builder()`, RULE)).toBe(true);
  });

  it("fires on loadModelAsync", () => {
    expect(hasRule(`modelLoader.loadModelAsync("models/x.glb")`, RULE)).toBe(true);
  });

  it("does NOT fire on modern 3.0 APIs", () => {
    const code = `
      val engine = rememberEngine()
      SceneView(engine = engine) {
        rememberModelInstance(modelLoader, "models/x.glb")?.let { ModelNode(modelInstance = it) }
      }
    `;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── api/scene-missing-engine ─────────────────────────────────────────────────

describe("api/scene-missing-engine", () => {
  const RULE = "api/scene-missing-engine";

  it("fires when SceneView() has no engine nearby", () => {
    const code = `SceneView(modifier = Modifier.fillMaxSize()) { }`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when engine is provided", () => {
    const code = `
      val engine = rememberEngine()
      SceneView(modifier = Modifier.fillMaxSize(), engine = engine) { }
    `;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── api/fog-node-missing-view ────────────────────────────────────────────────

describe("api/fog-node-missing-view", () => {
  const RULE = "api/fog-node-missing-view";

  it("fires when FogNode has no view parameter", () => {
    const code = `FogNode(density = 0.1f, enabled = true)`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when view is provided", () => {
    const code = `FogNode(view = view, density = 0.1f)`;
    expect(hasRule(code, RULE)).toBe(false);
  });

  it("does NOT fire when FogNode is not used", () => {
    const code = `SceneView(engine = engine) { }`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── api/reflection-probe-missing-camera ─────────────────────────────────────

describe("api/reflection-probe-missing-camera", () => {
  const RULE = "api/reflection-probe-missing-camera";

  it("fires when ReflectionProbeNode has no cameraPosition", () => {
    const code = `ReflectionProbeNode(filamentScene = scene, environment = env)`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when cameraPosition is provided", () => {
    const code = `ReflectionProbeNode(
      filamentScene = scene,
      environment = env,
      cameraPosition = cameraPos
    )`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── api/physics-node-missing-radius ─────────────────────────────────────────

describe("api/physics-node-missing-radius", () => {
  const RULE = "api/physics-node-missing-radius";

  it("fires info when PhysicsNode has no radius", () => {
    const code = `PhysicsNode(node = sphere, mass = 1f, restitution = 0.7f)`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when radius is provided", () => {
    const code = `PhysicsNode(node = sphere, mass = 1f, radius = 0.15f)`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── api/dynamic-sky-outside-scene ───────────────────────────────────────────

describe("api/dynamic-sky-outside-scene", () => {
  const RULE = "api/dynamic-sky-outside-scene";

  it("fires when DynamicSkyNode is used without a Scene", () => {
    const code = `DynamicSkyNode(timeOfDay = 12f)`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when inside a SceneView", () => {
    const code = `
      SceneView(engine = engine) {
        DynamicSkyNode(timeOfDay = 12f)
      }
    `;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── performance/multiple-engines ─────────────────────────────────────────────

describe("performance/multiple-engines", () => {
  const RULE = "performance/multiple-engines";

  it("fires when multiple rememberEngine() calls exist", () => {
    const code = `
      val engine1 = rememberEngine()
      val engine2 = rememberEngine()
    `;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire for a single rememberEngine()", () => {
    const code = `val engine = rememberEngine()`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── threading/model-in-launched-effect ─────────────────────────────────────

describe("threading/model-in-launched-effect", () => {
  const RULE = "threading/model-in-launched-effect";

  it("fires when modelLoader is used inside LaunchedEffect", () => {
    const code = `
      LaunchedEffect(Unit) {
        val model = modelLoader.createModelInstance("models/chair.glb")
      }
    `;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when rememberModelInstance is used", () => {
    const code = `
      val instance = rememberModelInstance(modelLoader, "models/chair.glb")
    `;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── api/asset-path-leading-slash ──────────────────────────────────────────

describe("api/asset-path-leading-slash", () => {
  const RULE = "api/asset-path-leading-slash";

  it("fires when asset path starts with /", () => {
    const code = `val m = rememberModelInstance(modelLoader, "/models/chair.glb")`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire for correct relative path", () => {
    const code = `val m = rememberModelInstance(modelLoader, "models/chair.glb")`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── api/scene-zero-size ──────────────────────────────────────────────────

describe("api/scene-zero-size", () => {
  const RULE = "api/scene-zero-size";

  it("fires info when SceneView has no Modifier", () => {
    const code = `SceneView(engine = engine) { }`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when fillMaxSize is used", () => {
    const code = `SceneView(modifier = Modifier.fillMaxSize(), engine = engine) { }`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── api/remember-model-missing-loader ──────────────────────────────────────

describe("api/remember-model-missing-loader", () => {
  const RULE = "api/remember-model-missing-loader";

  it("fires when rememberModelInstance used without modelLoader", () => {
    const code = `val m = rememberModelInstance(loader, "models/chair.glb")`;
    // 'loader' not 'modelLoader' — but the rule checks for the string "modelLoader"
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does NOT fire when modelLoader is present", () => {
    const code = `
      val modelLoader = rememberModelLoader(engine)
      val m = rememberModelInstance(modelLoader, "models/chair.glb")
    `;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

// ─── formatValidationReport ───────────────────────────────────────────────────

describe("formatValidationReport", () => {
  it("returns success message when no issues", () => {
    expect(formatValidationReport([])).toContain("No issues found");
  });

  it("includes severity counts in the header", () => {
    const issues = validateCode(`
      val instance = rememberModelInstance(modelLoader, "x.glb")
      ModelNode(modelInstance = instance, scaleToUnits = 1f)
      LightNode(engine = engine) { intensity(1f) }
    `);
    const report = formatValidationReport(issues);
    expect(report).toMatch(/error/);
    expect(report).toContain("🔴");
  });

  it("includes line numbers when available", () => {
    const code = `\nval instance = rememberModelInstance(modelLoader, "x.glb")\nModelNode(modelInstance = instance, scaleToUnits = 1f)`;
    const report = formatValidationReport(validateCode(code));
    expect(report).toMatch(/line \d+/);
  });
});

// ─── Web (Kotlin/JS) validation rules ──────────────────────────────────────

describe("web/ar-not-supported", () => {
  const RULE = "web/ar-not-supported";

  it("fires when ARScene used in web code", () => {
    const code = `
import io.github.sceneview.web.SceneView
import kotlinx.browser.document
ARSceneView(modifier = Modifier.fillMaxSize())
`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does not fire for 3D-only web code", () => {
    const code = `
import io.github.sceneview.web.SceneView
import kotlinx.browser.document
SceneView.create(canvas = canvas, configure = {})
`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

describe("web/missing-start-rendering", () => {
  const RULE = "web/missing-start-rendering";

  it("fires when SceneView.create used without startRendering", () => {
    const code = `
import io.github.sceneview.web.SceneView
SceneView.create(canvas = canvas, configure = {}, onReady = { })
`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does not fire when startRendering is called", () => {
    const code = `
import io.github.sceneview.web.SceneView
SceneView.create(canvas = canvas, configure = {}, onReady = { it.startRendering() })
`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

describe("web/missing-canvas-resize", () => {
  const RULE = "web/missing-canvas-resize";

  it("fires when canvas not sized before SceneView.create", () => {
    const code = `
import io.github.sceneview.web.SceneView
val canvas = document.getElementById("c") as HTMLCanvasElement
SceneView.create(canvas = canvas, configure = {}, onReady = { it.startRendering() })
`;
    expect(hasRule(code, RULE)).toBe(true);
  });

  it("does not fire when clientWidth is used", () => {
    const code = `
import io.github.sceneview.web.SceneView
val canvas = document.getElementById("c") as HTMLCanvasElement
canvas.width = canvas.clientWidth
SceneView.create(canvas = canvas, configure = {}, onReady = { it.startRendering() })
`;
    expect(hasRule(code, RULE)).toBe(false);
  });
});

describe("web language detection", () => {
  it("detects kotlin-js from sceneview.web import", () => {
    const code = `import io.github.sceneview.web.SceneView`;
    // Should not trigger Android-only rules
    expect(hasRule(code, "threading/filament-off-main-thread")).toBe(false);
  });

  it("detects kotlin-js from kotlinx.browser import", () => {
    const code = `
import kotlinx.browser.document
val canvas = document.getElementById("c") as HTMLCanvasElement
SceneView.create(canvas = canvas, configure = {}, onReady = { it.startRendering() })
`;
    // Should trigger web rules, not Android rules
    expect(hasRule(code, "web/missing-canvas-resize")).toBe(true);
  });
});
