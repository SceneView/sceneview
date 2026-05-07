@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.sceneview.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Placeholder demo body shown when a demo composable is rendered inside Android Studio's
 * `@Preview` panel (`LocalInspectionMode.current == true`).
 *
 * Each demo's body opens with:
 * ```
 * if (LocalInspectionMode.current) {
 *     DemoPreviewPlaceholder(title = "Geometry Primitives", onBack = onBack)
 *     return
 * }
 * ```
 *
 * Without this guard, the very first call to `rememberEngine()` (inside the demo body,
 * BEFORE `SceneView { ... }` runs and bypasses Filament via its own inspection-mode
 * check) would crash the preview because Android Studio's LayoutLib doesn't load
 * Filament's native libraries (they're Android-arch only).
 *
 * The placeholder mimics the demo's [DemoScaffold] layout (TopAppBar + body) so the
 * visual scaffolding still renders correctly — only the 3D scene is replaced by an
 * informative gradient panel pointing developers at Live Edit.
 */
@Composable
fun DemoPreviewPlaceholder(
    title: String,
    onBack: () -> Unit,
    bodyHint: String = "3D rendering needs Filament JNI which AS LayoutLib does not load.\n" +
        "Use Android Studio Live Edit on a connected device for the real scene.",
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1F2937),
                            Color(0xFF0F172A),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = "🧊  Preview placeholder",
                    color = Color(0xFFE0E7FF),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    text = bodyHint,
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
