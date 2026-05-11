@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.sceneview.demo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sceneview.demo.ALL_DEMOS
import io.github.sceneview.demo.BuildConfig
import io.github.sceneview.demo.DemoEntry
import io.github.sceneview.demo.DemoListScreen
import io.github.sceneview.demo.ui.explore.ExploreTabScreen

/**
 * Top-level UI scaffold. Hosts the four primary tabs (Explore, AR View,
 * Samples, About) under a single M3 [NavigationBar]. The Explore tab
 * (live Sketchfab catalog) is the default landing experience — it's the
 * highest-conversion surface for new users.
 *
 * Routing back into the existing per-demo screens is delegated to the
 * caller via [onDemoClick] — this composable does not own the NavHost so
 * deep-link replay (`sceneview://demo/<id>`) keeps working unchanged.
 */
@Composable
fun RootScreen(onDemoClick: (String) -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(RootTab.Explore) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                RootTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (selectedTab) {
                RootTab.Explore -> ExploreTabScreen(
                    curatedSamples = curatedSamplesForExplore(),
                    onSampleClick = { sample -> onDemoClick(sample.id) },
                )
                RootTab.ArView -> ArViewTabContent(onDemoClick = onDemoClick)
                RootTab.Samples -> DemoListScreen(onDemoClick = onDemoClick)
                RootTab.About -> AboutTabContent()
            }
        }
    }
}

enum class RootTab(val label: String, val icon: ImageVector) {
    Explore("Explore", Icons.Filled.Search),
    ArView("AR View", Icons.Filled.ViewInAr),
    Samples("Samples", Icons.Filled.PlayArrow),
    About("About", Icons.Filled.Info),
}

/**
 * Curated subset of [ALL_DEMOS] surfaced in the "Try a sample" carousel.
 * Hand-picked across categories so the carousel feels diverse on first
 * launch — same intent as the iOS `featuredModels` list.
 */
private fun curatedSamplesForExplore(): List<DemoEntry> {
    val ids = listOf(
        "model-viewer",
        "geometry",
        "lighting",
        "ar-placement",
        "multi-model",
        "animation",
    )
    return ids.mapNotNull { id -> ALL_DEMOS.firstOrNull { it.id == id } }
}

@Composable
private fun AboutTabContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            text = "About",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "SceneView v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Declarative 3D & AR for Jetpack Compose. Open-source under the Apache 2.0 license — built on Filament + ARCore.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "github.com/sceneview/sceneview",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
