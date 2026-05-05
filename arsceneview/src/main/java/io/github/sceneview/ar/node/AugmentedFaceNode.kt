package io.github.sceneview.ar.node

import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute.POSITION
import com.google.android.filament.VertexBuffer.VertexAttribute.TANGENTS
import com.google.android.filament.VertexBuffer.VertexAttribute.UV0
import com.google.ar.core.AugmentedFace
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
    private val meshMaterialInstance: MaterialInstance? = null,
    private val builder: RenderableManager.Builder.() -> Unit = {},
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

        if (indices.limit() == 0 || vertices.limit() == 0) return

        if (meshNode == null) {
            // UVs are static for the session — read once at mesh creation.
            val uvs = augmentedFace.meshTextureCoordinates
            meshNode = MeshNode(
                engine = engine,
                primitiveType = PrimitiveType.TRIANGLES,
                vertexBuffer = VertexBuffer.Builder()
                    // Position + Normals + UV Coordinates
                    .bufferCount(3)
                    // Position Attribute (x, y, z)
                    .attribute(POSITION, 0, AttributeType.FLOAT3)
                    // Tangents Attribute (Quaternion: x, y, z, w)
                    .attribute(TANGENTS, 1, AttributeType.FLOAT4)
                    .normalized(TANGENTS)
                    // Uv Attribute (x, y)
                    .attribute(UV0, 2, AttributeType.FLOAT2)
                    .vertexCount(vertices.limit() / 3)
                    .build(engine).apply {
                        // Fill all slots before the node becomes visible,
                        // so Filament can compute a non-empty AABB (build:552)
                        setBufferAt(engine, 0, vertices) // positions  (dynamic)
                        setBufferAt(engine, 1, normals)  // normals    (dynamic)
                        setBufferAt(engine, 2, uvs)      // UVs        (static)
                    },
                indexBuffer = IndexBuffer.Builder()
                    .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                    .indexCount(indices.limit())
                    .build(engine).apply {
                        setBuffer(engine, indices)       // indices    (static)
                    },
                materialInstance = meshMaterialInstance,
                builder = {
                    // Filament computes AABB asynchronously after vertex buffer upload.
                    // If a render frame starts before AABB is updated, Filament aborts with
                    // "AABB can't be empty" (build:552). Disabling culling avoids this race
                    // condition. For a face mesh this may be acceptable since the mesh is always
                    // within the camera frustum while tracking.
                    culling(false)
                    castShadows(false)
                    receiveShadows(false)
                    builder()
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

        // Update dynamic buffers every frame
        meshNode?.vertexBuffer?.apply {
            setBufferAt(engine, 0, vertices) // positions
            setBufferAt(engine, 1, normals)  // normals
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
     *
     * [update] (trackable) is invoked every frame regardless of tracking state so the
     * `trackingState` setter in [TrackableNode] fires `onTrackingStateChanged` and
     * `updateVisibility()` on TRACKING -> STOPPED/PAUSED transitions (e.g. face leaving the
     * frame). Existing guards in [update] (trackable) skip mesh creation/buffer updates when
     * not TRACKING. `onUpdated` only fires while TRACKING since the face data is only
     * meaningful then.
     */
    override fun update(session: Session, frame: Frame) {
        // PoseNode state — set manually since we skip super
        this.session = session
        this.frame = frame
        this.cameraTrackingState = frame.camera.trackingState

        // Always propagate trackable state so onTrackingStateChanged fires on every
        // transition, including TRACKING -> STOPPED/PAUSED. update(trackable) guards mesh
        // work internally.
        update(augmentedFace)

        if (augmentedFace.trackingState == TrackingState.TRACKING) {
            onUpdated?.invoke(augmentedFace)
        }
    }
}
