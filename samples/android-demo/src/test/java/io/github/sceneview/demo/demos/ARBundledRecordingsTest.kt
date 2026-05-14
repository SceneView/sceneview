package io.github.sceneview.demo.demos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Locks the bundled ARCore session datasets shipped under
 * `samples/android-demo/src/debug/assets/ar-recordings/` (every `.mp4`
 * file in that directory) against four silent-corruption modes that
 * would otherwise only surface as
 * "Mode.PLAYBACK shows nothing on the next user's emulator":
 *
 * 1. The assets directory exists.
 * 2. At least one `.mp4` is bundled (so the AR demos are exercisable
 *    out-of-the-box without a physical capture).
 * 3. Each `.mp4` opens, declares a non-zero size, and starts with the MP4
 *    `ftyp` box header — catches a 0-byte commit, a Git-LFS pointer file
 *    that was never resolved, or a truncated copy.
 * 4. Each `.mp4` carries the H.264 video track AND at least one ARCore
 *    metadata track (`mett` codec tag), without which `ARSession`'s
 *    `setPlaybackDataset` accepts the file but replays nothing.
 *
 * These checks run pure-JVM under Robolectric — no device, no ARCore SDK
 * call. They protect the README's "drop a file in here, the test
 * automatically picks it up" contribution flow.
 *
 * Memory: the bytes for each file are loaded ONCE per test class via
 * [bytesByName] and reused across the per-file assertions. With three or
 * four 16 MB recordings this cap (~80 MB) stays comfortably under the
 * Robolectric default heap of 256–512 MB. If the bundle ever grows past
 * that, switch to a streaming `BufferedInputStream` boyer-moore scan.
 */
@RunWith(RobolectricTestRunner::class)
class ARBundledRecordingsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun listBundled(): List<String> =
        context.assets.list("ar-recordings")
            ?.filter { it.endsWith(".mp4", ignoreCase = true) }
            ?: emptyList()

    private val bytesByName: Map<String, ByteArray> by lazy {
        listBundled().associateWith { name ->
            context.assets.open("ar-recordings/$name").use { it.readBytes() }
        }
    }

    @Test
    fun `assets ar-recordings directory ships at least one mp4`() {
        val files = listBundled()
        assertTrue(
            "Expected at least one bundled `.mp4` under " +
                "samples/android-demo/src/debug/assets/ar-recordings/. The AR demos " +
                "rely on bundled recordings so emulators / ARCore-less devices have " +
                "a known-good playback dataset. Found: $files",
            files.isNotEmpty(),
        )
    }

    @Test
    fun `every bundled mp4 starts with the MP4 ftyp box header`() {
        for ((name, bytes) in bytesByName) {
            assertTrue(
                "$name is suspiciously small (< 1 KB) — likely an empty commit or " +
                    "an unresolved Git-LFS pointer.",
                bytes.size >= 1024,
            )
            // ISO BMFF 14496-12: every MP4 starts with a `ftyp` box. Bytes 4-7
            // are the ASCII tag `ftyp`. The first 4 bytes are the box size.
            val tag = String(bytes.copyOfRange(4, 8))
            assertEquals(
                "$name does not start with an MP4 `ftyp` box — got tag '$tag'. " +
                    "File is corrupt or not an MP4 dataset.",
                "ftyp",
                tag,
            )
        }
    }

    @Test
    fun `every bundled mp4 carries an ARCore metadata track marker`() {
        for ((name, bytes) in bytesByName) {
            // ARCore `Session.startRecording` writes camera frames as `avc1`
            // (H.264) and emits ARCore-specific tracks tagged `mett` (per
            // ISO 14496-12, codec_tag for "metadata text" / generic data). A
            // session dataset MUST contain BOTH or `setPlaybackDataset`
            // succeeds-then-replays-nothing.
            val haveAvc1 = containsAscii(bytes, "avc1")
            val haveMett = containsAscii(bytes, "mett")
            assertTrue(
                "$name is missing the H.264 video track tag `avc1` — not an " +
                    "ARCore camera-frame dataset.",
                haveAvc1,
            )
            assertTrue(
                "$name is missing the ARCore metadata track tag `mett` — " +
                    "this is camera-only video, not a session dataset (replay " +
                    "would silently produce no anchors / no tracking).",
                haveMett,
            )
        }
    }

    @Test
    fun `recordings dir resolves to a real assets path`() {
        // Catches the trivial typo of renaming the assets dir without
        // updating the demo + test in lockstep.
        val list = context.assets.list("ar-recordings")
        assertNotNull("assets/ar-recordings/ directory not on the classpath", list)
    }

    private fun containsAscii(bytes: ByteArray, needle: String): Boolean {
        if (needle.isEmpty() || bytes.size < needle.length) return false
        val target = needle.toByteArray(Charsets.US_ASCII)
        outer@ for (i in 0..(bytes.size - target.size)) {
            for (j in target.indices) {
                if (bytes[i + j] != target[j]) continue@outer
            }
            return true
        }
        return false
    }
}
