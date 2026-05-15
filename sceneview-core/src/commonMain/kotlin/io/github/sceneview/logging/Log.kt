package io.github.sceneview.logging

/**
 * Logs a warning-level message using the host platform's logging facility.
 *
 * Cross-platform `expect` function — each target supplies its own implementation
 * (Android `java.util.logging`, iOS `NSLog`, JS `console.warn`).
 *
 * @param tag Short category/source label for the message.
 * @param message The warning text to log.
 */
expect fun logWarning(tag: String, message: String)
