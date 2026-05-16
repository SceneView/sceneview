plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    // Kotlin 2.x requires the Compose Compiler Gradle plugin. The version is
    // supplied by the host React Native app's Kotlin Gradle plugin (it ships
    // with the same coordinates), so it is applied without an explicit version
    // here — exactly like `org.jetbrains.kotlin.android` above. The legacy
    // `composeOptions { kotlinCompilerExtensionVersion }` block is obsolete
    // under Kotlin 2.x and has been removed.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.sceneview.reactnative"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // SceneView — track the last PUBLISHED release on Maven Central (4.7.0).
    // The bridge Kotlin already targets the 4.x API surface (the `SceneView { }`
    // composable, `rememberEngine`, the `SceneScope` node DSL); only these
    // coordinates lagged behind on the year-old 3.6.0. Keep this aligned with
    // the latest published artifact, NOT the in-development `VERSION_NAME`
    // (see #1494).
    implementation("io.github.sceneview:sceneview:4.7.0")
    implementation("io.github.sceneview:arsceneview:4.7.0")

    // React Native
    implementation("com.facebook.react:react-android")

    // Compose — BOM aligned with the repo's `gradle/libs.versions.toml`
    // (`composeBom = 2026.05.00`) to avoid an ABI mismatch with the SceneView
    // library, which is compiled against that same BOM.
    implementation(platform("androidx.compose:compose-bom:2026.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
}
