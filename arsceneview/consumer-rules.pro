# Filament — JNI bridge classes must not be renamed or stripped
-keep class com.google.android.filament.** { *; }

# ARCore — JNI bridge
-keep class com.google.ar.core.** { *; }

# Kotlin-Math — used reflectively for transform operations
-keep class dev.romainguy.kotlin.math.** { *; }

# SceneView collision system — uses reflection for shape intersection
-keep class io.github.sceneview.collision.** { *; }

# Google Play Services Location — reflectively linked by ARCore's Geospatial /
# Terrain / Rooftop / Streetscape paths when Config.GeospatialMode.ENABLED is
# set. Without these keeps, R8 in release builds strips the FusedLocationProvider
# classes (no static call from consumer code) and ARCore raises:
#   "The Google Fused Location Provider for Android classes must be linked
#    into the app's binary when calling Session.configure() with Geospatial
#    mode enabled (Config.GeospatialMode.ENABLED)."
# Pixel 9 v4.3.0 production reproduces this — see issue #1178.
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.common.api.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
