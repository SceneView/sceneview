# react-native-sceneview

[![npm version](https://img.shields.io/npm/v/react-native-sceneview.svg)](https://www.npmjs.com/package/react-native-sceneview)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![GitHub](https://img.shields.io/badge/GitHub-sceneview%2Fsceneview-black)](https://github.com/sceneview/sceneview)

React Native bindings for [SceneView](https://sceneview.github.io) — 3D and AR scenes powered by Filament (Android) and RealityKit (iOS).

> **Status:** Alpha — 3D model loading works on both platforms. AR scene is functional on Android.

## Features

- Load and display 3D models (GLB/GLTF) using native renderers
- AR scenes with plane detection — ARCore (Android) and ARKit (iOS)
- HDR environment lighting
- Orbit camera controls
- TypeScript types for all props and events
- Fallback message on unsupported platforms (web, etc.)

## Installation

```sh
npm install react-native-sceneview
```

### iOS

```sh
cd ios && pod install
```

Requires iOS 17+ and Xcode 15+. The host app must also add `SceneViewSwift` via Swift Package Manager:
- URL: `https://github.com/sceneview/SceneViewSwift`
- Version: `4.0.0`

### Android

Requires `minSdk 24`. SceneView is published to Maven Central — no extra repository needed:

```groovy
// android/app/build.gradle
android {
    defaultConfig {
        minSdkVersion 24
    }
}
```

## Usage

### 3D Scene

```tsx
import { SceneView } from 'react-native-sceneview';

<SceneView
  style={{ flex: 1 }}
  environment="environments/studio_small.hdr"
  modelNodes={[{ src: 'models/damaged_helmet.glb', position: [0, 0, -2] }]}
  cameraOrbit
/>
```

### AR Scene

```tsx
import { ARSceneView } from 'react-native-sceneview';

<ARSceneView
  style={{ flex: 1 }}
  planeDetection
  modelNodes={[{ src: 'models/chair.glb', position: [0, 0, -1] }]}
/>
```

### Props — SceneView

| Prop             | Type            | Default | Description                              |
|------------------|-----------------|---------|------------------------------------------|
| `style`          | `ViewStyle`     | —       | Standard React Native style              |
| `environment`    | `string`        | —       | HDR environment asset path               |
| `modelNodes`     | `ModelNode[]`   | `[]`    | Models to render                         |
| `geometryNodes`  | `GeometryNode[]`| `[]`    | Geometry nodes (forward-compatible)      |
| `lightNodes`     | `LightNode[]`   | `[]`    | Light nodes (forward-compatible)         |
| `cameraOrbit`    | `boolean`       | `true`  | Enable orbit camera controls             |
| `onTap`          | `function`      | —       | Tap callback (event pending)             |

### Props — ARSceneView (extends SceneView)

| Prop                | Type       | Default | Description                               |
|---------------------|------------|---------|-------------------------------------------|
| `planeDetection`    | `boolean`  | `true`  | Enable plane detection                    |
| `depthOcclusion`    | `boolean`  | `false` | Enable depth occlusion (Depth API/LiDAR)  |
| `instantPlacement`  | `boolean`  | `false` | Enable instant placement                  |
| `onPlaneDetected`   | `function` | —       | Callback when a new plane is detected     |

### ModelNode interface

```ts
interface ModelNode {
  src: string;                             // Asset path or URL to GLB/GLTF
  position?: [number, number, number];     // World-space [x, y, z]
  rotation?: [number, number, number];     // Euler degrees [x, y, z]
  scale?: number | [number, number, number]; // Uniform or per-axis scale
  animation?: string;                      // Animation name to auto-play
}
```

## Architecture

```
React Native JS
    |
    v
requireNativeComponent('RNSceneView' / 'RNARSceneView')
    |
    +---> Android: ViewManager -> ComposeView -> SceneView { } / ARSceneView { }
    |                              (Filament, SceneView SDK 4.0.0)
    |
    +---> iOS: RCTViewManager -> UIHostingController -> SceneView / ARSceneView
                                  (RealityKit, SceneViewSwift 4.0.0)
```

Props are mapped from the React Native bridge to native view parameters on each platform.

## Limitations

- Geometry and light node props are defined in types but not yet rendered natively
- `onTap` and `onPlaneDetected` event callbacks are declared but events are not yet dispatched
- Scale prop on `ModelNode` is parsed as a uniform float (per-axis array not yet supported)
- Only Android and iOS are supported; other platforms render a fallback message

## Contributing

See [CONTRIBUTING.md](https://github.com/sceneview/sceneview/blob/main/.github/CONTRIBUTING.md).

## License

Apache-2.0 — see [LICENSE](LICENSE) for details.
