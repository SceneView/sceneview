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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Static About screen described in CLAUDE.md (audit #948 — strings.xml shipped 16 about_*
 * keys but no composable consumed them). Renders the SceneView identity, three feature
 * cards (3D / AR / AI-first), platform footprint, and credits — all sourced from the
 * existing string resources so localization stays in res/.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 0.dp),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            HeroBlock(versionName = BuildConfig.VERSION_NAME)
            Spacer(Modifier.height(24.dp))
            FeatureGrid()
            Spacer(Modifier.height(24.dp))
            PlatformsBlock()
            Spacer(Modifier.height(24.dp))
            LinksRow(
                onGithub = { uriHandler.openUri("https://github.com/sceneview/sceneview") },
                onDocs = { uriHandler.openUri("https://sceneview.github.io") },
            )
            Spacer(Modifier.height(24.dp))
            CreditsBlock(
                onModelsLinkClick = {
                    uriHandler.openUri(context.getString(R.string.about_credits_models_url))
                },
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun HeroBlock(versionName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Brand mark — gradient tile with "SV" monogram. We render it inline rather
        // than bundling another logo asset since the About screen is the only place
        // it shows up; the launcher already ships the proper adaptive icon.
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(SceneViewColors.Primary, SceneViewColors.Accent),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "SV",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.about_sceneview),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = stringResource(R.string.about_version, versionName),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun FeatureGrid() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeatureCard(
            emoji = "🧊",
            title = stringResource(R.string.about_feature_3d_title),
            description = stringResource(R.string.about_feature_3d_desc),
        )
        FeatureCard(
            emoji = "📱",
            title = stringResource(R.string.about_feature_ar_title),
            description = stringResource(R.string.about_feature_ar_desc),
        )
        FeatureCard(
            emoji = "🤖",
            title = stringResource(R.string.about_feature_ai_title),
            description = stringResource(R.string.about_feature_ai_desc),
        )
    }
}

@Composable
private fun FeatureCard(emoji: String, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(text = emoji, fontSize = 28.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun PlatformsBlock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.about_platforms_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        // Static list — we don't have a single source of truth for the supported-platform
        // count beyond CLAUDE.md, but these match the docs as of v4.0.9.
        val platforms = remember {
            listOf(
                "Android (Jetpack Compose)",
                "Android TV",
                "iOS (SwiftUI)",
                "macOS",
                "visionOS",
                "Web (Filament.js / WebGL2)",
                "Flutter (PlatformView bridge)",
                "React Native (Fabric bridge)",
                "Desktop (Compose placeholder)",
            )
        }
        platforms.forEach { name ->
            Text(
                text = "• $name",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun LinksRow(onGithub: () -> Unit, onDocs: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onGithub,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.about_github))
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        OutlinedButton(
            onClick = onDocs,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.about_docs))
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CreditsBlock(onModelsLinkClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.about_credits_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.about_credits_filament),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.about_credits_models),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_credits_models_link),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onModelsLinkClick() }
                .padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.about_credits_license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
    }
}

