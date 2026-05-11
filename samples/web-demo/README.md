# SceneView Web Demo

Browser-based 3D viewer using SceneView.js (Filament.js WASM engine).

## Features

- **Model Gallery** — 40 curated CDN models across 5 categories (Showcase, Vehicles, Animated, Characters, Objects)
- **Sketchfab Search** — search and browse downloadable 3D models from Sketchfab
- **Geometry Showcase** — create cubes, spheres, cylinders, and planes with color pickers and size sliders
- **Settings Panel** — quality (low/medium/high), bloom toggle, auto-rotate toggle, background color
- **WebXR AR/VR** — enter immersive AR or VR sessions (when browser supports WebXR)
- **Tab Navigation** — Models, Geometry, and Settings tabs
- **Responsive dark theme** — works on desktop and mobile
- **SDK version badge** — displays SceneView v4.0.9

## Run

Open `src/jsMain/resources/index.html` directly in a browser, or:

```bash
./gradlew :samples:web-demo:jsBrowserRun
```

## Architecture

- `index.html` — self-contained single-file app (HTML + CSS + JS)
- Uses `SceneView.js` from CDN (`sceneview.modelViewer()`, `createBox()`, `createSphere()`, etc.)
- Filament.js WASM engine loaded from CDN
- Sketchfab API: `GET /v3/search?type=models&downloadable=true&q={query}`
- CDN models: `https://cdn.jsdelivr.net/gh/sceneview/sceneview@main/assets/models/glb/`

## Requirements

- WebGL2-compatible browser (~95% coverage)
- WebXR for AR/VR where available (Chrome Android, Quest Browser, etc.)
