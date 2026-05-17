<!-- category: Fixed -->
- Web `AutoCenterGate` now latches after a bounded number of framing passes (`MAX_FRAMING_PASSES = 10`), so an animated / skeletal / physics scene whose union diagonal jitters every frame stops re-centring the camera forever — parity with Android's `FramingGate` ceiling (#1633, #1629).
