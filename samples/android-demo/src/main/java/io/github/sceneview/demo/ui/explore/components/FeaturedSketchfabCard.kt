package io.github.sceneview.demo.ui.explore.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sceneview.demo.sketchfab.SketchfabModel

/**
 * Horizontal-carousel card for a Sketchfab model. Mirrors the iOS
 * `FeaturedSketchfabCard` in `ExploreTab.swift`: 200×160 thumbnail, name,
 * primary tag, poly count, and an "Animated" pill when applicable.
 *
 * Tap → opens the [io.github.sceneview.demo.ui.explore.SketchfabModelViewerScreen]
 * which renders the GLB inside SceneView. The Sketchfab viewer is NEVER
 * opened externally — the whole point of the demo is to show off SceneView.
 */
@Composable
fun FeaturedSketchfabCard(
    model: SketchfabModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(200.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            AsyncNetworkImage(
                url = model.preferredThumbnailUrl(minWidth = 320, maxWidth = 640),
                contentDescription = model.name,
                modifier = Modifier.size(width = 200.dp, height = 160.dp),
                contentScale = ContentScale.Crop,
            )
            if (model.isAnimated) {
                AnimatedPill(modifier = Modifier.padding(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = model.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Text(
                text = model.primaryTagDisplay(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (model.faceCount > 0) {
                Text(
                    text = " · ${model.formattedFaceCount()} polys",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AnimatedPill(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.92f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Animated",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Display helpers on SketchfabModel ─────────────────────────────────────

/** Pick a thumbnail close to the card's render size. */
fun SketchfabModel.preferredThumbnailUrl(minWidth: Int = 320, maxWidth: Int = 640): String? {
    val images = thumbnails.images
    val sweetSpot = images.firstOrNull { it.width in minWidth..maxWidth }
    return (sweetSpot ?: images.maxByOrNull { it.width } ?: images.firstOrNull())?.url
}

/** First tag in Title Case, or a generic fallback. */
fun SketchfabModel.primaryTagDisplay(): String =
    tags?.firstOrNull()?.name?.replaceFirstChar { it.titlecase() } ?: "3D Model"

fun SketchfabModel.formattedFaceCount(): String = when {
    faceCount >= 1_000_000 -> String.format("%.1fM", faceCount / 1_000_000.0)
    faceCount >= 1_000 -> String.format("%.1fk", faceCount / 1_000.0)
    else -> faceCount.toString()
}
