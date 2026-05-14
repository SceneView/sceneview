package io.github.sceneview.node

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.geometries.geometry
import io.github.sceneview.managers.materials
import io.github.sceneview.safeDestroyGeometry

/**
 * Mesh are bundles of primitives, each of which has its own geometry and material.
 *
 * All primitives in a particular renderable share a set of rendering attributes, such as whether
 * they cast shadows or use vertex skinning. Kotlin usage example:
 *
 * ```
 * val entity = EntityManager.get().create()
 *
 * RenderableManager.Builder(1)
 *         .boundingBox(Box(0.0f, 0.0f, 0.0f, 9000.0f, 9000.0f, 9000.0f))
 *         .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
 *         .material(0, material)
 *         .build(engine, entity)
 *
 * scene.addEntity(renderable)
 * ```
 *
 * To modify the state of an existing renderable, clients should first use RenderableManager to get
 * a temporary handle called an <em>instance</em>. The instance can then be used to get or set the
 * renderable's state. Please note that instances are ephemeral; clients should store entities,
 * not instances.
 *
 * @see Geometry
 */
open class GeometryNode(
    engine: Engine,
    open val geometry: Geometry,
    materialInstances: List<MaterialInstance?>,
    primitivesOffsets: List<IntRange> = geometry.primitivesOffsets,
    /**
     * If `true`, [destroy] also destroys every non-null entry of [materialInstances].
     *
     * See [RenderableNode]'s `destroyMaterialsOnDispose` KDoc for usage guidance. Default
     * `false` for backward compatibility (#1123).
     */
    destroyMaterialsOnDispose: Boolean = false,
    builderApply: RenderableManager.Builder.() -> Unit = {}
) : RenderableNode(
    engine = engine,
    primitiveCount = primitivesOffsets.size,
    boundingBox = geometry.boundingBox,
    materialInstances = materialInstances,
    destroyMaterialsOnDispose = destroyMaterialsOnDispose,
    builder = {
        geometry(geometry, primitivesOffsets)
        materials(materialInstances)
        apply(builderApply)
    }) {

    constructor(
        engine: Engine,
        geometry: Geometry,
        materialInstance: MaterialInstance? = null,
        destroyMaterialsOnDispose: Boolean = false,
        builderApply: RenderableManager.Builder.() -> Unit = {}
    ) : this(
        engine = engine,
        geometry = geometry,
        materialInstances = listOf(materialInstance),
        primitivesOffsets = listOf(0..geometry.primitivesOffsets.last().last),
        destroyMaterialsOnDispose = destroyMaterialsOnDispose,
        builderApply = builderApply
    )

    fun updateGeometry(
        vertices: List<Geometry.Vertex> = geometry.vertices,
        indices: List<List<Int>> = geometry.primitivesIndices
    ) = setGeometry(geometry.update(engine, vertices, indices))

    override fun destroy() {
        super.destroy()
        engine.safeDestroyGeometry(geometry)
    }
}