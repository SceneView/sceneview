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
        builder: RenderableManager.Builder.() -> Unit,
    ) : this(engine, entity) {
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
        super.destroy()
    }
}