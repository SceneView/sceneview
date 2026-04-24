@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.sceneview.demo

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize

/**
 * Shared scaffold for all demo screens.
 *
 * - [controls] == null → [scene] fills the entire screen below the top bar.
 * - [controls] != null → [scene] takes ~60 % (weight 3) and the controls
 *   panel takes ~40 % (weight 2) with vertical scrolling.
 */
@Composable
fun DemoScaffold(
    title: String,
    onBack: () -> Unit,
    controls: (@Composable ColumnScope.() -> Unit)? = null,
    scene: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Long-press the title to toggle QA mode — showcase camera
                    // animations freeze, which is what test harnesses want.
                    Row(
                        modifier = Modifier
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {},
                                onLongClick = {
                                    DemoSettings.qaMode = !DemoSettings.qaMode
                                },
                            )
                    ) {
                        Text(title)
                        if (DemoSettings.qaMode) {
                            Text(
                                text = " QA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .wrapContentSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        if (controls != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Scene — 60 %
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(3f),
                    content = scene
                )

                HorizontalDivider()

                // Controls — 40 %
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    content = controls
                )
            }
        } else {
            // Full-screen scene
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                content = scene
            )
        }
    }
}
