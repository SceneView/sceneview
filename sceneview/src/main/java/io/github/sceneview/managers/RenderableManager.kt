package io.github.sceneview.managers

import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import io.github.sceneview.Entity


/**
 * Assigns one [MaterialInstance] per renderable primitive, mapping each list element to the
 * primitive at the same index. `null` entries are skipped, leaving that primitive's material
 * unchanged. Returns this builder for chaining.
 */
fun RenderableManager.Builder.materials(materialInstances: List<MaterialInstance?>) =
    materialInstances.forEachIndexed { index, materialInstance ->
        materialInstance?.let { material(index, it) }
    }

/**
 * Creates a fresh Filament [Entity], builds this renderable onto it, and returns the entity.
 * Convenience over the two-step `EntityManager.create()` + `build(engine, entity)`. Must be
 * called on the main thread.
 *
 * @return the entity now carrying the built renderable component.
 */
fun RenderableManager.Builder.build(engine: Engine) = EntityManager.get().create().apply {
    build(engine, this)
}

/**
 * Destroys the renderable component attached to [entity]. Safe to call even if [entity] has
 * no renderable component. Must be called on the main thread.
 */
fun RenderableManager.safeDestroy(entity: Entity) = destroy(entity)