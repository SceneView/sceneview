<!-- category: Fixed -->
- Web: guard `SceneView.loadModel` against a use-after-free — a reloaded or destroyed model's pending `loadResources` callback no longer touches the freed `FilamentAsset` (#1597).
