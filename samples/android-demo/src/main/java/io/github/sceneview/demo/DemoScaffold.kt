@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.sceneview.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Shared scaffold for all demo screens — version 2 (modal bottom sheet).
 *
 * Renders the 3D scene **full-screen** under the top bar, with the user controls
 * tucked behind a ModalBottomSheet — a settings FAB and a peek chip make the
 * controls discoverable without stealing viewport real estate from the showcase.
 *
 * - `controls == null` → no FAB / no sheet, scene fills the entire area below the top bar.
 * - `controls != null` → a `Tune` FAB pinned to the bottom-end opens the controls
 *   sheet. While the sheet is closed, a small peek chip ("Settings") sits above
 *   the FAB to advertise the gesture (see [#951] discoverability lesson).
 *
 * Gestures:
 * - **Tap FAB / tap peek chip** → opens the sheet at its partial detent.
 * - **Long-press FAB** → toggles `DemoSettings.qaMode` (deterministic captures
 *   for screenshot suites). Previously this gesture lived on the top-app-bar
 *   title; moved to the FAB so the title can carry the demo name verbatim.
 * - **Drag handle / outside tap / back gesture** → dismiss the sheet.
 * - **AR**: opening the sheet does NOT pause the AR session (the sheet sits on
 *   top of the existing scene; AR keeps tracking 6DOF underneath).
 *
 * The sheet content is rendered inside a vertically-scrolling Column so the same
 * `controls = { ... }` blocks that worked with the v1 side-panel work unchanged
 * — 35 demo call-sites stay byte-identical.
 */
@Composable
fun DemoScaffold(
    title: String,
    onBack: () -> Unit,
    controls: (@Composable ColumnScope.() -> Unit)? = null,
    scene: @Composable BoxScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row {
                        Text(title)
                        if (DemoSettings.qaMode) {
                            // Tappable QA pill: tap to disable, so a user who
                            // long-pressed the FAB by accident has a
                            // single-tap escape hatch instead of having to
                            // guess that another long-press toggles it back
                            // off. See #951.
                            Text(
                                text = " QA ×",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        haptic.performHapticFeedback(
                                            HapticFeedbackType.LongPress
                                        )
                                        DemoSettings.qaMode = false
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag(DemoScaffoldTestTags.QA_PILL),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button)
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
        // Scene always full-screen below the top app bar.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(modifier = Modifier.fillMaxSize(), content = scene)

            if (controls != null) {
                DemoSettingsLayer(
                    controlsContent = controls,
                    haptic = haptic,
                )
            }
        }
    }
}

/**
 * Stable test tags consumed by `DemoInteractionTest` and any future visual smoke
 * tooling so the controls sheet can be opened deterministically without relying
 * on accessibility-tree heuristics. Kept in the public surface of this file so
 * tests can `import io.github.sceneview.demo.DemoScaffoldTestTags`.
 */
object DemoScaffoldTestTags {
    const val SETTINGS_FAB = "demo-settings-fab"
    const val SETTINGS_PEEK = "demo-settings-peek"
    const val SETTINGS_SHEET = "demo-settings-sheet"
    const val QA_PILL = "demo-qa-pill"
}

/**
 * Peek chip + FAB + ModalBottomSheet — pulled into its own composable so the
 * sheet `LaunchedEffect`s scope to the `expanded` state without re-running on
 * every recomposition of the parent Scaffold body.
 */
@Composable
private fun BoxScope.DemoSettingsLayer(
    controlsContent: @Composable ColumnScope.() -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sheetState: SheetState = rememberModalBottomSheetState(
        // partially expanded is the default open state — keeps ~45 % of viewport
        // visible so the showcase stays alive while you tweak settings.
        skipPartiallyExpanded = false,
    )
    val scope = rememberCoroutineScope()

    // FAB + peek chip pinned to the bottom-end of the scene area.
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Peek chip — only shown while the sheet is closed. Tap = open at the
        // partial detent (same as tapping the FAB). The chip is intentionally
        // semi-transparent so it disappears against busy 3D scenes but stays
        // legible on plain backgrounds.
        if (!expanded) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        expanded = true
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .semantics { contentDescription = "Open demo settings" }
                    .testTag(DemoScaffoldTestTags.SETTINGS_PEEK),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = " Settings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        FloatingActionButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                expanded = true
            },
            shape = CircleShape,
            modifier = Modifier
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        expanded = true
                    },
                    onLongClick = {
                        // Long-press FAB = toggle QA mode (deterministic captures).
                        // Previously on the top-bar title — moved here so the
                        // title can carry the demo name verbatim. See #951.
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        DemoSettings.qaMode = !DemoSettings.qaMode
                    },
                )
                .semantics { contentDescription = "Demo settings" }
                .testTag(DemoScaffoldTestTags.SETTINGS_FAB),
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
            )
        }
    }

    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    sheetState.hide()
                    expanded = false
                }
            },
            sheetState = sheetState,
            modifier = Modifier.testTag(DemoScaffoldTestTags.SETTINGS_SHEET),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                content = controlsContent,
            )
        }

        // Medium-tick haptic when the user moves between detents.
        LaunchedEffect(sheetState.currentValue) {
            when (sheetState.currentValue) {
                SheetValue.Expanded,
                SheetValue.PartiallyExpanded -> {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                SheetValue.Hidden -> {
                    expanded = false
                }
            }
        }
    }
}
