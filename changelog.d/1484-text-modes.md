<!-- category: Changed -->
- Android demo: redesigned the **Text Nodes** demo so its three stacked labels each
  demonstrate a distinct facing mode — single-sided (culled from behind), double-sided
  (readable from both sides), and mirror/billboard (always faces the camera). Added a
  **Slab Thickness** control in the Settings sheet that extrudes a real 3D slab behind
  each label, making the 3D-ness obvious as the camera orbits. (Note: `TextNode` renders
  flat bitmap text — SceneView has no glyph-extrusion API yet, so the thickness control
  drives a backing `CubeNode` slab instead.) (#1484)
