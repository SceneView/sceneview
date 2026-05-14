package io.github.sceneview.node

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import io.github.sceneview.Entity
import io.github.sceneview.FilamentEntity
import io.github.sceneview.components.RenderableComponent
import io.github.sceneview.math.toVector3Box
import io.github.sceneview.safeDestroyMaterialInstance
import io.github.sceneview.safeDestroyRenderable

/**
 * A Node represents a transformation within the scene graph's hierarchy.
 *
 * This node contains a renderable model for the rendering engine to render.
 *
 * Each node can have an arbitrary number of child nodes and one parent. The parent may be
 * another node, or the scene root
 * .
 */
open class RenderableNode(
    engine: Engine,
    @FilamentEntity entity: Entity = EntityManager.get().create(),
) : Node(engine, entity), RenderableComponent {

    /**
     * Material instances this node owns and will tear down on [destroy].
     *
     * Populated only when the [primitiveCount] constructor is called with
     * `destroyMaterialsOnDispose = true`. Direct callers of the no-arg constructor manage
     * their own materials.
     */
    private var ownedMaterialInstances: List<MaterialInstance> = emptyList()

    constructor(
        engine: Engine,
        @FilamentEntity entity: Entity = EntityManager.get().create(),
        /**
         * Count the number of primitives that will be supplied to the builder
         */
        primitiveCount: Int,
        boundingBox: Box,
        /**
         * Binds a material instance.
         *
         * If no material is specified, Filament will fall back to a basic default material.
         */
        materialInstances: List<MaterialInstance?> = listOf(),
        /**
         * If `true`, [destroy] also destroys every non-null entry of [materialInstances] via
         * [Engine.safeDestroyMaterialInstance].
         *
         * Use `true` when this node owns the lifecycle of its materials end-to-end — typically
         * when you build a one-off node with a freshly-created `MaterialInstance` and let the
         * node go out of scope (the common per-demo pattern). Without this flag, the bound
         * `MaterialInstance`s outlive the renderable and accumulate in Filament's internal
         * tables until engine teardown — a steady-state memory leak (#1123).
         *
         * Use `false` (the default) when a `MaterialLoader` or other long-lived owner is
         * responsible for destroying the materials externally — for example via the
         * `rememberMaterialInstance` Compose helper or a manual `DisposableEffect`.
         *
         * Note: this calls `engine.safeDestroyMaterialInstance(...)`. If the material was
         * created through a `MaterialLoader`, the loader's internal tracking set is **not**
         * cleaned up automatically — prefer `materialLoader.destroyMaterialInstance(...)` in
         * an external `DisposableEffect` for loader-managed materials.
         */
        destroyMaterialsOnDispose: Boolean = false,
        builder: RenderableManager.Builder.() -> Unit,
    ) : this(engine, entity) {
        if (destroyMaterialsOnDispose) {
            ownedMaterialInstances = materialInstances.filterNotNull()
        }
        RenderableManager.Builder(primitiveCount)
            .boundingBox(boundingBox)
            .apply {
                materialInstances.forEachIndexed { index, materialInstance ->
                    materialInstance?.let { material(index, materialInstance) }
                }
            }.apply(builder)
            .build(engine, entity)
        updateCollisionShape()
    }

    override fun updateVisibility() {
        super.updateVisibility()

        setLayerVisible(isVisible)
    }

    fun updateCollisionShape() {
        collisionShape = axisAlignedBoundingBox.toVector3Box()
    }

    override fun destroy() {
        // Destroy the renderable component BEFORE Node.destroy() frees the entity ID.
        // If safeDestroyEntity runs first, renderableManager.destroy(entity) silently fails
        // (entity ID invalid), leaving Filament's internal MI/texture bindings dangling and
        // causing "Invalid texture still bound to MaterialInstance" on the subsequent texture
        // destroy.
        engine.safeDestroyRenderable(entity)
        // Then tear down owned MaterialInstances if the constructor opted in (#1123).
        // safeDestroyMaterialInstance is a no-op (via runCatching) if the instance is already
        // destroyed or invalid — robust against double-destroy.
        ownedMaterialInstances.forEach { engine.safeDestroyMaterialInstance(it) }
        super.destroy()
    }
}