package io.github.sceneview.demo.demos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.StreetscapeGeometry
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.SceneViewColors
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader

/**
 * Geospatial API streetscape geometry demo.
 *
 * Enables the ARCore Geospatial API and Streetscape Geometry mode to render detected building
 * and terrain meshes. Each [StreetscapeGeometryNode] displays a semi-transparent mesh overlay
 * on real-world structures.
 *
 * Requires:
 * - ARCore Geospatial API enabled in Google Cloud Console
 * - Device supports ARCore Geospatial API
 * - Outdoor environment with Google Street View coverage
 */
@Composable
fun ARStreetscapeDemo(onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    val geometries = remember { mutableStateListOf<StreetscapeGeometry>() }
    var isTracking by remember { mutableStateOf(false) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var geometryCount by remember { mutableStateOf(0) }

    // Semi-transparent material for streetscape geometry overlays — SceneView TintLight at
    // low alpha so the real camera feed of buildings/sidewalks stays readable through the
    // overlay mesh.
    val buildingMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = SceneViewColors.LandscapeOverlay,
            metallic = 0.1f,
            roughness = 0.9f,
            reflectance = 0.1f
        )
    }

    DemoScaffold(
        title = "Streetscape Geometry",
        onBack = onBack
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                planeRenderer = false,
                sessionConfiguration = { _: Session, config: Config ->
                    config.geospatialMode = Config.GeospatialMode.ENABLED
                    config.streetscapeGeometryMode = Config.StreetscapeGeometryMode.ENABLED
                    config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                },
                onSessionUpdated = { _: Session, frame: Frame ->
                    isTracking = frame.camera.trackingState == TrackingState.TRACKING
                    frame.getUpdatedTrackables(StreetscapeGeometry::class.java).forEach { geo ->
                        if (geo.trackingState == TrackingState.TRACKING) {
                            if (geometries.none { it == geo }) {
                                geometries.add(geo)
                            }
                        }
                    }
                    // Remove geometries that stopped tracking
                    geometries.removeAll { it.trackingState == TrackingState.STOPPED }
                    geometryCount = geometries.size
                },
                onTrackingFailureChanged = { reason ->
                    trackingFailureReason = reason
                }
            ) {
                geometries.forEach { geo ->
                    StreetscapeGeometryNode(
                        streetscapeGeometry = geo,
                        meshMaterialInstance = buildingMaterial,
                        onTrackingStateChanged = { state ->
                            // Could update UI for individual geometry tracking
                        }
                    )
                }
            }

            // Status overlay
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val statusText = when {
                    geometryCount > 0 -> "Rendering $geometryCount structure(s)"
                    !isTracking -> trackingFailureReason?.let { reason ->
                        when (reason) {
                            TrackingFailureReason.NONE -> "Initializing geospatial\u2026"
                            TrackingFailureReason.BAD_STATE -> "AR session error"
                            TrackingFailureReason.INSUFFICIENT_LIGHT -> "Not enough light"
                            TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast"
                            TrackingFailureReason.INSUFFICIENT_FEATURES -> "Not enough detail"
                            TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable"
                        }
                    } ?: "Scanning environment\u2026"
                    else -> "Looking for streetscape geometry\u2026"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(bottom = 32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}
