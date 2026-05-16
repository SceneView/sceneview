// Unit tests for the SceneView Flutter plugin Dart API.
//
// These cover the pure-Dart surface (data classes, controller guards) without
// a platform view — the native rendering paths are exercised by the demo app.
// See issue #909.

import 'package:flutter_test/flutter_test.dart';
import 'package:sceneview_flutter/sceneview_flutter.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('ModelNode', () {
    test('toMap carries every field', () {
      final map = const ModelNode(
        modelPath: 'models/helmet.glb',
        x: 1.0,
        y: 2.0,
        z: 3.0,
        scale: 0.5,
        rotationX: 10.0,
        rotationY: 20.0,
        rotationZ: 30.0,
      ).toMap();

      expect(map['modelPath'], 'models/helmet.glb');
      expect(map['x'], 1.0);
      expect(map['y'], 2.0);
      expect(map['z'], 3.0);
      expect(map['scale'], 0.5);
      expect(map['rotationX'], 10.0);
      expect(map['rotationY'], 20.0);
      expect(map['rotationZ'], 30.0);
    });
  });

  group('GeometryNode', () {
    test('defaults are sane', () {
      final map = const GeometryNode(type: 'cube').toMap();
      expect(map['type'], 'cube');
      expect(map['size'], 1.0);
      expect(map['color'], 0xFF888888);
      expect(map['unlit'], false);
    });

    test('toMap carries every field including unlit', () {
      final map = const GeometryNode(
        type: 'sphere',
        x: 1.0,
        y: -1.0,
        z: 2.0,
        size: 0.25,
        color: 0xFF005BC1,
        unlit: true,
      ).toMap();

      expect(map['type'], 'sphere');
      expect(map['x'], 1.0);
      expect(map['y'], -1.0);
      expect(map['z'], 2.0);
      expect(map['size'], 0.25);
      expect(map['color'], 0xFF005BC1);
      expect(map['unlit'], true);
    });
  });

  group('LightNode', () {
    test('defaults match the Android bridge fallbacks', () {
      final map = const LightNode().toMap();
      expect(map['type'], 'directional');
      expect(map['intensity'], 100000.0);
      expect(map['color'], 0xFFFFFFFF);
      expect(map['y'], 4.0);
    });

    test('toMap carries every field', () {
      final map = const LightNode(
        type: 'point',
        intensity: 50000.0,
        color: 0xFFFFAA00,
        x: 1.0,
        y: 5.0,
        z: -2.0,
      ).toMap();

      expect(map['type'], 'point');
      expect(map['intensity'], 50000.0);
      expect(map['color'], 0xFFFFAA00);
      expect(map['x'], 1.0);
      expect(map['y'], 5.0);
      expect(map['z'], -2.0);
    });
  });

  group('SceneViewController', () {
    test('is not attached before a view is created', () {
      final controller = SceneViewController();
      expect(controller.isAttached, isFalse);
    });

    test('throws StateError when calling addGeometry before attach', () {
      final controller = SceneViewController();
      expect(
        () => controller.addGeometry(const GeometryNode(type: 'cube')),
        throwsStateError,
      );
    });

    test('throws StateError when calling addLight before attach', () {
      final controller = SceneViewController();
      expect(
        () => controller.addLight(const LightNode()),
        throwsStateError,
      );
    });

    test('attach then dispose flips isAttached', () {
      final controller = SceneViewController();
      controller.attach(0);
      expect(controller.isAttached, isTrue);
      controller.dispose();
      expect(controller.isAttached, isFalse);
    });
  });
}
