<!-- category: Fixed -->
- Model Viewer demo: the top-right "Streaming…" asset-source pill now clears to "Streamed" once a streamed model finishes loading, instead of staying pinned for the whole session. The streamed model instance is now loaded from a stable composable slot so the load-completion state invalidates the chip correctly (#1464).
