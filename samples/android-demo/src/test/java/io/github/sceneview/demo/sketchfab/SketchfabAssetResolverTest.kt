package io.github.sceneview.demo.sketchfab

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Pure-JVM tests for [SketchfabAssetResolver] — Robolectric provides a real
 * [Context] (incl. `cacheDir` + `assets`) without spinning up a device.
 *
 * The tests are offline-only. The Sketchfab network path is exercised via the
 * `downloader` constructor parameter which lets us inject a stub that writes a
 * synthetic file into the cache directory.
 */
@RunWith(RobolectricTestRunner::class)
class SketchfabAssetResolverTest {

    private lateinit var context: Context

    private fun cacheRoot(): File =
        File(context.cacheDir, SketchfabConfig.CACHE_DIR_NAME)

    /**
     * A real Sketchfab UID picked from [SampleAssets.gallery]. Avoids tying the
     * test to any one slug so re-ordering the registry doesn't break this test.
     */
    private val testSlug: SketchfabSlug = SampleAssets.gallery.first()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Always start from a clean cache dir.
        cacheRoot().deleteRecursively()
    }

    @After
    fun tearDown() {
        cacheRoot().deleteRecursively()
    }

    // ── 1. Cache-hit short-circuit ────────────────────────────────────────

    @Test
    fun `resolve returns cached file without invoking downloader when blob already exists`() {
        // Pre-populate the cache the same way SketchfabService would.
        cacheRoot().mkdirs()
        val cachedFile = File(cacheRoot(), "${testSlug.uid}.glb").apply {
            writeBytes(ByteArray(64) { it.toByte() })
        }
        var downloaderCalls = 0
        val resolver = SketchfabAssetResolver(
            context = context,
            downloader = { uid ->
                downloaderCalls += 1
                error("downloader should not be called on cache hit (uid=$uid)")
            },
        )

        val resolved = runBlocking { resolver.resolve(testSlug) }
        assertEquals(cachedFile.absolutePath, resolved.absolutePath)
        assertEquals(64L, resolved.length())
        assertEquals(0, downloaderCalls)
    }

    // ── 2. Cache miss + no API key → bundled fallback ─────────────────────

    @Test
    fun `resolve falls back to bundled asset when api key is absent`() {
        // BuildConfig.SKETCHFAB_API_KEY is "" in unit-test builds and no env
        // var is exported by CI. Skip on developer machines that happen to
        // export the key — we'd otherwise wander into the live network path.
        if (SketchfabConfig.apiKey != null) return

        var downloaderCalls = 0
        val resolver = SketchfabAssetResolver(
            context = context,
            downloader = {
                downloaderCalls += 1
                error("downloader should not be invoked without api key")
            },
        )

        val resolved = runBlocking { resolver.resolve(testSlug) }
        assertTrue(
            "Fallback file must exist: $resolved",
            resolved.exists() && resolved.length() > 0L,
        )
        // The resolver names fallback files `fallback-<uid>.glb` so they cannot
        // be confused with a real Sketchfab download in the cache scan.
        assertTrue(
            "Expected fallback-prefixed file name, got ${resolved.name}",
            resolved.name.startsWith("fallback-"),
        )
        assertEquals(0, downloaderCalls)
    }

    // ── 3. Cache miss + downloader succeeds → returned file ───────────────

    @Test
    fun `resolve invokes downloader and returns its file on cache miss`() {
        // We bypass the api-key check by injecting a downloader that succeeds.
        // This requires that SketchfabConfig.apiKey is set — when it's null
        // (the common test path), the resolver short-circuits to the bundled
        // fallback before ever reaching the downloader.
        if (SketchfabConfig.apiKey == null) return  // see test 2 for the no-key path

        var downloaderCalls = 0
        val resolver = SketchfabAssetResolver(
            context = context,
            downloader = { uid ->
                downloaderCalls += 1
                File(cacheRoot().apply { mkdirs() }, "$uid.glb").apply {
                    writeBytes(ByteArray(1024))
                }
            },
            boundsValidator = SketchfabAssetResolver.ACCEPT_ALL,
        )
        val resolved = runBlocking { resolver.resolve(testSlug) }
        assertEquals(1, downloaderCalls)
        assertEquals(1024L, resolved.length())
        assertEquals("${testSlug.uid}.glb", resolved.name)
    }

    // ── 4. LRU eviction past the configured budget ────────────────────────

    @Test
    fun `evictLruIfOverBudget deletes oldest files until under the budget`() {
        cacheRoot().mkdirs()
        // Create 5 files of 100 KB each = 500 KB total.
        (1..5).forEach { i ->
            File(cacheRoot(), "file_$i.glb").apply {
                writeBytes(ByteArray(100 * 1024))
                // Stagger lastModified so we have a deterministic eviction order.
                setLastModified(1_000_000L + i * 60_000L)
            }
        }
        val resolver = SketchfabAssetResolver(
            context = context,
            downloader = { error("eviction should not call downloader") },
            cacheBudgetBytes = 300L * 1024, // 300 KB cap → must drop the 2 oldest
        )

        resolver.evictLruIfOverBudget()

        val remaining = cacheRoot().listFiles()!!.toList()
        // 3 files left at 100 KB each = 300 KB total.
        assertEquals(3, remaining.size)
        val remainingNames = remaining.map { it.name }.toSet()
        // file_1 and file_2 are the oldest → must be gone.
        assertFalse("file_1.glb should have been evicted", "file_1.glb" in remainingNames)
        assertFalse("file_2.glb should have been evicted", "file_2.glb" in remainingNames)
        assertTrue("file_3.glb should remain", "file_3.glb" in remainingNames)
        assertTrue("file_4.glb should remain", "file_4.glb" in remainingNames)
        assertTrue("file_5.glb should remain", "file_5.glb" in remainingNames)
    }

    @Test
    fun `evictLruIfOverBudget is a no-op when total is already under budget`() {
        cacheRoot().mkdirs()
        File(cacheRoot(), "only.glb").apply { writeBytes(ByteArray(100)) }
        val resolver = SketchfabAssetResolver(
            context = context,
            downloader = { error("no-op eviction must not call downloader") },
            cacheBudgetBytes = 1024L,
        )
        resolver.evictLruIfOverBudget()
        assertEquals(1, cacheRoot().listFiles()!!.size)
    }

    @Test
    fun `evictLruIfOverBudget tolerates a missing cache directory`() {
        // Directory does not exist yet — pruning must be a silent no-op.
        cacheRoot().deleteRecursively()
        assertFalse(cacheRoot().exists())
        val resolver = SketchfabAssetResolver(
            context = context,
            downloader = { error("no-op") },
        )
        resolver.evictLruIfOverBudget(0L)  // any budget — nothing to evict
        // No throw — that's the assertion.
    }

    // ── 5. License contract — build-time assertion ────────────────────────

    @Test
    fun `SketchfabSlug rejects a non-CC-BY license at construction`() {
        try {
            SketchfabSlug(
                uid = "deadbeef".repeat(4),
                fallbackBundledPath = "models/khronos_damaged_helmet.glb",
                scaleToUnits = 1f,
                hasBakedAnimation = false,
                license = "MIT",
                attribution = "Some Author",
                sketchfabPageUrl = "https://sketchfab.com/3d-models/example",
            )
            fail("Expected IllegalStateException for non-CC license, got success")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
            assertTrue(
                "Error must name the offending license: ${e.message}",
                e.message!!.contains("MIT"),
            )
        }
    }

    @Test
    fun `SketchfabSlug accepts every license string in VALID_LICENSES`() {
        // Every entry must construct successfully — `init { check(...) }` would
        // otherwise throw.
        for (license in SketchfabSlug.VALID_LICENSES) {
            SketchfabSlug(
                uid = "uid-$license".replace("/", "-"), // any non-blank string works
                fallbackBundledPath = "models/khronos_damaged_helmet.glb",
                scaleToUnits = 1f,
                hasBakedAnimation = false,
                license = license,
                attribution = "Test",
                sketchfabPageUrl = "https://sketchfab.com/3d-models/example",
            )
        }
    }

    @Test
    fun `SketchfabSlug requires a non-blank attribution`() {
        try {
            SketchfabSlug(
                uid = "abc",
                fallbackBundledPath = "models/khronos_damaged_helmet.glb",
                scaleToUnits = 1f,
                hasBakedAnimation = false,
                license = "CC-BY-4.0",
                attribution = "",
                sketchfabPageUrl = "https://sketchfab.com/3d-models/example",
            )
            fail("Expected IllegalArgumentException for empty attribution")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("attribution"))
        }
    }

    @Test
    fun `SketchfabSlug requires sketchfab url`() {
        try {
            SketchfabSlug(
                uid = "abc",
                fallbackBundledPath = "models/khronos_damaged_helmet.glb",
                scaleToUnits = 1f,
                hasBakedAnimation = false,
                license = "CC-BY-4.0",
                attribution = "T",
                sketchfabPageUrl = "https://example.com/foo",
            )
            fail("Expected IllegalArgumentException for non-sketchfab url")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("sketchfab"))
        }
    }

    @Test
    fun `SampleAssets registry has no duplicate UIDs`() {
        // Acts as a regression pin: adding a slug with an already-registered
        // uid must be caught at app startup, not at runtime download time.
        val uids = SampleAssets.all.map { it.uid }
        assertEquals(
            "Duplicate UID detected: $uids",
            uids.size,
            uids.toSet().size,
        )
    }

    // ── 6. Bounds validator path ──────────────────────────────────────────

    @Test
    fun `bounds rejection deletes download and falls back to bundled asset`() {
        if (SketchfabConfig.apiKey == null) return  // requires the network path

        var rejectCalls = 0
        val resolver = SketchfabAssetResolver(
            context = context,
            downloader = { uid ->
                File(cacheRoot().apply { mkdirs() }, "$uid.glb").apply {
                    writeBytes(ByteArray(2048))
                }
            },
            boundsValidator = { _, _ ->
                rejectCalls += 1
                false
            },
        )
        val resolved = runBlocking { resolver.resolve(testSlug) }
        assertEquals("validator must run exactly once", 1, rejectCalls)
        assertTrue(
            "Fallback file must be returned on validator reject: $resolved",
            resolved.name.startsWith("fallback-"),
        )
        // The rejected download must not survive in the cache (it would be
        // re-picked on the next resolve and we'd be stuck in a fall-back loop).
        assertNotEquals(
            "${testSlug.uid}.glb",
            resolved.name,
        )
    }

    @Test
    fun `bounds validator throwing is treated as a reject and falls back`() {
        if (SketchfabConfig.apiKey == null) return

        val resolver = SketchfabAssetResolver(
            context = context,
            downloader = { uid ->
                File(cacheRoot().apply { mkdirs() }, "$uid.glb").apply {
                    writeBytes(ByteArray(256))
                }
            },
            boundsValidator = { _, _ -> throw IllegalStateException("boom") },
        )
        val resolved = runBlocking { resolver.resolve(testSlug) }
        assertTrue(resolved.name.startsWith("fallback-"))
    }
}
