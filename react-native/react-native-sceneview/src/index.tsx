import React from 'react';
import {
  requireNativeComponent,
  Platform,
  type ViewStyle,
  type NativeSyntheticEvent,
  View,
  Text,
  StyleSheet,
} from 'react-native';

// ---------------------------------------------------------------------------
// Node type interfaces
// ---------------------------------------------------------------------------

/** A 3D model loaded from a .glb / .gltf file. */
export interface ModelNode {
  /** Asset path or URL to the glTF/GLB model. */
  src: string;
  /** World-space position [x, y, z]. Default: [0, 0, 0]. */
  position?: [number, number, number];
  /** Euler rotation in degrees [x, y, z]. Default: [0, 0, 0]. */
  rotation?: [number, number, number];
  /** Scale factor. Can be uniform (number) or per-axis [x, y, z]. */
  scale?: number | [number, number, number];
  /**
   * Animation name to play automatically.
   * If provided (non-null), auto-animate is enabled on the native side.
   */
  animation?: string;
}

/** A procedural geometry node (box, sphere, cylinder, plane). */
export interface GeometryNode {
  type: 'box' | 'cube' | 'sphere' | 'cylinder' | 'plane';
  size?: [number, number, number];
  position?: [number, number, number];
  rotation?: [number, number, number];
  scale?: number | [number, number, number];
  /** Hex color string, e.g. "#FF5500". */
  color?: string;
  /**
   * When `true` the material ignores all scene lighting (no PBR shading,
   * no IBL, no shadows) and renders the flat [color] straight to the
   * framebuffer. Use for HUD overlays, gizmos, axes, lines, or AR face/body
   * meshes — anywhere lighting would fight the use case. Defaults to `false`
   * (lit PBR).
   */
  unlit?: boolean;
}

/** A light source in the scene. */
export interface LightNode {
  type: 'directional' | 'point' | 'spot';
  intensity?: number;
  color?: string;
  position?: [number, number, number];
  direction?: [number, number, number];
}

// ---------------------------------------------------------------------------
// Event payloads
// ---------------------------------------------------------------------------

export interface TapEvent {
  /** World-space coordinates of the tap. */
  x: number;
  y: number;
  z: number;
  /** Name of the tapped node, if any. */
  nodeName?: string;
}

export interface PlaneDetectedEvent {
  id: string;
  type: 'horizontal' | 'vertical';
  center: [number, number, number];
  extent: [number, number];
}

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

export interface SceneViewProps {
  style?: ViewStyle;

  /** HDR environment asset path (e.g. "environments/studio.hdr"). */
  environment?: string;

  /** Model nodes to render in the scene. */
  modelNodes?: ModelNode[];
  /** Geometry nodes to render in the scene. */
  geometryNodes?: GeometryNode[];
  /** Light nodes in the scene. */
  lightNodes?: LightNode[];

  /** Enable default orbit camera controls. Default: true. */
  cameraOrbit?: boolean;

  /** Called when the user taps inside the scene. */
  onTap?: (event: NativeSyntheticEvent<TapEvent>) => void;
}

export interface ARSceneViewProps extends SceneViewProps {
  /** Enable plane detection. Default: true. */
  planeDetection?: boolean;

  /** Enable depth occlusion (ARCore Depth API / LiDAR). Default: false. */
  depthOcclusion?: boolean;

  /** Enable instant placement (approximate hit-test before tracking). Default: false. */
  instantPlacement?: boolean;

  /** Called when a new plane is detected. */
  onPlaneDetected?: (event: NativeSyntheticEvent<PlaneDetectedEvent>) => void;
}

// ---------------------------------------------------------------------------
// Native components (only available on Android and iOS)
// ---------------------------------------------------------------------------

const isNativeAvailable = Platform.OS === 'android' || Platform.OS === 'ios';

const NativeSceneView = isNativeAvailable
  ? requireNativeComponent<SceneViewProps>('RNSceneView')
  : null;

const NativeARSceneView = isNativeAvailable
  ? requireNativeComponent<ARSceneViewProps>('RNARSceneView')
  : null;

// ---------------------------------------------------------------------------
// Fallback for unsupported platforms
// ---------------------------------------------------------------------------

const UnsupportedView: React.FC<{ name: string }> = ({ name }) => (
  <View style={fallbackStyles.container}>
    <Text style={fallbackStyles.text}>{name} is not supported on this platform</Text>
  </View>
);

const fallbackStyles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#1a1a2e',
  },
  text: {
    color: '#aaa',
    fontSize: 16,
  },
});

// ---------------------------------------------------------------------------
// Public components
// ---------------------------------------------------------------------------

/**
 * A 3D scene rendered with Filament (Android) or RealityKit (iOS).
 *
 * ```tsx
 * <SceneView
 *   environment="environments/studio.hdr"
 *   modelNodes={[{ src: "models/robot.glb" }]}
 * />
 * ```
 */
export const SceneView: React.FC<SceneViewProps> = (props) => {
  if (!NativeSceneView) {
    return <UnsupportedView name="SceneView" />;
  }
  return <NativeSceneView {...props} />;
};

/**
 * An augmented-reality scene using ARCore (Android) or ARKit (iOS).
 *
 * ```tsx
 * <ARSceneView
 *   planeDetection
 *   modelNodes={[{ src: "models/chair.glb", position: [0, 0, -1] }]}
 * />
 * ```
 */
export const ARSceneView: React.FC<ARSceneViewProps> = (props) => {
  if (!NativeARSceneView) {
    return <UnsupportedView name="ARSceneView" />;
  }
  return <NativeARSceneView {...props} />;
};
