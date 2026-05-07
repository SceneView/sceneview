package io.github.sceneview.loaders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Regression contract for issue [#836](https://github.com/sceneview/sceneview/issues/836)
 * — "GLB Model loaded but not visible — Filament error: Missing TANGENTS attributes".
 *
 * The bug surfaced when a user-supplied GLB shipped only `POSITION` + `NORMAL` (no
 * `TANGENT`) and Filament's `gltfio` failed to synthesize tangents at load, leaving
 * the lit-PBR shader with a zero tangent frame and producing a black render.
 *
 * The runtime fix lives in `gltfio`'s auto-tangent path — `AssetLoader.createAsset`
 * computes a Mikkelsen tangent basis from positions + normals + UVs when `TANGENT` is
 * absent. That code is JNI-only and not callable from a pure JVM test.
 *
 * What this test pins instead:
 *   1. A canonical "minimal lit primitive without TANGENTS" GLB binary can be authored
 *      by hand (the [buildNoTangentsGlb] helper below). The binary's structural
 *      invariants — magic, version, chunk layout, primitive attributes — are the
 *      *contract* that gltfio's auto-tangent path must continue to handle.
 *   2. The fixture is correctly classified as TANGENT-less by any consumer that walks
 *      `mesh.primitives[*].attributes` (this is the property the user reported as
 *      working at upload time and broken at render time).
 *
 * Future work (tracked separately): replace the @Ignore'd render tests with a
 * SwiftShader-or-hardware path that loads this fixture via the real `ModelLoader`
 * and asserts pixels are non-black. See #803 for the render-test enablement issue.
 */
class NoTangentsGlbContractTest {

    @Test
    fun `synthetic GLB has valid magic header`() {
        val glb = buildNoTangentsGlb()
        glb.order(ByteOrder.LITTLE_ENDIAN)
        // glTF 2.0 GLB magic = ASCII "glTF" little-endian = 0x46546C67.
        assertEquals(0x46546C67, glb.getInt(0))
        // Version = 2 (only spec'd version as of glTF 2.0).
        assertEquals(2, glb.getInt(4))
        // Total length matches buffer size.
        assertEquals(glb.capacity(), glb.getInt(8))
    }

    @Test
    fun `synthetic GLB JSON chunk declares POSITION and NORMAL`() {
        val json = readJsonChunk(buildNoTangentsGlb())
        assertTrue(
            "Primitive must declare POSITION attribute (vertex positions)",
            json.contains("\"POSITION\""),
        )
        assertTrue(
            "Primitive must declare NORMAL attribute — auto-tangent synthesis " +
                "in gltfio requires normals + UVs as input.",
            json.contains("\"NORMAL\""),
        )
    }

    @Test
    fun `synthetic GLB JSON chunk does NOT declare TANGENT — this is the bug input`() {
        val json = readJsonChunk(buildNoTangentsGlb())
        // The whole point of this fixture: it represents the asset shape that
        // triggered #836. If a future change "fixes" the fixture by adding TANGENTS,
        // we lose the regression target.
        assertFalse(
            "Fixture must NOT declare TANGENT — it represents the GLB shape that " +
                "triggered #836. Adding TANGENT here makes the fixture useless as a " +
                "regression target for gltfio's auto-tangent synthesis path.",
            json.contains("\"TANGENT\""),
        )
    }

    @Test
    fun `synthetic GLB JSON chunk declares TEXCOORD_0 — auto-tangent needs UVs`() {
        val json = readJsonChunk(buildNoTangentsGlb())
        // Mikkelsen tangent basis = function of (position, normal, UV). Without UVs,
        // gltfio cannot synthesize tangents and the model still renders black —
        // which is a separate bug class. Pin UV presence so the fixture stays in the
        // "auto-tangent should work" regime.
        assertTrue(
            "Primitive must declare TEXCOORD_0 — gltfio's Mikkelsen tangent basis " +
                "synthesis requires UVs.",
            json.contains("\"TEXCOORD_0\""),
        )
    }

    @Test
    fun `synthetic GLB has both JSON and BIN chunks in spec order`() {
        val glb = buildNoTangentsGlb()
        glb.order(ByteOrder.LITTLE_ENDIAN)
        // First chunk header lives at offset 12 (after the 12-byte file header).
        val jsonLen = glb.getInt(12)
        val jsonType = glb.getInt(16)
        assertEquals("First chunk must be JSON (0x4E4F534A)", 0x4E4F534A, jsonType)
        // BIN chunk header sits right after the JSON chunk's 8-byte header + payload.
        val binChunkOffset = 12 + 8 + jsonLen
        val binType = glb.getInt(binChunkOffset + 4)
        assertEquals("Second chunk must be BIN (0x004E4942)", 0x004E4942, binType)
    }

    // ── Fixture builder ────────────────────────────────────────────────────────────

    /**
     * Reads the JSON chunk's UTF-8 payload from the GLB binary as a String. We use
     * `contains("\"FIELD\"")` rather than a JSON parser because `org.json` is the
     * Android stub here (throws RuntimeException in pure JVM tests) and pulling in
     * gson/moshi just for this fixture isn't worth it.
     */
    private fun readJsonChunk(glb: ByteBuffer): String {
        glb.rewind()
        glb.order(ByteOrder.LITTLE_ENDIAN)
        glb.position(12)
        val jsonLen = glb.int
        glb.int // skip type
        val jsonBytes = ByteArray(jsonLen)
        glb.get(jsonBytes)
        return String(jsonBytes, Charsets.UTF_8)
    }

    /**
     * Authors a minimal valid glTF 2.0 GLB binary in memory: a single triangle with
     * POSITION + NORMAL + TEXCOORD_0, intentionally **without** TANGENT.
     *
     * Binary layout (per glTF 2.0 §4.4):
     *   - 12-byte header: magic("glTF"), version(2), length
     *   - JSON chunk: header(8 bytes) + UTF-8 JSON manifest, padded to 4 bytes with spaces
     *   - BIN chunk:  header(8 bytes) + raw vertex data, padded to 4 bytes with zeros
     *
     * We use a single triangle (3 vertices) instead of a full cube to keep the
     * fixture under 200 bytes and the assertions easy to follow. The triangle has
     * the same attribute shape as a cube would — the regression target is the
     * absence of TANGENT, not the geometry topology.
     */
    private fun buildNoTangentsGlb(): ByteBuffer {
        // ── Vertex data (one triangle in XY plane facing +Z) ──────────────────────
        val positions = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f,
        )
        val normals = floatArrayOf(
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f,
        )
        val uvs = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
        )
        val indices = shortArrayOf(0, 1, 2)

        val posBytes = positions.size * 4
        val nrmBytes = normals.size * 4
        val uvBytes = uvs.size * 4
        val idxBytes = indices.size * 2
        // Pad indices to 4-byte boundary so the BIN chunk total is divisible by 4.
        val idxPad = (4 - idxBytes % 4) % 4
        val binTotal = posBytes + nrmBytes + uvBytes + idxBytes + idxPad

        val bin = ByteBuffer.allocate(binTotal).order(ByteOrder.LITTLE_ENDIAN)
        positions.forEach { bin.putFloat(it) }
        normals.forEach { bin.putFloat(it) }
        uvs.forEach { bin.putFloat(it) }
        indices.forEach { bin.putShort(it) }
        repeat(idxPad) { bin.put(0.toByte()) }

        // ── glTF JSON manifest (hand-built string — no JSON library dep) ──────────
        // Keep this on the canonical glTF 2.0 schema; the only intentional omission
        // is the "TANGENT" entry in the primitive attributes.
        val json = """
            {
              "asset": {"version": "2.0"},
              "buffers": [{"byteLength": $binTotal}],
              "bufferViews": [
                {"buffer": 0, "byteOffset": 0, "byteLength": $posBytes},
                {"buffer": 0, "byteOffset": $posBytes, "byteLength": $nrmBytes},
                {"buffer": 0, "byteOffset": ${posBytes + nrmBytes}, "byteLength": $uvBytes},
                {"buffer": 0, "byteOffset": ${posBytes + nrmBytes + uvBytes}, "byteLength": $idxBytes}
              ],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
                {"bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC3"},
                {"bufferView": 2, "componentType": 5126, "count": 3, "type": "VEC2"},
                {"bufferView": 3, "componentType": 5123, "count": 3, "type": "SCALAR"}
              ],
              "meshes": [{"primitives": [{"attributes": {"POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2}, "indices": 3, "mode": 4}]}],
              "nodes": [{"mesh": 0}],
              "scenes": [{"nodes": [0]}],
              "scene": 0
            }
        """.trimIndent()

        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        // Pad JSON to 4-byte boundary with trailing spaces (glTF spec).
        val jsonPad = (4 - jsonBytes.size % 4) % 4
        val paddedJson = ByteArray(jsonBytes.size + jsonPad)
        System.arraycopy(jsonBytes, 0, paddedJson, 0, jsonBytes.size)
        for (i in jsonBytes.size until paddedJson.size) paddedJson[i] = 0x20 // space

        // ── Assemble the GLB binary ───────────────────────────────────────────────
        val totalLength = 12 + 8 + paddedJson.size + 8 + binTotal
        val buf = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)
        // Header.
        buf.putInt(0x46546C67) // magic "glTF"
        buf.putInt(2) // version
        buf.putInt(totalLength)
        // JSON chunk.
        buf.putInt(paddedJson.size)
        buf.putInt(0x4E4F534A) // "JSON"
        buf.put(paddedJson)
        // BIN chunk.
        buf.putInt(binTotal)
        buf.putInt(0x004E4942) // "BIN\0"
        buf.put(bin.array())
        return buf
    }
}
