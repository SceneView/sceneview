package io.github.sceneview.demo

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pure-JVM tests for [DeepLinkRouter]. Robolectric is the cheapest path
 * to a real `android.net.Uri` parser without spinning up a device.
 *
 * Tests the **end-to-end intent → demo id** lookup, including the
 * registry guard so we can assert that fuzzed / spoofed deep links
 * fall through to `null` (= the activity falls back to the demo list).
 */
@RunWith(RobolectricTestRunner::class)
class DeepLinkRouterTest {

    private val knownRegistry = listOf(
        DemoEntry("ar-rerun", "AR Debug", "", "Augmented Reality"),
        DemoEntry("model-viewer", "Model Viewer", "", "3D Basics"),
    )

    // ── Custom scheme: sceneview://demo/<id> ──────────────────────────────

    @Test
    fun `custom scheme resolves a known demo id`() {
        val uri = Uri.parse("sceneview://demo/ar-rerun")
        assertEquals("ar-rerun", DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `custom scheme returns null for an unknown demo id (guards against fuzzing)`() {
        val uri = Uri.parse("sceneview://demo/totally-not-a-demo")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `custom scheme tolerates uppercase scheme and host`() {
        val uri = Uri.parse("SCENEVIEW://Demo/model-viewer")
        assertEquals("model-viewer", DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `custom scheme returns null for missing path segment`() {
        val uri = Uri.parse("sceneview://demo")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `custom scheme returns null for wrong host`() {
        val uri = Uri.parse("sceneview://playground/ar-rerun")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    // ── HTTPS App-Links: sceneview.github.io/open?demo=<id> ───────────────

    @Test
    fun `https app link resolves a known demo id from the demo query parameter`() {
        val uri = Uri.parse("https://sceneview.github.io/open?demo=ar-rerun")
        assertEquals("ar-rerun", DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `https app link returns null when demo query param is missing`() {
        val uri = Uri.parse("https://sceneview.github.io/open")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `https app link returns null for the wrong path`() {
        val uri = Uri.parse("https://sceneview.github.io/playground?demo=ar-rerun")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `https app link returns null for the wrong host`() {
        val uri = Uri.parse("https://example.com/open?demo=ar-rerun")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    // ── Fall-through cases ────────────────────────────────────────────────

    @Test
    fun `null uri returns null`() {
        assertNull(DeepLinkRouter.parse(null, knownRegistry))
    }

    @Test
    fun `unsupported scheme returns null`() {
        val uri = Uri.parse("ftp://demo/ar-rerun")
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }

    @Test
    fun `extractCandidate exposes the raw id without registry validation`() {
        // Round-trip through the URI parser to cover a path other tests
        // don't (the bare-id extraction is the security-sensitive bit; we
        // want it covered independently of the registry).
        val uri = Uri.parse("sceneview://demo/some-future-id-not-yet-shipped")
        assertEquals("some-future-id-not-yet-shipped", DeepLinkRouter.extractCandidate(uri))
        // …but the public API still gates it on the registry:
        assertNull(DeepLinkRouter.parse(uri, knownRegistry))
    }
}
