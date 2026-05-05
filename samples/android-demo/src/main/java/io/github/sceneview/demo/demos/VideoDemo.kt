package io.github.sceneview.demo.demos

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.demo.DemoScaffold
import io.github.sceneview.demo.rememberHeroOrbitCameraManipulator
import io.github.sceneview.environment.rememberHDREnvironment
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader

/**
 * Demonstrates [VideoNode] — a flat quad inside a 3D scene that plays a streaming video
 * with audio. The camera slowly orbits the quad against an HDR-lit environment so the
 * playback feels embedded in real 3D space rather than glued to the screen.
 *
 * Source: Big Buck Bunny — © Blender Foundation, CC-BY 3.0.
 * Streamed from Google's public sample bucket so the APK stays slim. Requires the
 * [INTERNET][android.Manifest.permission.INTERNET] permission (already declared in the
 * sample manifest).
 */
@Composable
fun VideoDemo(onBack: () -> Unit) {
    var isPlaying by remember { mutableStateOf(true) }
    var isReady by remember { mutableStateOf(false) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    // HDR environment for IBL — same studio lightbox AnimationDemo uses, with the
    // skybox disabled so the wraparound image doesn't fight the video. We just want
    // the lighting / reflection contribution so the quad sits in a lit "space".
    val hdrEnvironment = rememberHDREnvironment(
        environmentLoader,
        "environments/studio_warm_2k.hdr",
        createSkybox = false,
    )
    val fallbackEnvironment = rememberEnvironment(environmentLoader)
    val activeEnvironment = hdrEnvironment ?: fallbackEnvironment

    // Manual MediaPlayer — `rememberMediaPlayer` only accepts asset paths, but we want
    // streaming so the APK stays slim. `prepareAsync` keeps the main thread responsive
    // while the network buffer fills; the quad shows black until `setOnPreparedListener`
    // fires.
    val player = remember {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            isLooping = true
            setDataSource(
                // Big Buck Bunny by Blender Foundation (CC-BY 3.0). The previously-used
                // commondatastorage.googleapis.com mirror started returning 403 in 2026,
                // so we point at the W3Schools mirror — same content, ~788 kB MP4 with
                // audio, well-known and stable.
                "https://www.w3schools.com/html/mov_bbb.mp4"
            )
            setOnPreparedListener {
                isReady = true
                start()
            }
            prepareAsync()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Slow hero orbit so the quad reads as a 3D surface in the lit environment.
    val cameraManipulator = rememberHeroOrbitCameraManipulator(
        trigger = isReady,
        radius = 2.2f,
        yHeight = 0.3f,
        durationMillis = 18_000,
    )

    DemoScaffold(
        title = "Video",
        onBack = onBack,
        controls = {
            Text("Playback", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            IconButton(onClick = {
                isPlaying = !isPlaying
                if (isReady) {
                    if (isPlaying) player.start() else player.pause()
                }
            }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Big Buck Bunny — © Blender Foundation (CC-BY 3.0)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            environment = activeEnvironment,
            cameraManipulator = cameraManipulator,
        ) {
            // VideoNode auto-sizes the quad to the streaming video's aspect ratio.
            // The first frames render black until prepareAsync completes.
            VideoNode(player = player)
        }
    }
}
