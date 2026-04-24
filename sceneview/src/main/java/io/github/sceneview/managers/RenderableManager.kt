package io.github.sceneview.managers

import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import io.github.sceneview.Entity


fun RenderableManager.Builder.materials(materialInstances: List<MaterialInstance?>) =
    materialInstances.forEachIndexed { index, materialInstance ->
        materialInstance?.let { material(index, it) }
    }

fun RenderableManager.Builder.build(engine: Engine) = EntityManager.get().create().apply {
    build(engine, this)
}

fun RenderableManager.safeDestroy(entity: Entity) = destroy(entity)