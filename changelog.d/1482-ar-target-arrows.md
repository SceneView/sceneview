<!-- category: Added -->
- AR Orbital demo: an on-screen directional arrow now appears at the viewport edge whenever the chase target (the orbiting toy car) is outside the camera frustum, pointing the user toward it so they know which way to turn to catch it. The arrow is driven by a per-frame `projection · view · worldPoint` projection that also handles the behind-the-camera case (#1482).
