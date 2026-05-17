<!-- category: Tests -->
- AR replay device-QA harness (`ARReplayHarnessTest` + `ar-replay-qa.sh`) no
  longer reports a misleading `pass` when the recorded ARCore session was never
  actually replayed. ARCore dataset playback needs camera-stream support the
  x86 software-GPU CI emulator does not provide, so `ar-record-playback`
  advancing `replayedFrames: 0` is now graded `skipped` (with the reason
  surfaced) rather than green `alive`. `ar-qa-summary.json` gains `skipped` /
  `failed` counts and a per-demo `reason`; `ar-replay-qa.sh` exits `3` and the
  device-QA AR leg records `skipped` — skips never count as passes (#1645).
