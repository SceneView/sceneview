package io.github.sceneview.sample.common.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive banner that surfaces a Play in-app update in progress.
 *
 * No-op while [InAppUpdateManager.updateState] is `IDLE` / `CHECKING` /
 * `AVAILABLE` / `UP_TO_DATE`. Renders a progress card during `DOWNLOADING` and a
 * primary "Restart" CTA once the install reaches `READY_TO_INSTALL`.
 *
 * The banner is intentionally rounded + edge-aligned (24 dp radius, 16 dp inset)
 * so it overlays cleanly on top of any sample UI — including the full-bleed
 * SceneView surface — without competing with primary CTAs.
 *
 * @param restartFocusRequester optional [FocusRequester] for the "Restart" CTA.
 * D-pad hosts (Android TV) should pass one in: when the install reaches
 * `READY_TO_INSTALL` the button is focused automatically so the Leanback user
 * can act without hunting for it. Phone hosts leave this `null` — touch users
 * tap the button regardless, and an unsolicited focus request would be inert.
 */
@Composable
fun UpdateBanner(
    updateManager: InAppUpdateManager,
    modifier: Modifier = Modifier,
    restartFocusRequester: FocusRequester? = null,
) {
    val showBanner = updateManager.updateState == InAppUpdateManager.UpdateState.DOWNLOADING
            || updateManager.updateState == InAppUpdateManager.UpdateState.READY_TO_INSTALL

    AnimatedVisibility(
        visible = showBanner,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (updateManager.updateState) {
                    InAppUpdateManager.UpdateState.READY_TO_INSTALL ->
                        MaterialTheme.colorScheme.primaryContainer
                    else ->
                        MaterialTheme.colorScheme.secondaryContainer
                }
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (updateManager.updateState) {
                                InAppUpdateManager.UpdateState.DOWNLOADING -> "Downloading update…"
                                InAppUpdateManager.UpdateState.READY_TO_INSTALL -> "Update ready!"
                                else -> ""
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (updateManager.updateState == InAppUpdateManager.UpdateState.DOWNLOADING) {
                            Text(
                                text = "${(updateManager.downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    if (updateManager.updateState == InAppUpdateManager.UpdateState.READY_TO_INSTALL) {
                        // Auto-focus the Restart CTA for D-pad hosts. Keyed on
                        // `updateState` so it fires exactly once on the
                        // IDLE/DOWNLOADING -> READY_TO_INSTALL transition, not
                        // on every recomposition. No-op for phone hosts, which
                        // pass `restartFocusRequester == null`.
                        if (restartFocusRequester != null) {
                            LaunchedEffect(updateManager.updateState) {
                                restartFocusRequester.requestFocus()
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { updateManager.completeUpdate() },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = if (restartFocusRequester != null) {
                                Modifier.focusRequester(restartFocusRequester)
                            } else {
                                Modifier
                            }
                        ) {
                            Text("Restart")
                        }
                    }
                }

                if (updateManager.updateState == InAppUpdateManager.UpdateState.DOWNLOADING) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { updateManager.downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
