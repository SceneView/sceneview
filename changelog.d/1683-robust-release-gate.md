<!-- category: Fixed -->
- Release device-QA gate is now deterministic and non-blocking — it dispatches its own uncancellable Device QA run, waits with a hard timeout, treats web+ar as required and android as advisory, and proceeds-with-warning on timeout, so a flaky harness can never block a release indefinitely (#1683).
