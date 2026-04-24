package io.github.sceneview

import android.content.Context
import android.util.Log
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.Fence
import com.google.android.filament.IndexBuffer
import com.google.android.filament.IndirectLight
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.Stream
import com.google.android.filament.Texture
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.gltfio.AssetLoader
import io.github.sceneview.environment.Environment
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.Model

typealias Entity = Int
typealias EntityInstance = Int
typealias FilamentEntity = com.google.android.filament.Entity
typealias FilamentEntityInstance = com.google.android.filament.EntityInstance

fun Engine.createModelLoader(context: Context) = ModelLoader(this, context)
fun Engine.createMaterialLoader(context: Context) = MaterialLoader(this, context)
fun Engine.createEnvironmentLoader(context: Context) = EnvironmentLoader(this, context)

fun Engine.createCamera() = createCamera(entityManager.create())

/**
 * Blocks until all pending GPU frames have been rendered.
 * Call after resizing or destroying a surface to avoid pipeline races.
 */
fun Engine.drainFramePipeline() {
    createFence().apply {
        wait(Fence.Mode.FLUSH, Fence.WAIT_FOR_EVER)
        destroyFence(this)
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Direct destroy helpers.
//
// All of these used to be named `safeDestroyX` and wrapped in `runCatching` as a hotfix that
// quietly swallowed any Filament native-side exception. That masked real lifecycle bugs (nodes
// destroyed twice, textures destroyed before the MaterialInstance that still referenced them,
// and so on) and made the library feel "mostly working" while crashing on specific teardown
// orderings. The `safeDestroyX` names remain for source compatibility — they now fail loud,
// which is how a clean implementation should behave. Fix the real ordering bug at the call site
// instead of re-adding a runCatching wrapper.
// ─────────────────────────────────────────────────────────────────────────────────────────────

fun AssetLoader.safeDestroyModel(model: Model) {
    model.releaseSourceData()
    destroyAsset(model)
}

fun Engine.safeDestroy() {
    destroy()
    Log.d("Sceneview", "Engine destroyed")
}

fun Engine.safeDestroyEntity(entity: Entity) = destroyEntity(entity)

fun Engine.destroyTransformable(@FilamentEntity entity: Entity) = transformManager.destroy(entity)
fun Engine.safeDestroyTransformable(@FilamentEntity entity: Entity) = destroyTransformable(entity)

fun Engine.safeDestroyCamera(camera: Camera) = destroyCameraComponent(camera.entity)

fun Engine.safeDestroyEnvironment(environment: Environment) {
    environment.indirectLight?.let { safeDestroyIndirectLight(it) }
    environment.skybox?.let { safeDestroySkybox(it) }
}

fun Engine.safeDestroyIndirectLight(indirectLight: IndirectLight) = destroyIndirectLight(indirectLight)

fun Engine.safeDestroySkybox(skybox: Skybox) = destroySkybox(skybox)

fun Engine.safeDestroyMaterial(material: Material) = destroyMaterial(material)
fun Engine.safeDestroyMaterialInstance(materialInstance: MaterialInstance) =
    destroyMaterialInstance(materialInstance)

fun Engine.safeDestroyTexture(texture: Texture) = destroyTexture(texture)

fun Engine.safeDestroyStream(stream: Stream) = destroyStream(stream)

fun Engine.destroyRenderable(@FilamentEntity entity: Entity) = renderableManager.destroy(entity)

fun Engine.safeDestroyRenderable(@FilamentEntity entity: Entity) = destroyRenderable(entity)

fun Engine.destroyGeometry(geometry: Geometry) {
    destroyVertexBuffer(geometry.vertexBuffer)
    destroyIndexBuffer(geometry.indexBuffer)
}

fun Engine.safeDestroyGeometry(geometry: Geometry) {
    safeDestroyVertexBuffer(geometry.vertexBuffer)
    safeDestroyIndexBuffer(geometry.indexBuffer)
}

fun Engine.safeDestroyVertexBuffer(vertexBuffer: VertexBuffer) = destroyVertexBuffer(vertexBuffer)

fun Engine.safeDestroyIndexBuffer(indexBuffer: IndexBuffer) = destroyIndexBuffer(indexBuffer)

fun Engine.safeDestroyMaterialLoader(materialLoader: MaterialLoader) = materialLoader.destroy()

fun Engine.safeDestroyModelLoader(modelLoader: ModelLoader) = modelLoader.destroy()
fun Engine.safeDestroyRenderer(renderer: Renderer) = destroyRenderer(renderer)
fun Engine.safeDestroyView(view: View) = destroyView(view)
fun Engine.safeDestroyScene(scene: Scene) = destroyScene(scene)
