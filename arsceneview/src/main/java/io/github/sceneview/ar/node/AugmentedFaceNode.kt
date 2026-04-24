package io.github.sceneview.ar.node

import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.SurfaceOrientation
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute.POSITION
import com.google.android.filament.VertexBuffer.VertexAttribute.TANGENTS
import com.google.android.filament.VertexBuffer.VertexAttribute.UV0
import com.google.ar.core.AugmentedFace
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.google.ar.core.AugmentedFace.RegionType
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.Trackable
import com.google.ar.core.TrackingState
import io.github.sceneview.node.MeshNode

/**
 * AR Augmented Face positioned 3D model node
 *
 * Describes a face detected by ARCore and provides methods to access additional center and face
 * region poses as well as face mesh related data.
 *
 * Augmented Faces supports front-facing (selfie) camera only, and does not support attaching
 * anchors nor raycast hit testing. [Trackable.createAnchor] will result in an
 * `IllegalStateException`.
 *
 * To use Augmented Faces, enable the feature in the session. This can be done at session creation
 * time, or at any time during session runtime:
 *
 * ```
 * Session session = new Session(context, EnumSet.of(Session.Feature.FRONT_CAMERA));
 * Config config = ...
 * config.setAugmentedFaceMode(AugmentedFaceMode.MESH3D);
 * session.configure(config);
 * }
 * ```
 *
 * When Augmented Face mode is enabled, ARCore updates the list of detected faces for each frame.
 * Use [Session.getAllTrackables] and [Trackable.getTrackingState] to get a list of faces that have
 * valid meshes that can be rendered.
 *
 * ```
 * for (AugmentedFace face : session.getAllTrackables(AugmentedFace.class)) {
 *   if (face.getTrackingState() == TrackingState.TRACKING) {
 *     // Render face mesh ...
 *   }
 * }
 * }
 * ```
 *
 * Faces provide static mesh data that does not change during the session, as well as pose and mesh
 * data that is updated each frame:
 *
 * ```
 * // UVs and indices can be cached as they do not change during the session.
 * FloatBuffer uvs = face.getMeshTextureCoordinates();
 * ShortBuffer indices = face.getMeshTriangleIndices();
 *
 * // Center and region poses, mesh vertices, and normals are updated each frame.
 * Pose facePose = face.getCenterPose();
 * FloatBuffer faceVertices = face.getMeshVertices();
 * FloatBuffer faceNormals = face.getMeshNormals();
 * }
 * ```
 */
open class AugmentedFaceNode(
    engine: Engine,
    val augmentedFace: AugmentedFace,
    meshMaterialInstance: MaterialInstance? = null,
    builder: RenderableManager.Builder.() -> Unit = {},
    onTrackingStateChanged: ((TrackingState) -> Unit)? = null,
    onUpdated: ((AugmentedFace) -> Unit)? = null
) : TrackableNode<AugmentedFace>(
    engine = engine,
    onTrackingStateChanged = onTrackingStateChanged,
    onUpdated = onUpdated
) {

    /**
     * The center of the face, defined to have the origin located behind the nose and between the
     * two cheek bones.
     *
     * Z+ is forward out of the nose, Y+ is upwards, and X+ is towards the left.
     * The units are in meters. When the face trackable state is TRACKING, this pose is synced with
     * the latest frame. When face trackable state is PAUSED, an identity pose will be returned.
     *
     * Use [regionNodes] to retrieve poses of specific regions of the face.
     */
    val centerNode = PoseNode(engine).apply { parent = this@AugmentedFaceNode }

    /**
     * The face mesh node, created lazily once ARCore provides valid mesh buffers.
     *
     * Returns `null` until the first tracking frame with non-empty mesh data.
     * Vertex positions and normals are updated every frame while tracking;
     * UVs and triangle indices are set once (they are static per ARCore docs).
     */
    var meshNode: MeshNode? = null
        private set

    private val faceMaterialInstance = meshMaterialInstance
    private val meshBuilder = builder

    // Reusable tangent-quaternion buffer: 4 floats per vertex, 16 bytes/vertex.
    // Allocated lazily and grown on demand — the face mesh vertex count is stable
    // across frames (ARCore returns a fixed-topology mesh), so this is typically
    // allocated once and reused for the life of the node.
    private var tangentsBuffer: ByteBuffer? = null

    /**
     * The region nodes at the tip of the nose, the detected face's left side of the forehead,
     * the detected face's right side of the forehead.
     *
     * Defines face regions to query the pose for. Left and right are defined relative to the person
     * that the mesh belongs to. To retrieve the center pose use [AugmentedFace.getCenterPose].
     */
    val regionNodes = RegionType.values().associateWith {
        PoseNode(engine).apply { parent = this@AugmentedFaceNode }
    }

    init {
        trackable = augmentedFace
    }

    override fun update(trackable: AugmentedFace?) {
        super.update(trackable)

        if (augmentedFace.trackingState != TrackingState.TRACKING) return

        // Guard: buffers are not yet populated in the very first TRACKING frame.
        // Building Filament buffers with size 0 triggers a native abort.
        val indices = augmentedFace.meshTriangleIndices
        val vertices = augmentedFace.meshVertices
        val normals = augmentedFace.meshNormals
        val uvs = augmentedFace.meshTextureCoordinates

        if (indices.limit() == 0 || vertices.limit() == 0) return

        val vertexCount = vertices.limit() / 3

        // Compute tangent quaternions from positions + normals + uvs + indices.
        // PBR materials require TANGENTS (FLOAT4 quaternions encoding normal + tangent
        // + bitangent), not raw FLOAT3 normals — uploading normals as if they were
        // quaternions left the mesh with undefined lighting and rendered it invisible
        // under the transparent colored material used by the demo.
        val tangents = computeTangents(vertices, normals, uvs, indices, vertexCount)

        if (meshNode == null) {
            meshNode = MeshNode(
                engine = engine,
                primitiveType = PrimitiveType.TRIANGLES,
                vertexBuffer = VertexBuffer.Builder()
                    // Position + Tangents (quaternion) + UV Coordinates
                    .bufferCount(3)
                    // Position Attribute (x, y, z)
                    .attribute(POSITION, 0, AttributeType.FLOAT3)
                    // Tangents Attribute (Quaternion: x, y, z, w) — encodes normal + tangent
                    // + bitangent for PBR lighting. Must be FLOAT4.
                    .attribute(TANGENTS, 1, AttributeType.FLOAT4)
                    .normalized(TANGENTS)
                    // Uv Attribute (x, y)
                    .attribute(UV0, 2, AttributeType.FLOAT2)
                    .vertexCount(vertexCount)
                    .build(engine).apply {
                        // Fill all slots before the node becomes visible,
                        // so Filament can compute a non-empty AABB (build:552)
                        setBufferAt(engine, 0, vertices) // positions  (dynamic)
                        setBufferAt(engine, 1, tangents) // tangents   (dynamic, recomputed)
                        setBufferAt(engine, 2, uvs)      // UVs        (static)
                    },
                indexBuffer = IndexBuffer.Builder()
                    .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                    .indexCount(indices.limit())
                    .build(engine).apply {
                        setBuffer(engine, indices)       // indices    (static)
                    },
                materialInstance = faceMaterialInstance,
                builder = {
                    // Filament computes AABB asynchronously after vertex buffer upload.
                    // If a render frame starts before AABB is updated, Filament aborts with
                    // "AABB can't be empty" (build:552). Disabling culling avoids this race
                    // condition. For a face mesh this may be acceptable since the mesh is always
                    // within the camera frustum while tracking.
                    culling(false)
                    castShadows(false)
                    receiveShadows(false)
                    meshBuilder()
                }
            ).apply { parent = centerNode }

            // Early return — buffers already filled above,
            // next frame will go to the update branch below
            centerNode.pose = augmentedFace.centerPose
            regionNodes.forEach { (regionType, regionNode) ->
                regionNode.pose = augmentedFace.getRegionPose(regionType)
            }
            return
        }

        // Update dynamic buffers every frame — positions + recomputed tangents.
        // Tangent quaternions depend on normals and must be rebuilt whenever the
        // mesh deforms (i.e. every tracked frame).
        meshNode?.vertexBuffer?.apply {
            setBufferAt(engine, 0, vertices) // positions
            setBufferAt(engine, 1, tangents) // tangent quaternions
        }

        centerNode.pose = augmentedFace.centerPose

        regionNodes.forEach { (regionType, regionNode) ->
            regionNode.pose = augmentedFace.getRegionPose(regionType)
        }
    }

    /**
     * Updates face tracking state each frame.
     *
     * Overrides [TrackableNode.update] because [Frame.getUpdatedTrackables] always returns
     * an empty list for [AugmentedFace] on the front camera. Manually sets [PoseNode] state
     * (session, frame, cameraTrackingState) since `super` cannot be called without
     * re-triggering the broken `getUpdatedTrackables` check.
     */
    override fun update(session: Session, frame: Frame) {
        // PoseNode state — set manually since we skip super
        this.session = session
        this.frame = frame
        this.cameraTrackingState = frame.camera.trackingState

        if (augmentedFace.trackingState == TrackingState.TRACKING) {
            update(augmentedFace)
            onUpdated?.invoke(augmentedFace)
        }
    }

    /**
     * Builds (or reuses) [tangentsBuffer] and fills it with quaternions computed from
     * the supplied positions, normals, UVs and indices via Filament's
     * [SurfaceOrientation]. The returned buffer holds `vertexCount * 16` bytes
     * (4 floats per vertex) and is rewound to position 0, ready for upload.
     */
    private fun computeTangents(
        positions: java.nio.FloatBuffer,
        normals: java.nio.FloatBuffer,
        uvs: java.nio.FloatBuffer,
        indices: java.nio.ShortBuffer,
        vertexCount: Int,
    ): ByteBuffer {
        val neededBytes = vertexCount * 4 * 4
        val buf = tangentsBuffer?.takeIf { it.capacity() >= neededBytes }
            ?: ByteBuffer.allocateDirect(neededBytes).order(ByteOrder.nativeOrder())
                .also { tangentsBuffer = it }

        buf.clear()
        buf.limit(neededBytes)

        val orientation = SurfaceOrientation.Builder()
            .vertexCount(vertexCount)
            .positions(positions.duplicate().rewind() as java.nio.Buffer)
            .normals(normals.duplicate().rewind() as java.nio.Buffer)
            .uvs(uvs.duplicate().rewind() as java.nio.Buffer)
            .triangleCount(indices.limit() / 3)
            .triangles_uint16(indices.duplicate().rewind() as java.nio.Buffer)
            .build()
        try {
            orientation.getQuatsAsFloat(buf)
        } finally {
            orientation.destroy()
        }
        buf.rewind()
        return buf
    }

    override fun destroy() {
        // Destroy the face mesh resources we built in update() before tearing down
        // the parent chain. Filament does not reclaim VertexBuffer / IndexBuffer when
        // the owning Renderable is destroyed — they stay in the engine registry until
        // Engine.destroy() runs, and because they're tied to this composable's
        // disposable lifecycle, each back-navigation leaks two buffers per tracked face.
        // More critically: destroying the Engine while a VertexBuffer is still
        // attached to a not-yet-unregistered Renderable triggers a Filament
        // PreconditionPanic ("resource still referenced") — that's the native abort
        // users hit when leaving the Face Mesh demo via the back gesture.
        val mn = meshNode
        if (mn != null) {
            val vb = mn.vertexBuffer
            val ib = mn.indexBuffer
            runCatching { mn.destroy() }
            runCatching { engine.destroyVertexBuffer(vb) }
            runCatching { engine.destroyIndexBuffer(ib) }
            meshNode = null
        }
        runCatching { centerNode.destroy() }
        regionNodes.values.forEach { runCatching { it.destroy() } }
        super.destroy()
    }
}
