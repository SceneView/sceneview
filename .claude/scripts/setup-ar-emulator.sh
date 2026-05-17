#!/usr/bin/env bash
# setup-ar-emulator.sh — bootstrap a reusable ARCore-friendly Android emulator
#
# Why this exists:
#   SceneView QA had been routinely using a personal Pixel 9 over USB. That's risky
#   (it squats the user's phone, AR sessions can leak GPS/camera data, app installs
#   can crash and need manual recovery). #1241 mandates emulator-first QA.
#
# What it does:
#   1. Ensures the AVD `Pixel_7a` exists. The AVD *name* is kept for back-compat
#      with the QA scripts that hard-code it; the underlying device profile is
#      now pixel_8 (see DEVICE_PROFILE_PREFS below).
#   2. Builds it to an ANR-resistant QA spec: 6 CPU cores, 1 GB VM heap, 12 GB
#      data partition, host-sized RAM (see below), virtualscene back camera,
#      emulated front camera (front cam is needed for Augmented Faces demos),
#      host GPU mode. An AVD still on the old 4-core / 4 GB spec is rebuilt.
#   3. Boots the emulator headless.
#   4. Waits for `sys.boot_completed`.
#   5. Verifies Google Play Services for AR (com.google.ar.core) is installed;
#      if not, opens the Play Store to the listing (one-time manual click).
#
# Why the higher spec:
#   A SceneView 3D demo renders through Filament (host GPU) while QA runs
#   `adb shell screenrecord` at the same time — the recorder's H.264 encoder is
#   a software thread on the *guest*. On the stock 4-core / 4 GB AVD the guest
#   `system_server` gets CPU/memory-starved and ANRs ("System UI isn't
#   responding"), aborting the recording. More cores + RAM give it headroom.
#
#   RAM is sized to the *host*, not pinned at 8 GB: an 8 GB guest on a 16 GB Mac
#   that is also running the Gradle build + agent tooling gets SIGTERM'd by
#   macOS under memory pressure mid-QA — which aborts the recording just as
#   surely as an ANR. detect_target_ram_mb() reserves 10 GB for the host and
#   clamps the guest to [4 GB, 8 GB]: ~6 GB on a 16 GB Mac, the full 8 GB on a
#   24 GB+ Mac. The load-bearing ANR fix is the core count, not raw RAM.
#
# Flags:
#   --check   Read-only inspection: prints current AVD config + ARCore state. No mutation.
#   --clean   Wipe the AVD's userdata and recreate config from scratch.
#   --no-boot Skip the emulator boot (useful in CI where boot is deferred).
#   -h|--help Show this help.
#
# Idempotent: re-running with no flag will skip work that's already done. Re-uses an
# already-booted emulator on the standard adb port. Stops nothing — caller closes the
# emulator (or it stays warm for follow-up scripts).

set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
EMULATOR_BIN="$SDK_ROOT/emulator/emulator"
ADB_BIN="$SDK_ROOT/platform-tools/adb"
AVDMANAGER_BIN="$SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
AVD_NAME="Pixel_7a"
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
AVD_CONFIG="$AVD_HOME/$AVD_NAME.avd/config.ini"
SYSTEM_IMAGE="system-images;android-36;google_apis_playstore;arm64-v8a"

# Detect arch for the system image
HOST_ARCH="$(uname -m)"
case "$HOST_ARCH" in
  arm64|aarch64) IMG_ARCH="arm64-v8a" ;;
  x86_64|amd64)  IMG_ARCH="x86_64" ;;
  *) echo "[setup-ar] unsupported host arch $HOST_ARCH" >&2; exit 1 ;;
esac
SYSTEM_IMAGE="system-images;android-36;google_apis_playstore;$IMG_ARCH"

# --- ANR-resistant QA spec ----------------------------------------------------
# Guest RAM is sized to the host so macOS never SIGTERMs the emulator under
# memory pressure (that kills a QA recording exactly like an ANR does). Reserve
# 10 GB for the host (macOS + the Gradle build + the agent driving QA), clamp
# the guest to [4 GB, 8 GB]. 16 GB Mac -> ~6 GB guest; 24 GB+ Mac -> 8 GB.
detect_target_ram_mb() {
  local host_bytes host_mb ram
  host_bytes="$(sysctl -n hw.memsize 2>/dev/null || echo '')"
  if [[ -z "$host_bytes" ]]; then
    echo 6144; return            # detection failed — safe middle value
  fi
  host_mb=$(( host_bytes / 1024 / 1024 ))
  ram=$(( host_mb - 10240 ))
  (( ram < 4096 )) && ram=4096
  (( ram > 8192 )) && ram=8192
  echo "$ram"
}
TARGET_RAM_MB="$(detect_target_ram_mb)"
TARGET_HEAP_MB=1024
TARGET_NCORE=6
TARGET_DATA_PARTITION="12G"
# Most-capable recent Pixel profile actually installed in the SDK. Pixel 9 ships
# no `avdmanager` profile yet; pixel_8 has the same 1080x2400 @ 420dpi geometry
# as pixel_7a (so the screenshot-crop math in capture-play-store-screenshots.sh
# is unchanged) but is the newer reference device. Falls down the list if absent.
DEVICE_PROFILE_PREFS=(pixel_9_pro pixel_9 pixel_8 pixel_8a pixel_7a pixel_7)

CHECK_ONLY=false
CLEAN=false
NO_BOOT=false
STOP_AFTER=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check)   CHECK_ONLY=true; shift ;;
    --clean)   CLEAN=true; shift ;;
    --no-boot) NO_BOOT=true; shift ;;
    --stop)    STOP_AFTER=true; shift ;;
    -h|--help)
      # Print the leading comment block (lines starting with #), stop at `set -`.
      # Skip the shebang (line 1).
      awk 'NR==1{next} /^set -/{exit} /^#/{sub(/^# ?/,""); print}' "$0"
      exit 0 ;;
    *) echo "[setup-ar] unknown flag: $1" >&2; exit 2 ;;
  esac
done

log() { echo "[setup-ar] $*"; }

# --- step 1: verify SDK basics -------------------------------------------------
[[ -x "$EMULATOR_BIN" ]] || { log "missing emulator at $EMULATOR_BIN"; exit 1; }
[[ -x "$ADB_BIN" ]] || { log "missing adb at $ADB_BIN"; exit 1; }
[[ -x "$AVDMANAGER_BIN" ]] || { log "missing avdmanager at $AVDMANAGER_BIN"; exit 1; }

# --- step 1b: emulator-version guard ------------------------------------------
# Android Emulator 36.x REGRESSED `adb shell screenrecord`: it no longer
# captures host-GPU-composited frames, so a Filament 3D scene records as a
# near-empty ~0.5 fps mp4 (verified: 36.5.11 -> 23 frames / 48 s; 35.6.11 ->
# 2541 frames). That silently breaks every QA screen recording while leaving
# the demo itself running fine — the worst kind of regression. 35.6.11 is the
# last version where screenrecord captures GPU content correctly, so the
# SceneView QA emulator is pinned there. If `sdkmanager` has since pulled a
# 36+ emulator, warn loudly with the one-shot re-pin recipe.
KNOWN_GOOD_EMULATOR_BUILD=13610412   # emulator 35.6.11
check_emulator_version() {
  local ver major
  ver="$("$EMULATOR_BIN" -version 2>/dev/null | sed -nE 's/.*version ([0-9]+)\..*/\1/p' | head -1)"
  major="${ver:-0}"
  if [[ "$major" -ge 36 ]]; then
    log "WARNING ────────────────────────────────────────────────────────────"
    log "  Emulator major version $major detected. Emulator 36.x breaks"
    log "  'adb shell screenrecord' capture of host-GPU content — SceneView QA"
    log "  recordings will be near-empty. Re-pin to 35.6.11 (build"
    log "  $KNOWN_GOOD_EMULATOR_BUILD), after killing any running emulator:"
    log "    curl -fsSL -o /tmp/emu.zip \\"
    log "      https://dl.google.com/android/repository/emulator-darwin_aarch64-${KNOWN_GOOD_EMULATOR_BUILD}.zip"
    log "    rm -rf '$SDK_ROOT/emulator' && unzip -q /tmp/emu.zip -d '$SDK_ROOT'"
    log "  (use emulator-linux_x64-${KNOWN_GOOD_EMULATOR_BUILD}.zip on Linux.)"
    log "─────────────────────────────────────────────────────────────────────"
  fi
}
check_emulator_version

# --- step 2: verify system image is installed ---------------------------------
SYS_IMAGE_DIR="$SDK_ROOT/system-images/android-36/google_apis_playstore/$IMG_ARCH"
if [[ ! -d "$SYS_IMAGE_DIR" ]]; then
  if $CHECK_ONLY; then
    log "MISSING: $SYS_IMAGE_DIR (install with: sdkmanager '$SYSTEM_IMAGE')"
    exit 1
  fi
  log "installing system image: $SYSTEM_IMAGE"
  yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" "$SYSTEM_IMAGE" || {
    log "sdkmanager failed; check ANDROID_SDK_ROOT and licenses"; exit 1
  }
fi

# --- step 3: create or recreate the AVD ---------------------------------------
recreate_avd() {
  # Pick the most-capable device profile this SDK actually has installed.
  local avail device_arg=""
  avail="$("$AVDMANAGER_BIN" list device 2>/dev/null || true)"
  for cand in "${DEVICE_PROFILE_PREFS[@]}"; do
    if grep -qE "id: [0-9]+ or \"$cand\"" <<<"$avail"; then
      device_arg="$cand"; break
    fi
  done
  [[ -z "$device_arg" ]] && device_arg="pixel_7"
  log "creating AVD $AVD_NAME (device=$device_arg, image=$SYSTEM_IMAGE)"
  echo "no" | "$AVDMANAGER_BIN" create avd \
    --name "$AVD_NAME" \
    --package "$SYSTEM_IMAGE" \
    --device "$device_arg" \
    --force >/dev/null
}

# True if the AVD is absent or built on the old low-spec config, so a full run
# rebuilds it into the ANR-resistant spec. RAM + cores are the load-bearing keys
# (a fresh recreate also clears accumulated userdata cruft and the larger data
# partition only takes effect on a clean image).
avd_is_stale() {
  [[ -f "$AVD_CONFIG" ]] || return 0
  local ram ncore
  ram="$(awk -F'=' '/^hw\.ramSize/{gsub(/ /,"",$2);print $2}' "$AVD_CONFIG")"
  ncore="$(awk -F'=' '/^hw\.cpu\.ncore/{gsub(/ /,"",$2);print $2}' "$AVD_CONFIG")"
  [[ "${ram:-0}" -ge "$TARGET_RAM_MB" && "${ncore:-0}" -ge "$TARGET_NCORE" ]] && return 1
  return 0
}

if $CLEAN; then
  if $CHECK_ONLY; then
    log "--check and --clean are mutually exclusive"; exit 2
  fi
  log "removing existing AVD $AVD_NAME"
  "$AVDMANAGER_BIN" delete avd --name "$AVD_NAME" 2>/dev/null || true
fi

if $CHECK_ONLY; then
  if [[ ! -f "$AVD_CONFIG" ]]; then
    log "MISSING AVD $AVD_NAME (would create on full run)"
    exit 1
  fi
  avd_is_stale && log "AVD $AVD_NAME is below the ANR-resistant spec (RAM/cores) — a full run would rebuild it"
elif avd_is_stale; then
  if [[ -f "$AVD_CONFIG" ]]; then
    log "AVD $AVD_NAME is below the ANR-resistant spec — rebuilding"
    "$AVDMANAGER_BIN" delete avd --name "$AVD_NAME" 2>/dev/null || true
  fi
  recreate_avd
fi

# --- step 4: patch config.ini for ARCore --------------------------------------
# Pinned values: host-sized RAM / 1 GB heap / 6 cores / 12 GB data (ANR-resistant
# QA), virtualscene back camera (ARCore can drive it), emulated front (Augmented
# Faces), host GPU mode.
patch_kv() {
  local key="$1"
  local value="$2"
  local file="$3"
  # Use [[:space:]] for portability (BSD sed in macOS doesn't honor \s)
  local key_escaped="${key//./\\.}"
  if grep -qE "^${key_escaped}[[:space:]]*=" "$file"; then
    # GNU/BSD sed compat: write to .bak then move
    sed -i.bak -E "s|^(${key_escaped})[[:space:]]*=.*|\1 = $value|" "$file" && rm -f "$file.bak"
  else
    echo "$key = $value" >> "$file"
  fi
}

apply_ar_config() {
  log "patching $AVD_CONFIG for ARCore + ANR-resistant QA"
  patch_kv "PlayStore.enabled" "yes" "$AVD_CONFIG"
  patch_kv "hw.camera.back"   "virtualscene" "$AVD_CONFIG"
  patch_kv "hw.camera.front"  "emulated"     "$AVD_CONFIG"
  patch_kv "hw.gpu.enabled"   "yes"          "$AVD_CONFIG"
  patch_kv "hw.gpu.mode"      "host"         "$AVD_CONFIG"
  # Headroom so a Filament render + screenrecord don't starve system_server.
  patch_kv "hw.ramSize"       "$TARGET_RAM_MB"         "$AVD_CONFIG"
  patch_kv "vm.heapSize"      "$TARGET_HEAP_MB"        "$AVD_CONFIG"
  patch_kv "hw.cpu.ncore"     "$TARGET_NCORE"          "$AVD_CONFIG"
  patch_kv "disk.dataPartition.size" "$TARGET_DATA_PARTITION" "$AVD_CONFIG"
  patch_kv "hw.sensors.orientation" "yes"    "$AVD_CONFIG"
  patch_kv "hw.sensors.proximity"   "yes"    "$AVD_CONFIG"
  patch_kv "hw.gps"           "yes"          "$AVD_CONFIG"
}

show_config() {
  log "current AVD config (QA-relevant keys):"
  grep -E "^(PlayStore|hw\.camera|hw\.gpu|hw\.ramSize|vm\.heapSize|hw\.cpu\.ncore|disk\.dataPartition\.size|hw\.device\.name|hw\.gps|image\.sysdir)" "$AVD_CONFIG" | sed 's/^/  /'
}

if $CHECK_ONLY; then
  show_config
else
  apply_ar_config
  show_config
fi

# --- step 5: boot the emulator ------------------------------------------------
emulator_is_running() {
  "$ADB_BIN" devices 2>/dev/null | awk 'NR>1 && /emulator-/ && $2=="device" {found=1} END {exit !found}'
}

boot_emulator() {
  if emulator_is_running; then
    log "emulator already running — re-using"
    return 0
  fi
  log "starting emulator $AVD_NAME (headless, no snapshot)"
  # Use nohup + & so the emulator survives this script. Redirect to a log so we
  # can debug a failed boot without spinning up an interactive console.
  local emu_log="/tmp/sceneview-emulator-$AVD_NAME.log"
  nohup "$EMULATOR_BIN" -avd "$AVD_NAME" \
    -gpu host \
    -cores "$TARGET_NCORE" \
    -no-snapshot-load \
    -no-snapshot-save \
    -no-audio \
    -no-boot-anim \
    -netdelay none -netspeed full \
    >"$emu_log" 2>&1 &
  log "emulator pid=$! log=$emu_log"
}

wait_for_boot() {
  log "waiting for boot complete (a fresh cold boot of the recreated AVD can take 5+ min)"
  local serial
  # Poll for first emulator-* device showing up. A fresh-userdata cold boot of
  # the heavier AVD genuinely takes minutes — keep this window generous so the
  # script doesn't bail while the emulator is still legitimately booting.
  for _ in $(seq 1 180); do
    serial="$("$ADB_BIN" devices | awk '/^emulator-[0-9]+/ && $2=="device" {print $1; exit}')"
    [[ -n "$serial" ]] && break
    sleep 2
  done
  if [[ -z "$serial" ]]; then
    log "no emulator-* device appeared after 360s — boot failed"
    return 1
  fi
  log "device $serial online, waiting for sys.boot_completed"
  "$ADB_BIN" -s "$serial" wait-for-device
  local i=0
  while [[ "$("$ADB_BIN" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" != "1" ]]; do
    i=$((i+1))
    if [[ $i -gt 150 ]]; then
      log "sys.boot_completed not set after 300s"
      return 1
    fi
    sleep 2
  done
  log "boot complete on $serial"
  export EMU_SERIAL="$serial"
}

if ! $CHECK_ONLY && ! $NO_BOOT; then
  boot_emulator
  wait_for_boot
fi

# --- step 6: verify / install ARCore (Google Play Services for AR) ------------
# The emulator's bundled Play Store does not auto-provision ARCore. We fetch the
# arm64/x86 APK straight from the public google-ar SDK GitHub release — it carries
# native code for both ABIs and installs cleanly on the emulator.
install_arcore() {
  local serial="$1"
  local tmp_apk="/tmp/sceneview-arcore.apk"
  log "resolving latest ARCore APK from google-ar/arcore-android-sdk"
  local apk_url
  apk_url="$(curl -fsSL "https://api.github.com/repos/google-ar/arcore-android-sdk/releases/latest" 2>/dev/null \
    | python3 -c "import json,sys
d=json.load(sys.stdin)
arch='$IMG_ARCH'
# x86 emulator build for x86_64 hosts, generic (arm64+armv7) APK otherwise
for a in d.get('assets',[]):
    n=a['name']
    if arch=='x86_64' and 'x86_for_emulator' in n:
        print(a['browser_download_url']); break
else:
    for a in d.get('assets',[]):
        n=a['name']
        if n.startswith('Google_Play_Services_for_AR_') and n.endswith('.apk') and 'x86_for_emulator' not in n:
            print(a['browser_download_url']); break
" 2>/dev/null)"
  if [[ -z "$apk_url" ]]; then
    log "could not resolve ARCore APK URL — install manually via Play Store"
    "$ADB_BIN" -s "$serial" shell am start -a android.intent.action.VIEW \
      -d 'market://details?id=com.google.ar.core' >/dev/null 2>&1 || true
    return 1
  fi
  log "downloading $apk_url"
  curl -fsSL -o "$tmp_apk" "$apk_url" || { log "ARCore download failed"; return 1; }
  log "installing ARCore on $serial"
  "$ADB_BIN" -s "$serial" install -r "$tmp_apk" >/dev/null 2>&1 || {
    log "ARCore install failed"; return 1
  }
}

serial="${EMU_SERIAL:-$("$ADB_BIN" devices | awk '/^emulator-[0-9]+/ && $2=="device" {print $1; exit}')}"
if [[ -n "$serial" ]]; then
  if "$ADB_BIN" -s "$serial" shell pm list packages 2>/dev/null | grep -q "com.google.ar.core"; then
    log "ARCore (com.google.ar.core) is installed on $serial"
  else
    log "ARCore NOT installed on $serial"
    if ! $CHECK_ONLY; then
      install_arcore "$serial" && log "ARCore installed OK on $serial"
    fi
  fi
else
  log "(no emulator running — skipped ARCore check)"
fi

# --- step 7: optionally stop the emulator -------------------------------------
# By default we leave the emulator warm so a QA script can run immediately after.
# --stop kills it for hygiene-conscious callers (CI, one-shot config runs).
if $STOP_AFTER && ! $CHECK_ONLY && [[ -n "$serial" ]]; then
  log "stopping emulator $serial (--stop)"
  "$ADB_BIN" -s "$serial" emu kill >/dev/null 2>&1 || true
fi

# --- step 8: summary ----------------------------------------------------------
log "done."
if $STOP_AFTER; then
  log "emulator stopped. Re-run without --stop to leave it warm for QA."
else
  log "emulator left running for QA. next steps:"
  log "  source .claude/scripts/lib/android-cli.sh && android_cli_ensure"
  log "  android run --apks <apk> --activity io.github.sceneview.demo/.MainActivity"
  log "  stop it with: $ADB_BIN -s ${serial:-emulator-5554} emu kill"
fi
