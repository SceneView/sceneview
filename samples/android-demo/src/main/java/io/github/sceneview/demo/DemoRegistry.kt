package io.github.sceneview.demo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FormatShapes
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PanoramaPhotosphere
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Roofing
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Runtime status of a demo as observed on the audited device matrix
 * (Pixel emulators + Pixel 9 hardware in `.claude/scripts/qa-android-demos.sh`).
 * Drives the badge rendered on the demo card in [DemoListScreen] so users
 * see honest expectations rather than a uniformly green list that lies.
 */
enum class DemoStatus {
    /** Verified working on the audit device matrix. */
    Working,

    /** Known visual or interaction regression — surfaced with a yellow badge. */
    KnownIssue,

    /** Compiles but not yet wired up to a real implementation. */
    ComingSoon,
}

/**
 * One entry in the curated demo list shown on the Samples tab.
 *
 * @param id        Stable identifier used by the deep-link router
 *                  (`sceneview://demo/<id>`) and as a Compose key.
 * @param title     Short headline shown on the card.
 * @param subtitle  One-line description rendered under [title].
 * @param category  Section header on the Samples tab; ordered by [DEMO_CATEGORIES].
 * @param icon      Material icon used to give the card visual identity
 *                  in the absence of a captured 3D thumbnail.
 * @param status    See [DemoStatus]. Defaults to [DemoStatus.Working].
 */
data class DemoEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val icon: ImageVector,
    val status: DemoStatus = DemoStatus.Working,
)

/** Ordered list of category names — controls display order in the list. */
val DEMO_CATEGORIES = listOf(
    "3D Basics",
    "Lighting & Environment",
    "Content",
    "Interaction",
    "Advanced",
    "Augmented Reality",
)

val ALL_DEMOS = listOf(
    // 3D Basics
    DemoEntry("model-viewer", "Model Viewer", "Load and display 3D models", "3D Basics", Icons.Filled.ViewInAr),
    DemoEntry("geometry", "Geometry Primitives", "Cube, Sphere, Cylinder, Plane", "3D Basics", Icons.Filled.Category),
    DemoEntry("animation", "Auto Rotate", "Play, pause, and control animations", "3D Basics", Icons.Filled.RotateRight),
    DemoEntry("multi-model", "Multi Model", "Multiple models in one scene", "3D Basics", Icons.Filled.Layers),
    // Lighting & Environment
    DemoEntry("lighting", "Light Types", "Directional, point, and spot lights", "Lighting & Environment", Icons.Filled.Lightbulb),
    DemoEntry("movable-light", "Movable Light", "Drag to orbit the light around the model", "Lighting & Environment", Icons.Filled.AutoFixHigh),
    DemoEntry("dynamic-sky", "Dynamic Sky", "Time-of-day sun simulation", "Lighting & Environment", Icons.Filled.WbSunny),
    DemoEntry("fog", "Fog", "Atmospheric fog effects", "Lighting & Environment", Icons.Filled.Cloud),
    DemoEntry("environment", "Environment Gallery", "HDR environment switching", "Lighting & Environment", Icons.Filled.PanoramaPhotosphere),
    // Content
    DemoEntry("text", "Text Nodes", "3D text in the scene", "Content", Icons.Filled.FormatShapes),
    DemoEntry("lines-paths", "Lines & Paths", "Line segments and polylines", "Content", Icons.Filled.Timeline),
    DemoEntry("image", "Image Planes", "Image planes in 3D space", "Content", Icons.Filled.Image),
    DemoEntry("billboard", "Billboard", "Camera-facing quads", "Content", Icons.Filled.Visibility),
    DemoEntry("video", "Video", "Video playback on 3D surface", "Content", Icons.Filled.VideoLibrary),
    // Interaction
    DemoEntry("camera-controls", "Camera Controls", "Orbit, fly, and free camera modes", "Interaction", Icons.Filled.PhotoCamera),
    DemoEntry("gesture-editing", "Gesture Editing", "Move, scale, rotate with gestures", "Interaction", Icons.Filled.Gesture),
    DemoEntry("collision", "Collision & Hit Test", "Hit testing and collision detection", "Interaction", Icons.Filled.DragIndicator),
    DemoEntry("view-node", "ViewNode", "Compose UI embedded in 3D space", "Interaction", Icons.Filled.Dashboard),
    // Advanced
    DemoEntry("physics", "Physics", "Gravity, collisions, rigid bodies", "Advanced", Icons.Filled.Stars),
    DemoEntry("post-processing", "Post Processing", "SSAO, anti-aliasing, tone mapping", "Advanced", Icons.Filled.Tune),
    DemoEntry("custom-mesh", "Custom Mesh", "Custom vertex and index buffers", "Advanced", Icons.Filled.Hexagon),
    DemoEntry("shape", "All Shapes", "Extruded 2D polygons", "Advanced", Icons.Filled.LinearScale),
    DemoEntry("reflection-probes", "Reflection Probes", "Local cubemap reflections", "Advanced", Icons.Filled.BlurOn),
    DemoEntry("secondary-camera", "Camera Presets", "Picture-in-picture camera view", "Advanced", Icons.Filled.PictureInPicture),
    DemoEntry("debug-overlay", "Debug Overlay", "Performance stats overlay", "Advanced", Icons.Filled.Speed),
    // Augmented Reality
    DemoEntry("ar-placement", "Tap to Place", "Place 3D models in AR", "Augmented Reality", Icons.Filled.TouchApp),
    DemoEntry("ar-image", "Augmented Image", "Detect and track images", "Augmented Reality", Icons.Filled.Image),
    DemoEntry("ar-face", "Face Mesh", "Facial mesh tracking", "Augmented Reality", Icons.Filled.Face),
    DemoEntry("ar-cloud-anchor", "Cloud Anchor", "Persistent cloud anchors", "Augmented Reality", Icons.Filled.CloudCircle),
    DemoEntry("ar-streetscape", "Streetscape Geometry", "Urban geometry overlay", "Augmented Reality", Icons.Filled.LocationCity),
    DemoEntry("ar-pose", "Pose Placement", "Free pose positioning", "Augmented Reality", Icons.Filled.MyLocation),
    DemoEntry("ar-rerun", "Rerun Debug", "AR debug streaming to Rerun.io", "Augmented Reality", Icons.Filled.BugReport),
    DemoEntry("ar-record-playback", "Record & Playback", "Record an AR session and replay it without a phone", "Augmented Reality", Icons.Filled.Replay),
    DemoEntry("ar-depth-occlusion", "Depth Occlusion", "Real objects hide virtual ones — Depth API", "Augmented Reality", Icons.Filled.FilterCenterFocus),
    DemoEntry("ar-instant-placement", "Instant Placement", "Place models before plane detection converges", "Augmented Reality", Icons.Filled.HourglassEmpty),
    DemoEntry("ar-terrain", "Terrain Anchors", "Geospatial anchor that snaps to outdoor terrain", "Augmented Reality", Icons.Filled.Landscape),
    DemoEntry("ar-rooftop", "Rooftop Anchors", "Geospatial anchor that snaps to building rooftops", "Augmented Reality", Icons.Filled.Roofing),
    DemoEntry("ar-image-stabilization", "Image Stabilization (EIS)", "Smooth shaky camera with one config flag", "Augmented Reality", Icons.Filled.Texture),
    DemoEntry("ar-orbital", "Orbital AR", "Models orbit around you in a personal solar system", "Augmented Reality", Icons.Filled.Public),
)
