<!-- category: Fixed -->
- Camera Controls demo: the **Free Flight** camera mode no longer renders a black
  viewport on launch and is now usable on touch devices. Free-flight previously
  spawned the camera at the origin — inside the helmet model — and offered no touch
  gesture to translate (Filament drives flight movement from held keys). The demo now
  sets `flightStartPosition` to the framed home position and shows an on-screen
  movement pad (forward / back / strafe / up / down) wired to the manipulator's
  key controls. Each mode also shows a short usage hint. (#1428)
