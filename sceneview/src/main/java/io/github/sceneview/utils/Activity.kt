package io.github.sceneview.utils

import android.app.Activity
import android.view.WindowManager

/**
 * Toggle the activity window's keep-screen-on flag. When `true`, prevents the
 * display from dimming or turning off while the activity is in the foreground —
 * recommended for 3D / AR scenes the user observes for more than a few seconds
 * without actively touching the screen (camera tour, AR session, video).
 *
 * Internally sets / clears [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON]; no
 * permission is required and the flag is automatically cleared when the
 * activity goes to the background.
 *
 * @param keepScreenOn `true` to add the flag (display stays on), `false` to
 *   clear it. Defaults to `true` to match the common "enable on enter scene"
 *   call site.
 */
fun Activity.setKeepScreenOn(keepScreenOn: Boolean = true) {
    if (keepScreenOn) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
