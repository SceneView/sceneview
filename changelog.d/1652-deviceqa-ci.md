<!-- category: Tests -->
- Device-QA CI: prebuild the android-demo APK in a separate cached `build-android-apk` job and install the artifact in the emulator legs (no cold build on the 2-core emulator runner); the release gate now grades `continue-on-error` legs — a red advisory leg (android/ar) surfaces as a WARN instead of being silent or hard-blocking (#1652, #1651).
