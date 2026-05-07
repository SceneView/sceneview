/// Flutter plugin for SceneView -- 3D and AR scenes.
///
/// Uses native platform views:
/// - Android: SceneView (Filament renderer via Jetpack Compose)
/// - iOS: SceneViewSwift (RealityKit renderer via SwiftUI)
library sceneview_flutter;

import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';

// ---------------------------------------------------------------------------
// Data classes
// ---------------------------------------------------------------------------

/// Describes a 3D model to load into the scene.
class ModelNode {
  /// Asset path or URL to the glTF/GLB model file.
  final String modelPath;

  /// X position in world space.
  final double x;

  /// Y position in world space.
  final double y;

  /// Z position in world space.
  final double z;

  /// Uniform scale factor applied to the model.
  final double scale;

  /// X rotation in degrees (Euler angles).
  final double rotationX;

  /// Y rotation in degrees (Euler angles).
  final double rotationY;

  /// Z rotation in degrees (Euler angles).
  final double rotationZ;

  const ModelNode({
    required this.modelPath,
    this.x = 0.0,
    this.y = 0.0,
    this.z = 0.0,
    this.scale = 1.0,
    this.rotationX = 0.0,
    this.rotationY = 0.0,
    this.rotationZ = 0.0,
  });

  Map<String, dynamic> toMap() => {
        'modelPath': modelPath,
        'x': x,
        'y': y,
        'z': z,
        'scale': scale,
        'rotationX': rotationX,
        'rotationY': rotationY,
        'rotationZ': rotationZ,
      };
}

/// Describes a geometry primitive in the scene.
///
/// Note: Geometry nodes are acknowledged by the native bridge but rendering
/// is not yet implemented. This is a forward-looking API placeholder.
class GeometryNode {
  /// Geometry type: 'cube', 'sphere', 'cylinder', or 'plane'.
  final String type;
  final double x;
  final double y;
  final double z;
  final double size;

  /// Fill color as an ARGB integer (e.g. 0xFF6750A4).
  final int color;

  /// When `true` the material ignores all scene lighting (no PBR shading,
  /// no IBL, no shadows) and renders the flat [color] straight to the
  /// framebuffer. Use for HUD overlays, gizmos, axes, lines, or AR face/body
  /// meshes — anywhere lighting would fight the use case.
  ///
  /// Defaults to `false` (lit PBR). Forwarded to the Android bridge as
  /// `MaterialLoader.createUnlitColorInstance(color)` when `true`.
  final bool unlit;

  const GeometryNode({
    required this.type,
    this.x = 0.0,
    this.y = 0.0,
    this.z = 0.0,
    this.size = 1.0,
    this.color = 0xFF888888,
    this.unlit = false,
  });

  Map<String, dynamic> toMap() => {
        'type': type,
        'x': x,
        'y': y,
        'z': z,
        'size': size,
        'color': color,
        'unlit': unlit,
      };
}

/// Describes a light source in the scene.
///
/// Note: Light nodes are acknowledged by the native bridge but custom
/// light configuration is not yet implemented. Scenes use sensible defaults.
class LightNode {
  /// Light type: 'directional', 'point', or 'spot'.
  final String type;
  final double intensity;

  /// Light color as an ARGB integer.
  final int color;
  final double x;
  final double y;
  final double z;

  const LightNode({
    this.type = 'directional',
    this.intensity = 100000.0,
    this.color = 0xFFFFFFFF,
    this.x = 0.0,
    this.y = 4.0,
    this.z = 0.0,
  });

  Map<String, dynamic> toMap() => {
        'type': type,
        'intensity': intensity,
        'color': color,
        'x': x,
        'y': y,
        'z': z,
      };
}

// ---------------------------------------------------------------------------
// Controller
// ---------------------------------------------------------------------------

/// Controls a [SceneView] or [ARSceneView] after creation.
///
/// Attach a controller to a scene widget, then call methods on it
/// after [onViewCreated] fires:
///
/// ```dart
/// final controller = SceneViewController();
///
/// SceneView(
///   controller: controller,
///   onViewCreated: () {
///     controller.loadModel(ModelNode(modelPath: 'models/helmet.glb'));
///   },
/// );
/// ```
class SceneViewController {
  MethodChannel? _channel;
  bool _disposed = false;

  /// Called when a model node is tapped. Receives the node name/id.
  void Function(String nodeName)? onTap;

  /// Called when an AR plane is detected. Receives the plane type
  /// ('horizontal_upward', 'horizontal_downward', 'vertical', or 'unknown').
  void Function(String planeType)? onPlaneDetected;

  /// Whether this controller is attached to a platform view.
  bool get isAttached => _channel != null && !_disposed;

  /// Called internally when the platform view is created.
  void attach(int viewId) {
    _channel = MethodChannel('io.github.sceneview.flutter/scene_$viewId');
    _channel!.setMethodCallHandler(_handleMethodCall);
    _disposed = false;
  }

  /// Called internally when the platform view is disposed.
  void dispose() {
    _channel?.setMethodCallHandler(null);
    _disposed = true;
    _channel = null;
  }

  Future<dynamic> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'onTap':
        final nodeName = call.arguments as String? ?? '';
        onTap?.call(nodeName);
        break;
      case 'onPlaneDetected':
        final planeType = call.arguments as String? ?? 'unknown';
        onPlaneDetected?.call(planeType);
        break;
    }
  }

  /// Load a glTF/GLB model into the scene.
  ///
  /// Throws [StateError] if the controller is not yet attached.
  Future<void> loadModel(ModelNode node) async {
    _ensureAttached();
    await _channel!.invokeMethod('loadModel', node.toMap());
  }

  /// Add a geometry node (placeholder -- not yet rendered natively).
  Future<void> addGeometry(GeometryNode node) async {
    _ensureAttached();
    await _channel!.invokeMethod('addGeometry', node.toMap());
  }

  /// Add a light node (placeholder -- uses scene defaults on native side).
  Future<void> addLight(LightNode node) async {
    _ensureAttached();
    await _channel!.invokeMethod('addLight', node.toMap());
  }

  /// Clear all nodes from the scene.
  Future<void> clearScene() async {
    _ensureAttached();
    await _channel!.invokeMethod('clearScene');
  }

  /// Set the environment HDR for image-based lighting.
  ///
  /// [hdrPath] should be an asset path like `'environments/studio_small.hdr'`.
  Future<void> setEnvironment(String hdrPath) async {
    _ensureAttached();
    await _channel!.invokeMethod('setEnvironment', {'hdrPath': hdrPath});
  }

  void _ensureAttached() {
    if (!isAttached) {
      throw StateError(
        'SceneViewController is not attached to a view. '
        'Wait for onViewCreated before calling methods.',
      );
    }
  }
}

// ---------------------------------------------------------------------------
// SceneView widget (3D)
// ---------------------------------------------------------------------------

/// Embeds a native 3D SceneView as a platform view.
///
/// ```dart
/// SceneView(
///   controller: controller,
///   onViewCreated: () => controller.loadModel(
///     ModelNode(modelPath: 'models/damaged_helmet.glb'),
///   ),
/// )
/// ```
class SceneView extends StatefulWidget {
  /// Optional controller for imperative commands (loadModel, clearScene, etc).
  final SceneViewController? controller;

  /// Called when the native platform view has been created and is ready.
  final VoidCallback? onViewCreated;

  /// Models to load immediately when the view is created.
  final List<ModelNode> initialModels;

  /// Called when a model node is tapped. Receives the node name/id.
  final void Function(String nodeName)? onTap;

  const SceneView({
    super.key,
    this.controller,
    this.onViewCreated,
    this.initialModels = const [],
    this.onTap,
  });

  @override
  State<SceneView> createState() => _SceneViewState();
}

class _SceneViewState extends State<SceneView> {
  static const String _viewType = 'io.github.sceneview.flutter/sceneview';

  /// Internal controller created when the widget has callbacks but no
  /// explicit controller. This ensures onTap is never silently dropped.
  SceneViewController? _internalController;

  SceneViewController get _effectiveController {
    if (widget.controller != null) return widget.controller!;
    _internalController ??= SceneViewController();
    return _internalController!;
  }

  void _onPlatformViewCreated(int id) {
    final controller = _effectiveController;
    controller.attach(id);
    controller.onTap = widget.onTap;
    widget.onViewCreated?.call();
  }

  @override
  void didUpdateWidget(covariant SceneView oldWidget) {
    super.didUpdateWidget(oldWidget);
    final controller = widget.controller ?? _internalController;
    if (controller != null && controller.isAttached) {
      controller.onTap = widget.onTap;
    }
  }

  @override
  void dispose() {
    _internalController?.dispose();
    widget.controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final creationParams = <String, dynamic>{
      'models': widget.initialModels.map((m) => m.toMap()).toList(),
    };

    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return AndroidView(
          viewType: _viewType,
          creationParams: creationParams,
          creationParamsCodec: const StandardMessageCodec(),
          onPlatformViewCreated: _onPlatformViewCreated,
          gestureRecognizers: <Factory<OneSequenceGestureRecognizer>>{
            Factory<PanGestureRecognizer>(() => PanGestureRecognizer()),
            Factory<ScaleGestureRecognizer>(() => ScaleGestureRecognizer()),
          },
        );
      case TargetPlatform.iOS:
        return UiKitView(
          viewType: _viewType,
          creationParams: creationParams,
          creationParamsCodec: const StandardMessageCodec(),
          onPlatformViewCreated: _onPlatformViewCreated,
          gestureRecognizers: <Factory<OneSequenceGestureRecognizer>>{
            Factory<PanGestureRecognizer>(() => PanGestureRecognizer()),
            Factory<ScaleGestureRecognizer>(() => ScaleGestureRecognizer()),
          },
        );
      default:
        return const Center(
          child: Text('SceneView is not supported on this platform'),
        );
    }
  }
}

// ---------------------------------------------------------------------------
// ARSceneView widget (AR)
// ---------------------------------------------------------------------------

/// Embeds a native AR SceneView as a platform view.
///
/// Requires camera permission on both Android and iOS.
///
/// ```dart
/// ARSceneView(
///   controller: controller,
///   onViewCreated: () => controller.loadModel(
///     ModelNode(modelPath: 'models/andy.glb'),
///   ),
/// )
/// ```
class ARSceneView extends StatefulWidget {
  /// Optional controller for imperative commands.
  final SceneViewController? controller;

  /// Called when the native platform view has been created and is ready.
  final VoidCallback? onViewCreated;

  /// Whether to enable plane detection and rendering.
  final bool planeDetection;

  /// Called when a model node is tapped. Receives the node name/id.
  final void Function(String nodeName)? onTap;

  /// Called when an AR plane is detected. Receives the plane type
  /// ('horizontal_upward', 'horizontal_downward', 'vertical', or 'unknown').
  final void Function(String planeType)? onPlaneDetected;

  const ARSceneView({
    super.key,
    this.controller,
    this.onViewCreated,
    this.planeDetection = true,
    this.onTap,
    this.onPlaneDetected,
  });

  @override
  State<ARSceneView> createState() => _ARSceneViewState();
}

class _ARSceneViewState extends State<ARSceneView> {
  static const String _viewType = 'io.github.sceneview.flutter/arsceneview';

  /// Internal controller created when the widget has callbacks but no
  /// explicit controller. This ensures onTap/onPlaneDetected are never
  /// silently dropped.
  SceneViewController? _internalController;

  SceneViewController get _effectiveController {
    if (widget.controller != null) return widget.controller!;
    _internalController ??= SceneViewController();
    return _internalController!;
  }

  void _onPlatformViewCreated(int id) {
    final controller = _effectiveController;
    controller.attach(id);
    controller.onTap = widget.onTap;
    controller.onPlaneDetected = widget.onPlaneDetected;
    widget.onViewCreated?.call();
  }

  @override
  void didUpdateWidget(covariant ARSceneView oldWidget) {
    super.didUpdateWidget(oldWidget);
    final controller = widget.controller ?? _internalController;
    if (controller != null && controller.isAttached) {
      controller.onTap = widget.onTap;
      controller.onPlaneDetected = widget.onPlaneDetected;
    }
  }

  @override
  void dispose() {
    _internalController?.dispose();
    widget.controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final creationParams = <String, dynamic>{
      'planeDetection': widget.planeDetection,
    };

    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return AndroidView(
          viewType: _viewType,
          creationParams: creationParams,
          creationParamsCodec: const StandardMessageCodec(),
          onPlatformViewCreated: _onPlatformViewCreated,
          gestureRecognizers: <Factory<OneSequenceGestureRecognizer>>{
            Factory<PanGestureRecognizer>(() => PanGestureRecognizer()),
            Factory<ScaleGestureRecognizer>(() => ScaleGestureRecognizer()),
          },
        );
      case TargetPlatform.iOS:
        return UiKitView(
          viewType: _viewType,
          creationParams: creationParams,
          creationParamsCodec: const StandardMessageCodec(),
          onPlatformViewCreated: _onPlatformViewCreated,
          gestureRecognizers: <Factory<OneSequenceGestureRecognizer>>{
            Factory<PanGestureRecognizer>(() => PanGestureRecognizer()),
            Factory<ScaleGestureRecognizer>(() => ScaleGestureRecognizer()),
          },
        );
      default:
        return const Center(
          child: Text('ARSceneView is not supported on this platform'),
        );
    }
  }
}
