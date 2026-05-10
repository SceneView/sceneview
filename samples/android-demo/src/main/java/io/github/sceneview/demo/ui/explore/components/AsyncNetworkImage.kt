package io.github.sceneview.demo.ui.explore.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Minimal async network-image composable used by the Explore tab cards.
 *
 * Why not Coil / Glide?
 *   The demo app does not yet depend on a Compose image-loading library and
 *   we don't want to grow the APK by ~600 KB just for thumbnails (the Play
 *   Store cares about install size — every feature trades against the next).
 *   The Sketchfab gallery only ever decodes ~30 small JPEGs at a time so a
 *   one-file fetcher backed by `OkHttp` + an in-memory `LruCache` is enough.
 *
 *   When we add Coil for other reasons (e.g. animated WebP support), swap
 *   this single composable out — every call site uses the same surface
 *   (`url`, `contentDescription`, `modifier`).
 */
@Composable
fun AsyncNetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(BitmapCache[url.orEmpty()]) }
    var loading by remember(url) { mutableStateOf(bitmap == null && !url.isNullOrBlank()) }

    LaunchedEffect(url) {
        val key = url ?: return@LaunchedEffect
        if (BitmapCache[key] != null) {
            bitmap = BitmapCache[key]
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val decoded = withContext(Dispatchers.IO) {
            runCatching { fetchBitmap(key) }.getOrNull()
        }
        if (decoded != null) {
            BitmapCache.put(key, decoded)
            bitmap = decoded
        }
        loading = false
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp,
            )
        }
        // else: silent placeholder — empty url or failed fetch.
    }
}

/** Shared OkHttp client. The Sketchfab CDN does not require auth headers. */
private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder().build()
}

/** ~12 MB bitmap cache — covers ~30 cards at 200x160 ARGB_8888. */
private object BitmapCache : LruCache<String, Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

@Throws(IOException::class)
private fun fetchBitmap(url: String): Bitmap? {
    val request = Request.Builder().url(url).get().build()
    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        val bytes = response.body.bytes()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
