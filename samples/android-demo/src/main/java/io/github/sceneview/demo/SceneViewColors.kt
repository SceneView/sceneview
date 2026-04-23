package io.github.sceneview.demo

import androidx.compose.ui.graphics.Color

/**
 * SceneView brand palette, mirrored from `DESIGN.md` — the same tokens the website and docs
 * use. Every 3D demo (spheres, cubes, lines, shapes…) draws from these constants instead of
 * `Color.Red` / `Color.Green` defaults so the rendered scene matches the product identity.
 *
 * Source of truth: `DESIGN.md` → "Colors" table.
 *
 * ```
 * primary        #005bc1   SceneView blue
 * primary-hover  #0050aa   deeper blue
 * accent         #6446cd   hero-gradient end (purple)
 * accent-deep    #5a32a3   alt-hero-gradient end
 * tint-light     #a4c1ff   dark-mode primary / light tint
 * tint-soft      #d2a8ff   dark-mode gradient end
 * surface-dim    #161B22   dark surface — used for grounding planes
 * ```
 */
object SceneViewColors {
    /** Primary brand blue — #005bc1. The SceneView colour. */
    val Primary = Color(0xFF005BC1)

    /** Hover / pressed blue — #0050aa. Slightly darker than [Primary]. */
    val PrimaryHover = Color(0xFF0050AA)

    /** Hero-gradient end — #6446cd. The purple the brand gradient fades into. */
    val Accent = Color(0xFF6446CD)

    /** Alt-hero-gradient end — #5a32a3. Deeper purple for secondary hero treatments. */
    val AccentDeep = Color(0xFF5A32A3)

    /** Light-tint blue — #a4c1ff. Dark-mode primary, also used as the third ramp colour. */
    val TintLight = Color(0xFFA4C1FF)

    /** Soft-tint purple — #d2a8ff. Dark-mode gradient end, also the fourth ramp colour. */
    val TintSoft = Color(0xFFD2A8FF)

    /** Dark surface — #161B22. Used for ground planes and neutral backdrops in demos. */
    val SurfaceDim = Color(0xFF161B22)

    /**
     * Four-colour ramp used whenever a demo needs a palette of distinct but on-brand swatches
     * (PhysicsDemo spheres, GeometryDemo primitives, LinesPathsDemo lines, …). Walks from
     * [Primary] to [TintSoft] so adjacent elements always contrast enough to read.
     */
    val Ramp4 = listOf(Primary, Accent, TintLight, TintSoft)
}
