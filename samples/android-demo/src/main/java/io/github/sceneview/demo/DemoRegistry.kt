package io.github.sceneview.demo

/**
 * Central registry of all demo entries.
 * Each demo maps to a composable in the `demos/` package.
 */
data class DemoEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String
)

/** Ordered list of category names — controls display order in the list. */
val DEMO_CATEGORIES = listOf(
    "3D Basics",
    "Lighting & Environment",
    "Content",
    "Interaction",
    "Advanced",
    "Augmented Reality"
)

val ALL_DEMOS = listOf(
    // 3D Basics
    DemoEntry("model-viewer", "Model Viewer", "Load and display 3D models", "3D Basics"),
    DemoEntry("geometry", "Geometry Primitives", "Cube, Sphere, Cylinder, Plane", "3D Basics"),
    DemoEntry("animation", "Animation", "Play, pause, and control animations", "3D Basics"),
    // Titles below match what each demo's DemoScaffold renders in the top bar —
    // keeps the list label, navigation breadcrumbs and instrumentation tests
    // aligned (#889).
    DemoEntry("multi-model", "Multiple Models", "Multiple models in one scene", "3D Basics"),
    // Lighting & Environment
    DemoEntry("lighting", "Lighting", "Directional, point, and spot lights", "Lighting & Environment"),
    DemoEntry("dynamic-sky", "Dynamic Sky", "Time-of-day sun simulation", "Lighting & Environment"),
    DemoEntry("fog", "Fog", "Atmospheric fog effects", "Lighting & Environment"),
    DemoEntry("environment", "Environment Gallery", "HDR environment switching", "Lighting & Environment"),
    // Content
    DemoEntry("text", "Text Nodes", "3D text in the scene", "Content"),
    DemoEntry("lines-paths", "Lines & Paths", "Line segments and polylines", "Content"),
    DemoEntry("image", "Image Node", "Image planes in 3D space", "Content"),
    DemoEntry("billboard", "Billboard Node", "Camera-facing quads", "Content"),
    DemoEntry("video", "Video", "Video playback on 3D surface", "Content"),
    // Interaction
    DemoEntry("camera-controls", "Camera Controls", "Orbit, fly, and free camera modes", "Interaction"),
    DemoEntry("gesture-editing", "Gesture Editing", "Move, scale, rotate with gestures", "Interaction"),
    DemoEntry("collision", "Collision & Hit Test", "Hit testing and collision detection", "Interaction"),
    DemoEntry("view-node", "ViewNode", "Compose UI embedded in 3D space", "Interaction"),
    // Advanced
    DemoEntry("physics", "Physics", "Gravity, collisions, rigid bodies", "Advanced"),
    DemoEntry("post-processing", "Post Processing", "SSAO, anti-aliasing, tone mapping", "Advanced"),
    DemoEntry("custom-mesh", "Custom Mesh", "Custom vertex and index buffers", "Advanced"),
    DemoEntry("shape", "Shape Node", "Extruded 2D polygons", "Advanced"),
    DemoEntry("reflection-probes", "Reflection Probes", "Local cubemap reflections", "Advanced"),
    DemoEntry("secondary-camera", "Camera Presets", "Picture-in-picture camera view", "Advanced"),
    DemoEntry("debug-overlay", "Debug Overlay", "Performance stats overlay", "Advanced"),
    // Augmented Reality
    DemoEntry("ar-placement", "Tap to Place", "Place 3D models in AR", "Augmented Reality"),
    DemoEntry("ar-image", "Augmented Image", "Detect and track images", "Augmented Reality"),
    DemoEntry("ar-face", "Face Mesh", "Facial mesh tracking", "Augmented Reality"),
    DemoEntry("ar-cloud-anchor", "Cloud Anchor", "Persistent cloud anchors", "Augmented Reality"),
    DemoEntry("ar-streetscape", "Streetscape Geometry", "Urban geometry overlay", "Augmented Reality"),
    DemoEntry("ar-pose", "Pose Placement", "Free pose positioning", "Augmented Reality"),
    DemoEntry("ar-rerun", "Rerun Debug", "AR debug streaming to Rerun.io", "Augmented Reality"),
    DemoEntry("ar-record-playback", "Record & Playback", "Record an AR session and replay it without a phone", "Augmented Reality"),
    DemoEntry("ar-depth-occlusion", "Depth Occlusion", "Real objects hide virtual ones — Depth API", "Augmented Reality"),
    DemoEntry("ar-instant-placement", "Instant Placement", "Place models before plane detection converges", "Augmented Reality"),
    DemoEntry("ar-terrain", "Terrain Anchors", "Geospatial anchor that snaps to outdoor terrain", "Augmented Reality"),
    DemoEntry("ar-rooftop", "Rooftop Anchors", "Geospatial anchor that snaps to building rooftops", "Augmented Reality"),
    DemoEntry("ar-image-stabilization", "Image Stabilization (EIS)", "Smooth shaky camera with one config flag", "Augmented Reality"),
)
