<!-- category: Tests -->
- Device QA workflow (#1665): `workflow_dispatch` (release-gate) runs now get a unique, non-cancellable concurrency group keyed on `github.run_id`, so a subsequent push to `main` can no longer cancel an in-progress release-gate Device QA run. Push-triggered runs still share a `push` group and auto-cancel stale runs.
