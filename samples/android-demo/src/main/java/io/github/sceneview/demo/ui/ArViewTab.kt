@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package io.github.sceneview.demo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * AR View tab — full-screen live ARSceneView with a glass status pill and a
 * floating M3-Expressive action bar. Mirrors the iOS `ARTab.swift` spec:
 *
 *  - Full-bleed ARSceneView underneath (camera passthrough)
 *  - Top: glass status pill — "Tap a surface to place {Model}" / "N placed"
 *  - Bottom: floating action bar with FAB "Pick model" + Reset + Screenshot
 *  - Modal bottom sheet for the model picker grid
 *
 * Reset is implemented by bumping a `key(arSceneId)` wrapper around the
 * ARSceneView — there is no `removeAllAnchors` on the wrapper, so we
 * recompose the whole subtree to clear ARCore state and start a fresh
 * session.
 *
 * The screenshot button is a stub that shares a deep link to the demo
 * placeholder for now — capturing the ARSceneView's GL surface requires
 * Frame.acquireCameraImage + Filament read-pixels plumbing that is out of
 * scope for this M3 Expressive UI refactor.
 */
@Composable
fun ArViewTabContent(
    @Suppress("UNUSED_PARAMETER") onDemoClick: (String) -> Unit,
) {
    val context = LocalContext.current

    // ---------- Permission gate ----------
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionsResolved by remember { mutableStateOf(cameraGranted) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        permissionsResolved = true
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            permissionsResolved = true
        }
    }

    if (!permissionsResolved || !cameraGranted) {
        ArPermissionPlaceholder(granted = permissionsResolved && cameraGranted)
        return
    }

    // ---------- State ----------
    val arModels = remember { AR_MODELS }
    var selectedModelIndex by remember { mutableStateOf(0) }
    val selectedModel = arModels[selectedModelIndex]

    val placedAnchors = remember { mutableStateListOf<PlacedAr>() }
    var nextId by remember { mutableStateOf(0) }

    // Live AR session state for the status pill.
    var isTracking by remember { mutableStateOf(false) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var latestFrame by remember { mutableStateOf<Frame?>(null) }

    // Force-rebuild key for the ARSceneView. Bumping this UUID recomposes the
    // whole AR subtree, which is the only way to discard ARCore state without
    // a wrapper-level resetSession() API (iOS does the same via arViewID).
    var arSceneId by remember { mutableStateOf(UUID.randomUUID()) }

    var showModelPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // ---------- Engine / loaders ----------
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    Box(modifier = Modifier.fillMaxSize()) {
        // Live ARSceneView — full bleed under the overlays.
        key(arSceneId) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                planeRenderer = true,
                cameraExposure = -1.0f,
                sessionConfiguration = { _: Session, config: Config ->
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                },
                onSessionUpdated = { _, frame ->
                    latestFrame = frame
                    isTracking = frame.camera.trackingState == TrackingState.TRACKING
                },
                onTrackingFailureChanged = { reason ->
                    trackingFailureReason = reason
                },
                onGestureListener = rememberOnGestureListener(
                    onSingleTapConfirmed = { event: MotionEvent, node ->
                        // Tap on an existing editable model -> let it handle
                        // the gesture. Avoid stacking new models on top.
                        if (node != null) return@rememberOnGestureListener

                        val frame = latestFrame ?: return@rememberOnGestureListener
                        if (frame.camera.trackingState != TrackingState.TRACKING) {
                            return@rememberOnGestureListener
                        }
                        val hit = frame.hitTest(event).firstOrNull { result ->
                            val trackable = result.trackable
                            trackable is Plane &&
                                trackable.isPoseInPolygon(result.hitPose) &&
                                result.distance <= 5.0f
                        }
                        if (hit != null) {
                            placedAnchors.add(
                                PlacedAr(
                                    id = nextId++,
                                    anchor = hit.createAnchor(),
                                    assetPath = selectedModel.assetPath,
                                    scale = selectedModel.scale,
                                ),
                            )
                        }
                    },
                ),
            ) {
                placedAnchors.forEach { placed ->
                    key(placed.id) {
                        AnchorNode(anchor = placed.anchor) {
                            val instance = rememberModelInstance(modelLoader, placed.assetPath)
                            instance?.let {
                                ModelNode(
                                    modelInstance = it,
                                    scaleToUnits = placed.scale,
                                    centerOrigin = Position(0.0f, 0.0f, 0.0f),
                                    isEditable = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top status pill — translucent surface, capsule shape, M3 Expressive.
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(50),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val placed = placedAnchors.size
                Icon(
                    imageVector = if (placed == 0) {
                        Icons.Filled.TouchApp
                    } else {
                        Icons.Filled.CheckCircle
                    },
                    contentDescription = null,
                    tint = if (placed == 0) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = when {
                        !isTracking -> trackingFailureReason?.let { friendly(it) }
                            ?: "Scanning for surfaces…"
                        placed == 0 -> "Tap a surface to place ${selectedModel.name}"
                        placed == 1 -> "1 placed · tap to add"
                        else -> "$placed placed · tap to add"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Bottom floating action bar.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // FAB "Pick model" — primary, fills available width.
                ExtendedFloatingActionButton(
                    onClick = { showModelPicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    expanded = true,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.ViewInAr,
                            contentDescription = null,
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "Model",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                    alpha = 0.7f,
                                ),
                            )
                            Text(
                                text = selectedModel.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                )

                // Reset.
                FilledIconButton(
                    onClick = {
                        placedAnchors.forEach { runCatching { it.anchor.detach() } }
                        placedAnchors.clear()
                        arSceneId = UUID.randomUUID()
                    },
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reset scene",
                    )
                }

                // Screenshot — share-stub for now (live capture is out of scope
                // for this UI refactor; ARSceneView GL readback requires
                // Filament SwapChain plumbing).
                FilledIconButton(
                    onClick = {
                        android.widget.Toast.makeText(
                            context,
                            "Screenshot capture: use system screenshot (Power + Volume Down)",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share screenshot",
                    )
                }
            }
        }
    }

    if (showModelPicker) {
        ModalBottomSheet(
            onDismissRequest = { showModelPicker = false },
            sheetState = sheetState,
        ) {
            ModelPickerGrid(
                models = arModels,
                selectedIndex = selectedModelIndex,
                onSelect = { idx ->
                    selectedModelIndex = idx
                    coroutineScope.launch {
                        sheetState.hide()
                        showModelPicker = false
                    }
                },
            )
        }
    }
}

@Composable
private fun ModelPickerGrid(
    models: List<ArModel>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Pick a model",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
        ) {
            items(models.size) { index ->
                val model = models[index]
                val selected = index == selectedIndex
                Card(
                    onClick = { onSelect(index) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                    modifier = Modifier
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(18.dp),
                        ),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.6f,
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ViewInAr,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ArPermissionPlaceholder(granted: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Cached,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (granted) {
                    "Starting AR session…"
                } else {
                    "Camera permission is required to run AR"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (granted) {
                    "Hold up — initializing ARCore."
                } else {
                    "Grant the permission in system settings, then return to this tab."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun friendly(reason: TrackingFailureReason): String = when (reason) {
    TrackingFailureReason.NONE -> "Scanning for surfaces…"
    TrackingFailureReason.BAD_STATE -> "AR session error"
    TrackingFailureReason.INSUFFICIENT_LIGHT -> "Not enough light"
    TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast"
    TrackingFailureReason.INSUFFICIENT_FEATURES ->
        "Not enough detail — try a textured surface"
    TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable"
}

// ---------- Model catalogue ----------

internal data class ArModel(
    val name: String,
    val assetPath: String,
    val scale: Float,
)

internal data class PlacedAr(
    val id: Int,
    val anchor: com.google.ar.core.Anchor,
    val assetPath: String,
    val scale: Float,
)

private val AR_MODELS = listOf(
    ArModel("Damaged Helmet", "models/khronos_damaged_helmet.glb", 0.3f),
    ArModel("Avocado", "models/khronos_avocado.glb", 0.15f),
    ArModel("Fox", "models/khronos_fox.glb", 0.3f),
    ArModel("Lantern", "models/khronos_lantern.glb", 0.3f),
    ArModel("Toy Car", "models/khronos_toy_car.glb", 0.3f),
    ArModel("Shiba", "models/shiba.glb", 0.3f),
    ArModel("Dragon", "models/animated_dragon.glb", 0.3f),
    ArModel("Soldier", "models/threejs_soldier.glb", 0.3f),
)
