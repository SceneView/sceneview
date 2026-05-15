package io.github.sceneview.web

import io.github.sceneview.web.nodes.GeometryConfig
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [GeometryGLBBuilder] — the in-memory glTF-Binary builder that
 * turns a [GeometryConfig] into a valid GLB buffer for the gltfio pipeline.
 *
 * `buildGLB` is pure: it touches only `ArrayBuffer`/`DataView`/typed arrays and
 * the KMP core geometry generators — no WebGL context, no Filament module. So
 * the produced GLB can be parsed back here and asserted byte-for-byte:
 * the 12-byte header, the JSON chunk, and the BIN chunk all follow the GLB 2.0
 * container spec.
 */
class GeometryGLBBuilderTest {

    // --- GLB container parsing helpers -------------------------------------

    private data class ParsedGlb(
        val totalLength: Int,
        val version: Int,
        val json: String,
        val jsonChunkLength: Int,
        val binChunkLength: Int,
    )

    /** Parse a GLB [ArrayBuffer] into its header + JSON chunk + BIN chunk lengths. */
    private fun parse(glb: ArrayBuffer): ParsedGlb {
        val view = DataView(glb)
        // 12-byte header: magic, version, length.
        val magic = view.getUint32(0, true)
        val version = view.getUint32(4, true)
        val totalLength = view.getUint32(8, true)
        assertEquals(0x46546C67, magic, "GLB magic must be 'glTF' (0x46546C67)")
        assertEquals(glb.byteLength, totalLength, "header length must equal the actual buffer size")

        // JSON chunk header at offset 12.
        val jsonChunkLength = view.getUint32(12, true)
        val jsonChunkType = view.getUint32(16, true)
        assertEquals(0x4E4F534A, jsonChunkType, "first chunk type must be 'JSON'")

        // JSON chunk body.
        val sb = StringBuilder()
        for (i in 0 until jsonChunkLength) {
            sb.append(view.getUint8(20 + i).toInt().toChar())
        }

        // BIN chunk header right after the JSON body.
        val binHeaderOffset = 20 + jsonChunkLength
        val binChunkLength = view.getUint32(binHeaderOffset, true)
        val binChunkType = view.getUint32(binHeaderOffset + 4, true)
        assertEquals(0x004E4942, binChunkType, "second chunk type must be 'BIN\\0'")

        return ParsedGlb(totalLength, version, sb.toString(), jsonChunkLength, binChunkLength)
    }

    @Test
    fun buildsValidGlbContainerForACube() {
        val glb = GeometryGLBBuilder.buildGLB(GeometryConfig().apply { cube() })
        val parsed = parse(glb)
        assertEquals(2, parsed.version, "GLB container version must be 2")
        assertTrue(parsed.totalLength > 12, "a non-empty GLB must be larger than its header")
    }

    @Test
    fun chunkLengthsAreFourByteAligned() {
        // The GLB spec requires every chunk length to be a multiple of 4.
        val glb = GeometryGLBBuilder.buildGLB(GeometryConfig().apply { sphere() })
        val parsed = parse(glb)
        assertEquals(0, parsed.jsonChunkLength % 4, "JSON chunk must be 4-byte aligned")
        assertEquals(0, parsed.binChunkLength % 4, "BIN chunk must be 4-byte aligned")
    }

    @Test
    fun totalLengthEqualsHeaderPlusBothChunks() {
        val glb = GeometryGLBBuilder.buildGLB(GeometryConfig().apply { cylinder() })
        val parsed = parse(glb)
        // header(12) + JSON chunk header(8) + JSON body + BIN chunk header(8) + BIN body
        val expected = 12 + 8 + parsed.jsonChunkLength + 8 + parsed.binChunkLength
        assertEquals(expected, parsed.totalLength)
    }

    @Test
    fun jsonDeclaresAssetAndSingleMesh() {
        val glb = GeometryGLBBuilder.buildGLB(GeometryConfig().apply { cube() })
        val json = JSON.parse<dynamic>(parse(glb).json)
        assertEquals("2.0", json.asset.version as String, "glTF asset version must be 2.0")
        assertEquals("SceneView-Web", json.asset.generator as String)
        assertEquals(1, (json.meshes as Array<dynamic>).size, "one geometry config -> one mesh")
        assertEquals(1, (json.nodes as Array<dynamic>).size, "one geometry config -> one node")
    }

    @Test
    fun cubeNodeIsNamedAfterItsGeometryType() {
        val glb = GeometryGLBBuilder.buildGLB(GeometryConfig().apply { cube() })
        val json = JSON.parse<dynamic>(parse(glb).json)
        assertEquals("cube", json.nodes[0].name as String)
    }

    @Test
    fun primitiveDeclaresPositionAndNormalAttributes() {
        val glb = GeometryGLBBuilder.buildGLB(GeometryConfig().apply { cube() })
        val json = JSON.parse<dynamic>(parse(glb).json)
        val primitive = json.meshes[0].primitives[0]
        assertEquals(0, primitive.attributes.POSITION as Int)
        assertEquals(1, primitive.attributes.NORMAL as Int)
        assertEquals(2, primitive.indices as Int)
        assertEquals(4, primitive.mode as Int, "mode 4 = TRIANGLES")
        // Three accessors: position (VEC3), normal (VEC3), indices (SCALAR).
        assertEquals(3, (json.accessors as Array<dynamic>).size)
        assertEquals("VEC3", json.accessors[0].type as String)
        assertEquals("VEC3", json.accessors[1].type as String)
        assertEquals("SCALAR", json.accessors[2].type as String)
    }

    @Test
    fun positionAccessorCarriesABoundingBox() {
        // A unit cube spans roughly [-0.5, 0.5] on every axis — the min/max
        // accessor fields must bracket the geometry, never collapse to a point.
        val glb = GeometryGLBBuilder.buildGLB(GeometryConfig().apply { cube(); size(2.0) })
        val json = JSON.parse<dynamic>(parse(glb).json)
        val acc = json.accessors[0]
        val min = acc.min as Array<dynamic>
        val max = acc.max as Array<dynamic>
        assertEquals(3, min.size); assertEquals(3, max.size)
        for (i in 0..2) {
            assertTrue(
                (max[i] as Number).toDouble() > (min[i] as Number).toDouble(),
                "bbox axis $i must have positive extent",
            )
        }
    }

    @Test
    fun baseColorFactorMatchesTheConfiguredColor() {
        val glb = GeometryGLBBuilder.buildGLB(
            GeometryConfig().apply { cube(); color(0.25, 0.5, 0.75, 0.6) },
        )
        val json = JSON.parse<dynamic>(parse(glb).json)
        val factor = json.materials[0].pbrMetallicRoughness.baseColorFactor as Array<dynamic>
        assertEquals(0.25, (factor[0] as Number).toDouble(), 1e-9)
        assertEquals(0.5, (factor[1] as Number).toDouble(), 1e-9)
        assertEquals(0.75, (factor[2] as Number).toDouble(), 1e-9)
        assertEquals(0.6, (factor[3] as Number).toDouble(), 1e-9)
    }

    @Test
    fun litGeometryHasNoUnlitExtension() {
        // Default config is lit PBR — KHR_materials_unlit must be absent and
        // not declared in extensionsUsed.
        val glb = GeometryGLBBuilder.buildGLB(GeometryConfig().apply { cube() })
        val json = JSON.parse<dynamic>(parse(glb).json)
        assertNull(json.materials[0].extensions, "lit material must have no extensions object")
        assertFalse(
            js("'extensionsUsed' in json") as Boolean,
            "lit GLB must not declare extensionsUsed",
        )
    }

    @Test
    fun unlitGeometryAddsKhrMaterialsUnlitExtension() {
        val glb = GeometryGLBBuilder.buildGLB(
            GeometryConfig().apply { cube(); unlit() },
        )
        val json = JSON.parse<dynamic>(parse(glb).json)
        val ext = json.materials[0].extensions
        assertNotNull(ext, "unlit material must carry an extensions object")
        assertNotNull(
            ext.KHR_materials_unlit,
            "unlit material must declare the KHR_materials_unlit extension",
        )
        // The extension must also be declared at the glTF root per spec.
        val used = json.extensionsUsed as Array<dynamic>
        assertTrue(
            used.any { (it as String) == "KHR_materials_unlit" },
            "extensionsUsed must list KHR_materials_unlit",
        )
    }

    @Test
    fun transparentUnlitMaterialIsDoubleSided() {
        // The builder marks a transparent unlit material doubleSided (overlay
        // use case) — matching Android's transparent_unlit_colored.mat.
        val opaqueUnlit = GeometryGLBBuilder.buildGLB(
            GeometryConfig().apply { cube(); unlit(); color(1.0, 0.0, 0.0, 1.0) },
        )
        assertEquals(
            false,
            JSON.parse<dynamic>(parse(opaqueUnlit).json).materials[0].doubleSided as Boolean,
            "opaque unlit material must stay single-sided",
        )

        val transparentUnlit = GeometryGLBBuilder.buildGLB(
            GeometryConfig().apply { cube(); unlit(); color(1.0, 0.0, 0.0, 0.4) },
        )
        assertEquals(
            true,
            JSON.parse<dynamic>(parse(transparentUnlit).json).materials[0].doubleSided as Boolean,
            "transparent unlit material must be double-sided for the overlay use case",
        )
    }

    @Test
    fun nonZeroPositionEmitsANodeTranslation() {
        val centered = GeometryGLBBuilder.buildGLB(GeometryConfig().apply { cube() })
        assertNull(
            JSON.parse<dynamic>(parse(centered).json).nodes[0].translation,
            "a geometry at the origin must omit the translation field",
        )

        val offset = GeometryGLBBuilder.buildGLB(
            GeometryConfig().apply { cube(); position(1.0, 2.0, -3.0) },
        )
        val t = JSON.parse<dynamic>(parse(offset).json).nodes[0].translation as Array<dynamic>
        assertEquals(1.0, (t[0] as Number).toDouble(), 1e-9)
        assertEquals(2.0, (t[1] as Number).toDouble(), 1e-9)
        assertEquals(-3.0, (t[2] as Number).toDouble(), 1e-9)
    }

    @Test
    fun nonUniformScaleEmitsANodeScale() {
        val unit = GeometryGLBBuilder.buildGLB(GeometryConfig().apply { cube() })
        assertNull(
            JSON.parse<dynamic>(parse(unit).json).nodes[0].scale,
            "a unit-scale geometry must omit the scale field",
        )

        val scaled = GeometryGLBBuilder.buildGLB(
            GeometryConfig().apply { cube(); scale(2.0, 3.0, 4.0) },
        )
        val s = JSON.parse<dynamic>(parse(scaled).json).nodes[0].scale as Array<dynamic>
        assertEquals(2.0, (s[0] as Number).toDouble(), 1e-9)
        assertEquals(3.0, (s[1] as Number).toDouble(), 1e-9)
        assertEquals(4.0, (s[2] as Number).toDouble(), 1e-9)
    }

    @Test
    fun rotationEmitsANormalizedQuaternion() {
        // A 90° rotation about Y must serialize as a unit-length quaternion.
        val glb = GeometryGLBBuilder.buildGLB(
            GeometryConfig().apply { cube(); rotation(0.0, 90.0, 0.0) },
        )
        val q = JSON.parse<dynamic>(parse(glb).json).nodes[0].rotation as Array<dynamic>
        assertEquals(4, q.size, "a glTF rotation is a 4-component quaternion")
        val qx = (q[0] as Number).toDouble()
        val qy = (q[1] as Number).toDouble()
        val qz = (q[2] as Number).toDouble()
        val qw = (q[3] as Number).toDouble()
        val length = kotlin.math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
        assertEquals(1.0, length, 1e-9, "a rotation quaternion must be unit length")
        // A pure Y rotation has zero X and Z components.
        assertEquals(0.0, qx, 1e-9)
        assertEquals(0.0, qz, 1e-9)
    }

    @Test
    fun bufferLogicalLengthMatchesBufferViews() {
        // The single buffer's byteLength must cover all three buffer views
        // (positions + normals + indices), per the glTF spec.
        val glb = GeometryGLBBuilder.buildGLB(GeometryConfig().apply { sphere() })
        val json = JSON.parse<dynamic>(parse(glb).json)
        val views = json.bufferViews as Array<dynamic>
        assertEquals(3, views.size)
        val bufferLength = (json.buffers[0].byteLength as Number).toInt()
        val lastView = views[2]
        val lastEnd = (lastView.byteOffset as Number).toInt() + (lastView.byteLength as Number).toInt()
        assertEquals(bufferLength, lastEnd, "buffer length must equal the end of the last buffer view")
    }

    @Test
    fun everyPrimitiveGeometryTypeProducesAParsableGlb() {
        // Smoke-check all four generators round-trip through buildGLB.
        for (build in listOf<GeometryConfig.() -> Unit>(
            { cube() }, { sphere() }, { cylinder() }, { plane() },
        )) {
            val glb = GeometryGLBBuilder.buildGLB(GeometryConfig().apply(build))
            val json = JSON.parse<dynamic>(parse(glb).json)
            val posCount = (json.accessors[0].count as Number).toInt()
            val idxCount = (json.accessors[2].count as Number).toInt()
            assertTrue(posCount > 0, "geometry must emit vertices")
            assertTrue(idxCount > 0, "geometry must emit indices")
            assertEquals(0, idxCount % 3, "a triangle list must have an index count divisible by 3")
        }
    }
}
