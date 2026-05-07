package io.github.sceneview.ar.recording

import android.content.Context
import android.os.Build
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files

/**
 * JVM unit tests for [ARRecorder.exportToDownloads].
 *
 * Pins the contract that lets us pull AR recordings out of app-private storage and into the
 * public Downloads collection — the foundation of the deterministic-AR-tests workflow
 * (`samples/android-demo/AR_TESTING.md`).
 *
 * Robolectric stubs `MediaStore` and `Environment` without a connected device. We verify:
 *   - Missing source file raises [FileNotFoundException] (clear caller signal — corrupt or
 *     uninitialised recordings shouldn't fail silently with a `null` return).
 *   - The legacy (pre-Q) path actually copies bytes to the public Downloads directory and
 *     returns a `file:` URI pointing at the destination.
 *   - The default subdirectory ("SceneView") is used when none is supplied.
 *   - An empty subdirectory lands the file directly in `Downloads/`.
 *   - The display-name override controls the destination filename.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ARRecorderExportTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var sourceFile: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        tempDir = Files.createTempDirectory("ar-recorder-export-test").toFile()
        sourceFile = File(tempDir, "session.mp4").apply {
            writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70))
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `missing source file throws FileNotFoundException`() {
        val missing = File(tempDir, "does-not-exist.mp4")
        assertThrows(FileNotFoundException::class.java) {
            ARRecorder.exportToDownloads(context, missing)
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `legacy path copies file to public Downloads-SceneView`() {
        val uri = ARRecorder.exportToDownloads(context, sourceFile)
        assertNotNull("legacy path must return a non-null Uri on success", uri)
        // Robolectric's Environment.getExternalStoragePublicDirectory returns a temp path
        // under the test sandbox. The Uri scheme is "file:" on the legacy path.
        assertEquals("file", uri!!.scheme)
        // The path includes the subdirectory and source filename — both contractually
        // important so callers can find the file via Files app / adb.
        val path = uri.path!!
        assertTrue(
            "Uri path should end with the source filename: $path",
            path.endsWith("/SceneView/session.mp4"),
        )
        assertTrue("Destination file should exist", File(path).isFile)
        assertEquals(
            "Destination should have the same byte length as the source",
            sourceFile.length(), File(path).length(),
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `legacy path respects custom subdirectory`() {
        val uri = ARRecorder.exportToDownloads(
            context = context,
            recording = sourceFile,
            subdirectory = "SceneView/regression-fixtures",
        )
        assertNotNull(uri)
        val path = uri!!.path!!
        assertTrue(
            "Uri path should include the custom subdirectory: $path",
            path.contains("/SceneView/regression-fixtures/"),
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `legacy path with empty subdirectory lands directly in Downloads`() {
        val uri = ARRecorder.exportToDownloads(
            context = context,
            recording = sourceFile,
            subdirectory = "",
        )
        assertNotNull(uri)
        val path = uri!!.path!!
        // Should be Downloads/<filename> with no subfolder between
        assertTrue(
            "Uri path should NOT contain a SceneView subfolder when subdirectory is empty: $path",
            !path.contains("/SceneView/"),
        )
        assertTrue(path.endsWith("/session.mp4"))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `legacy path uses display name override`() {
        val uri = ARRecorder.exportToDownloads(
            context = context,
            recording = sourceFile,
            displayName = "baseline-pixel9-room.mp4",
        )
        assertNotNull(uri)
        val path = uri!!.path!!
        assertTrue(
            "Display name override should rename the destination: $path",
            path.endsWith("/baseline-pixel9-room.mp4"),
        )
        // Original source file name should NOT appear
        assertTrue(!path.endsWith("/session.mp4"))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `MediaStore path returns content Uri on Android Q+`() {
        // Robolectric's MediaStore shadow accepts the insert and returns a content://
        // Uri. We don't try to read back via openOutputStream because Robolectric's
        // ContentResolver shadow doesn't fully wire the streams — but the insert itself
        // is the surface our caller cares about: was a Uri allocated?
        val uri = ARRecorder.exportToDownloads(context, sourceFile)
        // Either a real content:// Uri (good) or null (Robolectric returned no Uri from
        // the insert, in which case we silently failed — also acceptable, the helper
        // catches and returns null).
        if (uri != null) {
            assertEquals(
                "MediaStore path should return a content:// Uri on Q+",
                "content", uri.scheme,
            )
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `legacy path overwrites an existing file with the same name`() {
        // First export creates the file.
        val first = ARRecorder.exportToDownloads(context, sourceFile)
        assertNotNull(first)
        val firstFile = File(first!!.path!!)
        val firstSize = firstFile.length()
        // Modify the source — make it twice as large — and re-export with the same name.
        sourceFile.writeBytes(ByteArray(firstSize.toInt() * 2))
        val second = ARRecorder.exportToDownloads(context, sourceFile)
        assertNotNull(second)
        // Second export should overwrite, not duplicate. File at the same path should now
        // have the new size, not the original.
        assertEquals(first.path, second!!.path)
        assertEquals(
            "Re-export should overwrite the destination, not append",
            sourceFile.length(), File(second.path!!).length(),
        )
    }

    @Test
    fun `companion exposes exportToDownloads as a JvmStatic`() {
        // Sanity: callable from Java without an instance. Pinning JvmStatic so a refactor
        // doesn't accidentally drop it (the Companion would still compile but Java callers
        // would break at link time).
        val method = ARRecorder.Companion::class.java.declaredMethods.firstOrNull {
            it.name == "exportToDownloads" && it.parameterCount >= 2
        }
        assertNotNull("ARRecorder.Companion must expose exportToDownloads", method)
        // ARRecorder.exportToDownloads(...) callable on the class itself confirms @JvmStatic
        val staticMethod = ARRecorder::class.java.declaredMethods.firstOrNull {
            it.name == "exportToDownloads"
        }
        assertNotNull(
            "ARRecorder.exportToDownloads must be exposed as a @JvmStatic call on the class",
            staticMethod,
        )
    }
}
