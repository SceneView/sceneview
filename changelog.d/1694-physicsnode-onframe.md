<!-- category: Fixed -->
`PhysicsNode` no longer clobbers or destroys the caller's existing `Node.onFrame` callback — it now saves the prior callback, chain-calls it each frame, and restores it on dispose (#1694).
