@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.sceneview.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Samples tab — a 2-column M3 Expressive grid of demos grouped by
 * category. Each card has a tinted icon header that uses an accent color
 * tied to the demo's category so the user can scan the grid by hue rather
 * than reading every label.
 *
 * Replaces the pre-v4.1.1 plain `ListItem` list (a flat text rundown that
 * felt straight out of a 2018 Settings screen). Visual reference: Sketchfab
 * mobile + Polycam + Reality Composer launchers — same density, same
 * thumbnail-first scannability, but with semantic Material icons and tinted
 * gradients instead of pre-baked previews (a future refactor can swap in
 * captured device thumbnails behind the same Card structure).
 *
 * Demos with a non-[DemoStatus.Working] status surface a small chip in the
 * top-right corner so users have honest expectations.
 */
@Composable
fun DemoListScreen(
    onDemoClick: (String) -> Unit,
    onAboutClick: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val grouped = remember {
        DEMO_CATEGORIES.map { cat ->
            cat to ALL_DEMOS.filter { it.category == cat }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Samples") },
                actions = {
                    IconButton(onClick = onAboutClick) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "About",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            grouped.forEach { (category, demos) ->
                item(
                    key = "header-$category",
                    span = { GridItemSpan(2) },
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            top = 16.dp,
                            bottom = 4.dp,
                            start = 4.dp,
                        ),
                    )
                }

                items(demos, key = { it.id }) { demo ->
                    DemoCard(
                        demo = demo,
                        onClick = { onDemoClick(demo.id) },
                    )
                }
            }

            item(
                key = "footer",
                span = { GridItemSpan(2) },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 16.dp),
                ) {
                    Text(
                        // BuildConfig.VERSION_NAME comes from gradle.properties /
                        // CI build args — hard-coding here would drift every
                        // release.
                        text = "SceneView v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "github.com/sceneview/sceneview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoCard(
    demo: DemoEntry,
    onClick: () -> Unit,
) {
    val accent = remember(demo.category) { categoryAccent(demo.category) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Icon tile — accent-tinted gradient, semantic icon centered.
                // The tile is wider than tall so the icon reads as the visual
                // anchor of the card.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.32f),
                                    accent.copy(alpha = 0.14f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = demo.icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(40.dp),
                    )
                }

                // Title + subtitle.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = demo.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = demo.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        lineHeight = 14.sp,
                    )
                }
            }

            // Status chip — only renders for non-Working demos so the grid stays
            // visually quiet for the happy path.
            if (demo.status != DemoStatus.Working) {
                StatusChip(
                    status = demo.status,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: DemoStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        DemoStatus.Working -> "" to Color.Transparent
        DemoStatus.KnownIssue -> "Known issue" to Color(0xFFE57373)
        DemoStatus.ComingSoon -> "Coming soon" to Color(0xFF90A4AE)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.92f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * Per-category accent color used to tint the card's icon tile. Chosen to be
 * distinct enough that the eye can sort the grid by category at a glance,
 * but desaturated so they coexist on the same screen without clashing with
 * the M3 Expressive primary / tertiary roles.
 */
private fun categoryAccent(category: String): Color = when (category) {
    "3D Basics" -> Color(0xFF6446CD)              // primary purple
    "Lighting & Environment" -> Color(0xFFE6A23C) // warm amber
    "Content" -> Color(0xFF42A5F5)                // sky blue
    "Interaction" -> Color(0xFFEC407A)            // pink
    "Advanced" -> Color(0xFF26A69A)               // teal
    "Augmented Reality" -> Color(0xFF66BB6A)      // green
    else -> Color(0xFF6446CD)
}
