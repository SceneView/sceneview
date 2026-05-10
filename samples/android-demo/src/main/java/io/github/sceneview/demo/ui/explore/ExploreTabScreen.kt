@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.sceneview.demo.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.sceneview.demo.DemoEntry
import io.github.sceneview.demo.sketchfab.SketchfabConfig
import io.github.sceneview.demo.sketchfab.SketchfabModel
import io.github.sceneview.demo.sketchfab.SketchfabService
import io.github.sceneview.demo.ui.explore.components.FeaturedSketchfabCard
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Curated 3D-model discovery feed. Mirrors the iOS `ExploreTab` so QA can
 * compare both apps side-by-side.
 *
 * Top-down layout:
 *   1. Search bar (Sketchfab queries)
 *   2. Filter chips — currently "Animated" (toggle reloads the three feeds)
 *   3. "Try a sample" curated row — links straight into existing demos
 *   4. "Staff Picks"     carousel (Sketchfab)
 *   5. "Most Liked"      carousel (Sketchfab)
 *   6. "Recently Added"  carousel (Sketchfab)
 *   7. Categories grid (18 official Sketchfab slugs)
 *   8. Recent searches (SharedPreferences-backed)
 *
 * When `BuildConfig.SKETCHFAB_API_KEY` is empty the three Sketchfab carousels
 * are hidden and only the curated sample row + categories are shown — keeps
 * the public Play Store build useful even without the proprietary key.
 */
@Composable
fun ExploreTabScreen(
    curatedSamples: List<DemoEntry>,
    onSampleClick: (DemoEntry) -> Unit,
    onCategoryClick: (SketchfabCategory) -> Unit = {},
) {
    val scroll = rememberScrollState()
    val recentSearches = rememberRecentSearches()

    var searchQuery by remember { mutableStateOf("") }
    var animatedOnly by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf<SketchfabModel?>(null) }

    var staffPicks by remember { mutableStateOf<List<SketchfabModel>>(emptyList()) }
    var mostLiked by remember { mutableStateOf<List<SketchfabModel>>(emptyList()) }
    var recent by remember { mutableStateOf<List<SketchfabModel>>(emptyList()) }
    var feedsError by remember { mutableStateOf<String?>(null) }
    var loadingFeeds by remember { mutableStateOf(false) }

    val sketchfabService = SketchfabService.getInstance(androidx.compose.ui.platform.LocalContext.current)

    /** (re)load the three feeds when the animated toggle flips or on first composition. */
    LaunchedEffect(animatedOnly) {
        if (SketchfabConfig.apiKey == null) return@LaunchedEffect
        loadingFeeds = true
        feedsError = null
        val animatedParam: Boolean? = if (animatedOnly) true else null
        val result = runCatching {
            coroutineScope {
                val a = async { sketchfabService.staffPicks(animated = animatedParam, limit = 10) }
                val b = async { sketchfabService.featured(animated = animatedParam, limit = 10) }
                val c = async { sketchfabService.recentlyAdded(animated = animatedParam, limit = 10) }
                listOf(a, b, c).awaitAll()
            }
        }
        result.fold(
            onSuccess = { (s, l, r) ->
                staffPicks = s
                mostLiked = l
                recent = r
            },
            onFailure = { feedsError = "Couldn't reach Sketchfab — showing offline picks" },
        )
        loadingFeeds = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(0.dp))
        Text(
            text = "Explore",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        SearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            onSubmit = { q ->
                if (q.isNotBlank()) recentSearches.push(q)
            },
        )

        if (SketchfabConfig.apiKey != null) {
            FiltersBar(animatedOnly = animatedOnly, onToggle = { animatedOnly = !animatedOnly })
        }

        if (curatedSamples.isNotEmpty()) {
            CarouselSection(title = "Try a sample") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(curatedSamples) { sample ->
                        SampleCard(sample = sample, onClick = { onSampleClick(sample) })
                    }
                }
            }
        }

        if (SketchfabConfig.apiKey == null) {
            ApiKeyMissingNotice()
        } else {
            if (feedsError != null) {
                Text(
                    text = feedsError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            FeedSection(
                title = "Staff Picks",
                models = staffPicks,
                loading = loadingFeeds && staffPicks.isEmpty(),
                onModelClick = { selectedModel = it },
            )
            FeedSection(
                title = "Most Liked",
                models = mostLiked,
                loading = loadingFeeds && mostLiked.isEmpty(),
                onModelClick = { selectedModel = it },
            )
            FeedSection(
                title = "Recently Added",
                models = recent,
                loading = loadingFeeds && recent.isEmpty(),
                onModelClick = { selectedModel = it },
            )
        }

        CategoriesSection(onCategoryClick = onCategoryClick)

        if (recentSearches.items.isNotEmpty()) {
            RecentSearchesSection(
                items = recentSearches.items,
                onClear = { recentSearches.clear() },
                onClick = { searchQuery = it },
                onRemove = { recentSearches.remove(it) },
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    selectedModel?.let { model ->
        SketchfabModelViewerScreen(model = model, onDismiss = { selectedModel = null })
    }
}

// ── Sub-components ────────────────────────────────────────────────────────

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search 3D models on Sketchfab") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit(value) }),
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        } else null,
    )
}

@Composable
private fun FiltersBar(animatedOnly: Boolean, onToggle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = animatedOnly,
            onClick = onToggle,
            label = { Text("Animated") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        )
    }
}

@Composable
private fun CarouselSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun FeedSection(
    title: String,
    models: List<SketchfabModel>,
    loading: Boolean,
    onModelClick: (SketchfabModel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        if (models.isEmpty() && !loading) {
            Text(
                text = "Nothing here yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(models, key = { it.uid }) { m ->
                    FeaturedSketchfabCard(model = m, onClick = { onModelClick(m) })
                }
            }
        }
    }
}

@Composable
private fun SampleCard(sample: DemoEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = sample.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(12.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = sample.category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = sample.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun CategoriesSection(onCategoryClick: (SketchfabCategory) -> Unit) {
    val scrollState = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Categories",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Two-row flow: split the 18 categories across two horizontally-
        // scrolling rows so all chips remain visible without a grid layout
        // (which would need LazyVerticalGrid → conflicts with the parent
        // verticalScroll). Same compromise as the iOS demo.
        val all = SketchfabCategory.entries
        val rowSize = (all.size + 1) / 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                all.take(rowSize).forEach { CategoryChip(it, onCategoryClick) }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                all.drop(rowSize).forEach { CategoryChip(it, onCategoryClick) }
            }
        }
    }
}

@Composable
private fun CategoryChip(category: SketchfabCategory, onClick: (SketchfabCategory) -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clickable { onClick(category) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RecentSearchesSection(
    items: List<String>,
    onClear: () -> Unit,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text("Clear") }
        }
        items.forEach { query ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onClick(query) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(query) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove $query")
                }
            }
        }
    }
}

@Composable
private fun ApiKeyMissingNotice() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Column {
            Text(
                text = "Sketchfab gallery off",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Set SKETCHFAB_API_KEY (env or local.properties) to enable the live 3D catalog.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
