import React from 'react';
import { type ViewStyle, type NativeSyntheticEvent } from 'react-native';
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
/**
 * Camera interaction mode for a {@link SceneView} (v4.3.0).
 *
 * Platform support:
 * - **iOS**: all three modes are wired through `.cameraControls(_:)`.
 * - **Android**: `'orbit'` is the default; `'pan'` / `'firstPerson'` fall
 *   back to orbit (the per-mode switch is an iOS-first v4.3.0 addition —
 *   the Android side is tracked in issue #1051).
 */
export type CameraControlMode = 'orbit' | 'pan' | 'firstPerson';
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
    /**
     * Camera interaction mode (v4.3.0). Default: `'orbit'`.
     *
     * `'pan'` and `'firstPerson'` are iOS-only; on Android they fall back to
     * orbit. See {@link CameraControlMode}.
     */
    cameraControlMode?: CameraControlMode;
    /**
     * Whether the scene auto-centres its content on the first stable frame
     * (v4.3.0). Default: `true`. iOS-first; the Android side is tracked in
     * issue #1051.
     */
    autoCenterContent?: boolean;
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
export declare const SceneView: React.FC<SceneViewProps>;
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
export declare const ARSceneView: React.FC<ARSceneViewProps>;
/**
 * Records an AR session to a video file (v4.3.0).
 *
 * iOS port of SceneViewSwift's `ARRecorder` — record-only via ReplayKit,
 * producing a QuickTime `.mov`.
 *
 * ```ts
 * const recorder = new ARRecorder();
 * await recorder.start();
 * // ... later ...
 * const path = await recorder.stop();
 * await recorder.saveToPhotoLibrary(path);
 * ```
 *
 * Platform support:
 * - **iOS**: full support via `RPScreenRecorder`.
 * - **Android**: not yet bridged. ARCore session recording produces a
 *   replayable dataset (not a video) and needs deeper `Session`/`Frame`
 *   access than the Fabric bridge exposes. Every method rejects with an
 *   error on Android until issue #1051 lands the Android side.
 */
export declare class ARRecorder {
    /** `true` when {@link ARRecorder} is supported on the current platform. */
    static get isSupported(): boolean;
    private rejectUnsupported;
    /** Starts an AR session recording. */
    start(): Promise<void>;
    /**
     * Stops the in-progress recording and resolves with the path of the
     * written `.mov` file.
     *
     * @param outputPath optional destination path; when omitted the native
     *   side picks a temp location.
     */
    stop(outputPath?: string): Promise<string>;
    /** Saves a recorded `.mov` file to the device's photo library (iOS). */
    saveToPhotoLibrary(movPath: string): Promise<void>;
}
//# sourceMappingURL=index.d.ts.map