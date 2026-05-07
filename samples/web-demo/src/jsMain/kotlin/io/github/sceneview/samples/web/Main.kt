package io.github.sceneview.samples.web

import io.github.sceneview.web.SceneView
import io.github.sceneview.web.xr.ARSceneView
import io.github.sceneview.web.xr.VRSceneView
import io.github.sceneview.web.xr.XRSessionMode
import io.github.sceneview.web.xr.WebXRSession
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * SceneView Web Demo — rewritten showcase application.
 *
 * Features:
 * - Sketchfab search bar (fetch API) replaces hardcoded model list
 * - Geometry showcase: cube, sphere, cylinder, plane with color pickers
 * - WebXR AR/VR toggle buttons
 * - Tab-based navigation (Model Viewer / Geometry)
 * - Responsive dark theme
 * - SDK version 3.6.1 badge
 *
 * Uses Filament.js (WASM) — same rendering engine as SceneView Android.
 */

private const val SDK_VERSION = "4.0.8"

/** Sketchfab public API endpoint for searching downloadable models. */
private const val SKETCHFAB_API =
    "https://api.sketchfab.com/v3/search?type=models&downloadable=true"

private var currentSceneView: SceneView? = null
private var autoRotateEnabled = true
private var currentTab = "viewer"

/** Counter for geometry placement offset so shapes don't overlap. */
private var geometryCount = 0

fun main() {
    val canvas = document.getElementById("scene-canvas") as? HTMLCanvasElement
    if (canvas == null) {
        console.error("Canvas element 'scene-canvas' not found")
        return
    }

    canvas.width = canvas.clientWidth
    canvas.height = canvas.clientHeight

    // Initialize the 3D scene with a default model
    initSceneView(canvas, "https://sceneview.github.io/models/platforms/DamagedHelmet.glb")

    // Wire up tabs
    setupTabs()

    // Wire up Sketchfab search
    setupSearch()

    // Wire up geometry buttons
    setupGeometry()
}

// ---- Scene initialization ----

private fun initSceneView(canvas: HTMLCanvasElement, modelUrl: String) {
    showLoadingOverlay("Loading scene...")

    SceneView.create(
        canvas = canvas,
        configure = {
            camera {
                eye(0.0, 1.5, 5.0)
                target(0.0, 0.0, 0.0)
                fov(45.0)
            }
            light {
                directional()
                intensity(50_000.0)
                direction(0.6f, -1.0f, -0.8f)
            }
            model(modelUrl) {
                onLoaded {
                    hideLoadingOverlay()
                    console.log("Default model loaded")
                }
            }
            autoRotate(autoRotateEnabled)
        },
        onReady = { sceneView ->
            currentSceneView = sceneView
            sceneView.startRendering()

            window.addEventListener("resize", {
                canvas.width = canvas.clientWidth
                canvas.height = canvas.clientHeight
            })

            console.log("SceneView Web Demo initialized — SDK v$SDK_VERSION")

            setupXRButtons(canvas)
            setupAutoRotateToggle(sceneView)
        }
    )
}

/**
 * Load a model URL into the current scene by destroying and recreating.
 */
private fun loadModelIntoScene(url: String, name: String) {
    val canvas = document.getElementById("scene-canvas") as? HTMLCanvasElement ?: return
    showLoadingChip("Loading $name...")

    currentSceneView?.destroy()
    currentSceneView = null

    SceneView.create(
        canvas = canvas,
        configure = {
            camera {
                eye(0.0, 1.5, 5.0)
                target(0.0, 0.0, 0.0)
                fov(45.0)
            }
            light {
                directional()
                intensity(50_000.0)
                direction(0.6f, -1.0f, -0.8f)
            }
            model(url) {
                onLoaded {
                    hideLoadingChip()
                    console.log("Model loaded: $name")
                }
            }
            autoRotate(autoRotateEnabled)
        },
        onReady = { sceneView ->
            currentSceneView = sceneView
            sceneView.startRendering()

            window.addEventListener("resize", {
                canvas.width = canvas.clientWidth
                canvas.height = canvas.clientHeight
            })

            setupXRButtons(canvas)
            setupAutoRotateToggle(sceneView)
        }
    )
}

// ---- Tab navigation ----

private fun setupTabs() {
    val tabButtons = document.querySelectorAll(".tab-btn")
    for (i in 0 until tabButtons.length) {
        val btn = tabButtons.item(i) as? HTMLElement ?: continue
        btn.addEventListener("click", {
            val tab = btn.getAttribute("data-tab") ?: return@addEventListener
            switchTab(tab)
        })
    }
}

private fun switchTab(tab: String) {
    currentTab = tab

    // Update tab button states
    val tabButtons = document.querySelectorAll(".tab-btn")
    for (i in 0 until tabButtons.length) {
        val btn = tabButtons.item(i) as? HTMLElement ?: continue
        val btnTab = btn.getAttribute("data-tab")
        btn.className = if (btnTab == tab) "tab-btn active" else "tab-btn"
    }

    // Show/hide panels
    val panels = arrayOf("viewer", "geometry")
    panels.forEach { panelName ->
        val panel = document.getElementById("panel-$panelName") as? HTMLElement
        panel?.className = if (panelName == tab) {
            panel.className.replace(" active", "") + " active"
        } else {
            panel.className.replace(" active", "")
        }
    }

    // Move controls info out of the way when side panel is active
    val controlsInfo = document.getElementById("controls-info") as? HTMLElement
    controlsInfo?.style?.left = if (tab == "viewer" || tab == "geometry") "360px" else "20px"
}

// ---- Sketchfab search ----

private fun setupSearch() {
    val searchInput = document.getElementById("search-input") as? HTMLInputElement ?: return
    val searchBtn = document.getElementById("search-btn") as? HTMLButtonElement ?: return

    searchBtn.addEventListener("click", {
        val query = searchInput.value.trim()
        if (query.isNotEmpty()) {
            performSearch(query)
        }
    })

    searchInput.addEventListener("keydown", { event ->
        val keyEvent = event.asDynamic()
        if (keyEvent.key == "Enter") {
            val query = searchInput.value.trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }
    })
}

private fun performSearch(query: String) {
    val resultsContainer = document.getElementById("search-results") as? HTMLElement ?: return
    resultsContainer.innerHTML = "<div class='search-status'>Searching for \"$query\"...</div>"

    val url = "$SKETCHFAB_API&q=${encodeURIComponent(query)}"

    window.fetch(url).then { response ->
        if (!response.ok) {
            throw Error("HTTP ${response.status}")
        }
        response.json()
    }.then { data ->
        val json = data.asDynamic()
        val results = json.results
        val count = (results.length as? Number)?.toInt() ?: 0

        if (count == 0) {
            resultsContainer.innerHTML =
                "<div class='search-status'>No downloadable models found for \"$query\".</div>"
            return@then
        }

        resultsContainer.innerHTML = ""

        for (i in 0 until count) {
            val model = results[i]
            val name = (model.name as? String) ?: "Untitled"
            val uid = (model.uid as? String) ?: continue
            val authorName = model.user?.displayName as? String ?: model.user?.username as? String ?: "Unknown"
            val viewCount = (model.viewCount as? Number)?.toInt() ?: 0

            // Get thumbnail URL
            val thumbUrl = extractThumbnail(model)

            val card = document.createElement("div") as HTMLElement
            card.className = "result-card"
            card.innerHTML = """
                <img class="result-thumb" src="$thumbUrl" alt="$name" loading="lazy" onerror="this.style.display='none'">
                <div class="result-info">
                    <div class="result-name" title="$name">$name</div>
                    <div class="result-author">by $authorName</div>
                    <div class="result-views">$viewCount views</div>
                </div>
            """.trimIndent()

            card.addEventListener("click", {
                // Highlight active card
                val cards = document.querySelectorAll(".result-card")
                for (j in 0 until cards.length) {
                    (cards.item(j) as? HTMLElement)?.className = "result-card"
                }
                card.className = "result-card active"

                // Try to get the direct GLB download URL via the Sketchfab download API
                fetchModelDownloadUrl(uid, name)
            })

            resultsContainer.appendChild(card)
        }
    }.catch { error ->
        resultsContainer.innerHTML =
            "<div class='search-status error'>Search failed: ${error.asDynamic().message ?: error}</div>"
        console.error("Sketchfab search error:", error)
    }
}

/**
 * Extract the best thumbnail URL from a Sketchfab search result.
 */
private fun extractThumbnail(model: dynamic): String {
    val thumbnails = model.thumbnails
    if (thumbnails == null) return ""
    val images = thumbnails.images
    if (images == null) return ""
    val count = (images.length as? Number)?.toInt() ?: 0
    // Pick a medium-sized thumbnail
    for (i in 0 until count) {
        val img = images[i]
        val width = (img.width as? Number)?.toInt() ?: 0
        if (width in 100..400) {
            return (img.url as? String) ?: ""
        }
    }
    // Fallback: first image
    if (count > 0) {
        return (images[0].url as? String) ?: ""
    }
    return ""
}

/**
 * Fetch the download URL for a Sketchfab model.
 * Note: The Sketchfab download API requires authentication for most models.
 * For the demo, we attempt the public glTF download endpoint.
 * If it fails, we show a message suggesting the user use a direct GLB URL.
 */
private fun fetchModelDownloadUrl(uid: String, name: String) {
    val downloadUrl = "https://api.sketchfab.com/v3/models/$uid/download"

    window.fetch(downloadUrl).then { response ->
        if (!response.ok) {
            // Most Sketchfab models require auth for download.
            // Show a helpful message with the Sketchfab page link.
            showLoadingChip("Auth required — open model on Sketchfab")
            window.setTimeout({
                hideLoadingChip()
            }, 3000)
            // Open the model page so user can download manually
            window.open("https://sketchfab.com/3d-models/$uid", "_blank")
            return@then Unit.asDynamic()
        }
        response.json()
    }.then<dynamic> { data ->
        if (data == null || data == Unit.asDynamic()) return@then Unit.asDynamic()
        val json = data.asDynamic()
        // The download response has gltf.url for the GLB file
        val glbUrl = json.gltf?.url as? String
        if (glbUrl != null) {
            loadModelIntoScene(glbUrl, name)
        } else {
            showLoadingChip("No GLB available for this model")
            window.setTimeout({ hideLoadingChip() }, 3000)
        }
    }.catch { error ->
        console.error("Download API error for $uid:", error)
        showLoadingChip("Download unavailable — opening on Sketchfab")
        window.setTimeout({ hideLoadingChip() }, 3000)
        window.open("https://sketchfab.com/3d-models/$uid", "_blank")
    }
}

// ---- Geometry showcase ----

private fun setupGeometry() {
    // Add buttons
    val addButtons = document.querySelectorAll(".geo-add-btn")
    for (i in 0 until addButtons.length) {
        val btn = addButtons.item(i) as? HTMLButtonElement ?: continue
        val geoType = btn.getAttribute("data-geo") ?: continue
        btn.addEventListener("click", {
            addGeometryToScene(geoType)
        })
    }

    // Clear button
    val clearBtn = document.getElementById("geo-clear") as? HTMLButtonElement
    clearBtn?.addEventListener("click", {
        clearAndReinitScene()
    })
}

/**
 * Parse hex color to RGBA doubles (0.0-1.0).
 */
private fun hexToRgb(hex: String): Triple<Double, Double, Double> {
    val clean = hex.removePrefix("#")
    val r = clean.substring(0, 2).toInt(16) / 255.0
    val g = clean.substring(2, 4).toInt(16) / 255.0
    val b = clean.substring(4, 6).toInt(16) / 255.0
    return Triple(r, g, b)
}

private fun addGeometryToScene(geoType: String) {
    val canvas = document.getElementById("scene-canvas") as? HTMLCanvasElement ?: return
    val sceneView = currentSceneView

    // Get color from the picker
    val colorPicker = document.querySelector("[data-geo-color='$geoType']") as? HTMLInputElement
    val colorHex = colorPicker?.value ?: "#4488ff"
    val (r, g, b) = hexToRgb(colorHex)

    // Get size from slider
    val sizeSlider = document.querySelector("[data-geo-size='$geoType']") as? HTMLInputElement
    val sizeVal = sizeSlider?.value?.toDoubleOrNull() ?: 1.0

    // Read the per-shape unlit checkbox — opt-in to flat colour via KHR_materials_unlit.
    val unlitCheckbox = document.querySelector("[data-geo-unlit='$geoType']") as? HTMLInputElement
    val unlitEnabled = unlitCheckbox?.checked ?: false

    // Offset each new geometry so they don't stack
    val offsetX = (geometryCount % 5 - 2) * 2.0
    val offsetZ = (geometryCount / 5) * -2.0
    geometryCount++

    if (sceneView == null) {
        // No scene yet — create one with this geometry
        SceneView.create(
            canvas = canvas,
            configure = {
                camera {
                    eye(0.0, 3.0, 8.0)
                    target(0.0, 0.0, 0.0)
                    fov(45.0)
                }
                geometry {
                    type(geoType)
                    color(r, g, b)
                    if (unlitEnabled) unlit()
                    position(offsetX, 0.5, offsetZ)
                    when (geoType) {
                        "cube" -> size(sizeVal)
                        "sphere" -> radius(sizeVal)
                        "cylinder" -> { radius(sizeVal); height(sizeVal * 2.0) }
                        "plane" -> size(sizeVal, 0.01, sizeVal)
                    }
                }
                autoRotate(autoRotateEnabled)
            },
            onReady = { sv ->
                currentSceneView = sv
                sv.startRendering()
                setupXRButtons(canvas)
                setupAutoRotateToggle(sv)
                console.log("Scene created with $geoType geometry (unlit=$unlitEnabled)")
            }
        )
    } else {
        // Add geometry to existing scene
        sceneView.addGeometry(
            io.github.sceneview.web.nodes.GeometryConfig().apply {
                type(geoType)
                color(r, g, b)
                if (unlitEnabled) unlit()
                position(offsetX, 0.5, offsetZ)
                when (geoType) {
                    "cube" -> size(sizeVal)
                    "sphere" -> radius(sizeVal)
                    "cylinder" -> { radius(sizeVal); height(sizeVal * 2.0) }
                    "plane" -> size(sizeVal, 0.01, sizeVal)
                }
            }
        )
        console.log("Added $geoType geometry (color=$colorHex, size=$sizeVal, unlit=$unlitEnabled)")
    }
}

private fun clearAndReinitScene() {
    val canvas = document.getElementById("scene-canvas") as? HTMLCanvasElement ?: return
    currentSceneView?.destroy()
    currentSceneView = null
    geometryCount = 0

    // Reinitialize with empty scene
    SceneView.create(
        canvas = canvas,
        configure = {
            camera {
                eye(0.0, 3.0, 8.0)
                target(0.0, 0.0, 0.0)
                fov(45.0)
            }
            autoRotate(autoRotateEnabled)
        },
        onReady = { sv ->
            currentSceneView = sv
            sv.startRendering()
            setupXRButtons(canvas)
            setupAutoRotateToggle(sv)
            console.log("Scene cleared")
        }
    )
}

// ---- Auto-rotate toggle ----

private fun setupAutoRotateToggle(sceneView: SceneView) {
    val btn = document.getElementById("auto-rotate-toggle") as? HTMLButtonElement ?: return
    btn.className = if (autoRotateEnabled) "auto-rotate-btn active" else "auto-rotate-btn"

    val newBtn = btn.cloneNode(true) as HTMLButtonElement
    btn.parentNode?.replaceChild(newBtn, btn)

    newBtn.addEventListener("click", {
        autoRotateEnabled = !autoRotateEnabled
        sceneView.cameraController?.autoRotate = autoRotateEnabled
        newBtn.className = if (autoRotateEnabled) "auto-rotate-btn active" else "auto-rotate-btn"
    })
}

// ---- Loading UI ----

private fun showLoadingOverlay(text: String) {
    val overlay = document.getElementById("loading-overlay") as? HTMLElement ?: return
    overlay.className = "loading-overlay"
    val modelLabel = document.getElementById("loading-model-name") as? HTMLElement
    modelLabel?.textContent = text
}

private fun hideLoadingOverlay() {
    val overlay = document.getElementById("loading-overlay") as? HTMLElement ?: return
    overlay.className = "loading-overlay hidden"
}

private fun showLoadingChip(text: String) {
    val chip = document.getElementById("loading-chip") as? HTMLElement ?: return
    val chipText = document.getElementById("loading-chip-text") as? HTMLElement
    chipText?.textContent = text
    chip.className = "loading-chip visible"
}

private fun hideLoadingChip() {
    val chip = document.getElementById("loading-chip") as? HTMLElement ?: return
    chip.className = "loading-chip"
}

// ---- WebXR ----

private fun setupXRButtons(canvas: HTMLCanvasElement) {
    WebXRSession.checkSupport(XRSessionMode.IMMERSIVE_AR) { arSupported ->
        if (arSupported) {
            val arButton = document.getElementById("enter-ar") as? HTMLButtonElement
            if (arButton != null) {
                arButton.style.display = "inline-block"
                arButton.addEventListener("click", {
                    enterAR(canvas)
                })
            }
        }
    }

    WebXRSession.checkSupport(XRSessionMode.IMMERSIVE_VR) { vrSupported ->
        if (vrSupported) {
            val vrButton = document.getElementById("enter-vr") as? HTMLButtonElement
            if (vrButton != null) {
                vrButton.style.display = "inline-block"
                vrButton.addEventListener("click", {
                    enterVR(canvas)
                })
            }
        }
    }
}

private fun enterAR(canvas: HTMLCanvasElement) {
    ARSceneView.create(
        canvas = canvas,
        onError = { error ->
            console.error("AR Error: $error")
            window.alert("Failed to start AR: $error")
        },
        onReady = { arSession ->
            console.log("AR session started — tap to place content")

            arSession.onHitTest = { pose ->
                // Hit test running — pose.transform.position has the surface point
            }

            arSession.onSelect = { inputSource ->
                arSession.loadModel(
                    "https://sceneview.github.io/models/platforms/DamagedHelmet.glb"
                ) { asset ->
                    console.log("Model placed in AR")
                }
            }

            arSession.onSessionEnd = {
                console.log("AR session ended")
            }

            arSession.start()
        }
    )
}

private fun enterVR(canvas: HTMLCanvasElement) {
    VRSceneView.create(
        canvas = canvas,
        onError = { error ->
            console.error("VR Error: $error")
            window.alert("Failed to start VR: $error")
        },
        onReady = { vrSession ->
            console.log("VR session started")

            vrSession.loadModel(
                "https://sceneview.github.io/models/platforms/DamagedHelmet.glb"
            )

            vrSession.onInputSelect = { source, pose ->
                console.log("VR select from ${source.handedness} hand")
            }

            vrSession.onSessionEnd = {
                console.log("VR session ended")
            }

            vrSession.start()
        }
    )
}

// ---- Utility ----

/**
 * URL-encode a string for query parameters.
 */
private fun encodeURIComponent(value: String): String {
    return js("encodeURIComponent(value)") as String
}
