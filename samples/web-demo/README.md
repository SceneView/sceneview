# SceneView Web Demo

Browser-based 3D viewer using SceneView.js (Filament.js WASM engine).

## Features

The demo has four tabs in the top tab bar:

- **Models** — browse 15 curated models across 5 categories (Showcase,
  Vehicles, Animated, Characters, Objects), or switch the source toggle to
  **Sketchfab Search** to search downloadable 3D models from Sketchfab. The
  curated GLBs are self-hosted (bundled in the demo distribution), not loaded
  from a third-party CDN.
- **Geometry** — create cubes, spheres, cylinders, and planes with color
  pickers, size sliders, and a per-shape `KHR_materials_unlit` toggle.
- **Physics** — a chaotic **Double Pendulum** simulation whose integrator math
  mirrors the shared `DoublePendulum` in `sceneview-core` (KMP). Sliders tune
  the upper/lower link lengths and gravity; **Reset & drop** re-seeds the run.
- **Settings** — rendering quality (low/medium/high), bloom toggle, auto-rotate
  toggle, and background color.

Also:

- **WebXR AR/VR** — enter immersive AR or VR sessions (when the browser
  supports WebXR).
- **Deep linking** — a `#double-pendulum` (or `#physics`) URL fragment opens
  the Physics tab directly, mirroring the `sceneview://demo/double-pendulum`
  deep link the Android and iOS demos honour.
- **Responsive dark theme** — works on desktop and mobile.
- **Update snackbar** — surfaces a "Reload to update" prompt when
  `sceneview.github.io/version.json` reports a newer build.

## Run

Open `src/jsMain/resources/index.html` directly in a browser, or:

```bash
./gradlew :samples:web-demo:jsBrowserRun
```

## Architecture

- `index.html` — self-contained single-file app (HTML + CSS + inline JS). This
  is the shipped demo; it loads `SceneView.js` from the CDN.
- Uses `SceneView.js` from CDN (`SceneView.modelViewer()`, `createBox()`,
  `setQuality()`, `setBloom()`, `setBackgroundColor()`, etc.).
- Filament.js WASM engine loaded from CDN.
- Sketchfab API: `GET /v3/search?type=models&downloadable=true&q={query}`.
- Curated models: self-hosted GLBs under `src/jsMain/resources/models/`, loaded
  from the relative `models/` path. They are copied verbatim into the
  `jsBrowserDistribution` output and deployed alongside `index.html`, so the
  demo never depends on a third-party CDN for its assets (issue #1573).
- `src/jsMain/kotlin/.../web/Main.kt` is an alternative Kotlin/JS entry point
  built against the `sceneview-web` module; the shipped page uses the inline JS.

## Tests

Playwright tests run headless against the shipped `index.html` (served by
`http-server` — no Gradle build needed for the inline-JS path):

- `tests/render.spec.ts` — load + branding + tab-regression smoke layer.
- `tests/catalog.spec.ts` — full per-tab / per-demo QA: exercises every Models
  / Geometry / Physics / Settings demo, drives camera orbit + zoom, samples the
  WebGL canvas to assert a non-blank render, and asserts no console errors or
  unhandled rejections (slice 3 of the device-QA harness, issue #1564).
- `tests/helpers.ts` — shared canvas-sampling, console-capture and interaction
  helpers.

Run with:

```bash
npx playwright test
```

The run emits `test-results/web-qa-summary.json` — a flat machine-readable
result consumed by the autonomous device-QA orchestrator runner (issue #1566).

## Requirements

- WebGL2-compatible browser (~95% coverage)
- WebXR for AR/VR where available (Chrome Android, Quest Browser, etc.)
