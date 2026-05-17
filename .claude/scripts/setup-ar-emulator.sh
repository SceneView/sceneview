#!/usr/bin/env bash
# setup-ar-emulator.sh — bootstrap a reusable ARCore-friendly Android emulator
#
# Why this exists:
#   SceneView QA had been routinely using a personal Pixel 9 over USB. That's risky
#   (it squats the user's phone, AR sessions can leak GPS/camera data, app installs
#   can crash and need manual recovery). #1241 mandates emulator-first QA.
#
# What it does:
#   1. Ensures the AVD `Pixel_7a` exists (creates if missing).
#   2. Patches its config.ini for ARCore: 4 GB RAM, virtualscene back camera,
#      emulated front camera (front cam is needed for Augmented Faces demos),
#      host GPU mode.
#   3. Boots the emulator headless — RAM-aware and parallel-session-safe (#1647):
#      reuses an already-running emulator, checks free host RAM before a fresh
#      boot, scales the `-memory` flag to RAM headroom, and cooperates with
#      concurrent sessions via an advisory lock so only ONE emulator ever runs.
#   4. Waits for `sys.boot_completed`.
#   5. Verifies Google Play Services for AR (com.google.ar.core) is installed;
#      if not, opens the Play Store to the listing (one-time manual click).
#
# Flags:
#   --check   Read-only inspection: prints current AVD config + ARCore state +
#             host RAM / running-emulator status. No mutation.
#   --clean   Wipe the AVD's userdata and recreate config from scratch.
#   --no-boot Skip the emulator boot (useful in CI where boot is deferred).
#   -h|--help Show this help.
#
# Idempotent: re-running with no flag will skip work that's already done. Re-uses an
# already-booted emulator on the standard adb port. Stops nothing — caller closes the
# emulator (or it stays warm for follow-up scripts).
#
# Single-emulator guarantee (#1647): this script never boots a second emulator
# when one is already up, and two concurrent sessions cooperate via the advisory
# lock in lib/emulator-select.sh — the first boots, the rest reuse.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# RAM-aware single-emulator selection helpers (#1647): RAM monitoring, running-
# emulator reuse detection, RAM-scaled `-memory`, advisory parallel-session lock.
# shellcheck source=lib/emulator-select.sh
source "$SCRIPT_DIR/lib/emulator-select.sh"

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
  log "creating AVD $AVD_NAME with image $SYSTEM_IMAGE"
  # Some hosts don't have pixel_7a in `avdmanager list device`; fall back to pixel_7.
  local device_arg="pixel_7a"
  if ! "$AVDMANAGER_BIN" list device 2>/dev/null | grep -q "id: .*pixel_7a"; then
    device_arg="pixel_7"
    log "device profile pixel_7a not found, falling back to pixel_7"
  fi
  echo "no" | "$AVDMANAGER_BIN" create avd \
    --name "$AVD_NAME" \
    --package "$SYSTEM_IMAGE" \
    --device "$device_arg" \
    --force >/dev/null
}

if $CLEAN; then
  if $CHECK_ONLY; then
    log "--check and --clean are mutually exclusive"; exit 2
  fi
  log "removing existing AVD $AVD_NAME"
  "$AVDMANAGER_BIN" delete avd --name "$AVD_NAME" 2>/dev/null || true
fi

if [[ ! -f "$AVD_CONFIG" ]]; then
  if $CHECK_ONLY; then
    log "MISSING AVD $AVD_NAME (would create on full run)"
    exit 1
  fi
  recreate_avd
fi

# --- step 4: patch config.ini for ARCore --------------------------------------
# Pinned values: 4GB RAM, virtualscene back camera (ARCore can drive it),
# emulated front (Augmented Faces), host GPU mode, persistent disk.
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
  log "patching $AVD_CONFIG for ARCore"
  patch_kv "PlayStore.enabled" "yes" "$AVD_CONFIG"
  patch_kv "hw.camera.back"   "virtualscene" "$AVD_CONFIG"
  patch_kv "hw.camera.front"  "emulated"     "$AVD_CONFIG"
  patch_kv "hw.gpu.enabled"   "yes"          "$AVD_CONFIG"
  patch_kv "hw.gpu.mode"      "host"         "$AVD_CONFIG"
  patch_kv "hw.ramSize"       "4096"         "$AVD_CONFIG"
  patch_kv "vm.heapSize"      "576"          "$AVD_CONFIG"
  patch_kv "hw.sensors.orientation" "yes"    "$AVD_CONFIG"
  patch_kv "hw.sensors.proximity"   "yes"    "$AVD_CONFIG"
  patch_kv "hw.gps"           "yes"          "$AVD_CONFIG"
}

show_config() {
  log "current AVD config (ARCore-relevant keys):"
  grep -E "^(PlayStore|hw\.camera|hw\.gpu|hw\.ramSize|vm\.heapSize|hw\.gps|image\.sysdir)" "$AVD_CONFIG" | sed 's/^/  /'
}

# Report host RAM + running-emulator state (#1647). Used by --check and before
# every boot decision.
show_ram_and_emulator_status() {
  local total free running
  total="$(emu_host_total_ram_mb)"
  free="$(emu_host_free_ram_mb)"
  log "host RAM: ${free} MB free / ${total} MB total (boot threshold ${EMU_MIN_FREE_RAM_MB} MB)"
  if running="$(emu_running_serial "$ADB_BIN")"; then
    log "running emulator detected: $running — would REUSE it (no second boot)"
  else
    log "no emulator currently running"
    if emu_ram_allows_boot >/dev/null 2>&1; then
      log "RAM OK for a fresh boot — would launch with -memory $(emu_recommended_memory_mb) MB"
    else
      log "RAM too tight — a fresh boot would be REFUSED"
    fi
  fi
}

if $CHECK_ONLY; then
  show_config
  show_ram_and_emulator_status
else
  apply_ar_config
  show_config
fi

# --- step 5: boot the emulator ------------------------------------------------
# RAM-aware, single-emulator, parallel-session-safe (#1647). The decision flow:
#   1. Already an emulator running?  -> REUSE it, no boot, no lock needed.
#   2. Otherwise acquire the advisory lock so two sessions cooperate.
#   3. Re-check inside the lock (a peer may have booted while we waited) -> REUSE.
#   4. Gate on free host RAM; refuse a fresh boot on a RAM-starved host.
#   5. Boot with a `-memory` value scaled to current RAM headroom.

emulator_is_running() {
  emu_running_serial "$ADB_BIN" >/dev/null 2>&1
}

# Release the advisory lock on exit no matter how the script ends. emu_lock_release
# is a no-op unless THIS process owns the lock, so it is safe even when we reused.
trap 'emu_lock_release' EXIT

boot_emulator() {
  # Step 1: reuse a running emulator outright — never boot a second one.
  if emulator_is_running; then
    log "emulator already running ($(emu_running_serial "$ADB_BIN")) — re-using, no boot"
    return 0
  fi

  # Step 2: cooperate with concurrent sessions. The first to acquire the lock
  # boots; peers wait, then find the running emulator in step 3 and reuse it.
  if ! emu_lock_acquire "${EMU_LOCK_TIMEOUT:-300}"; then
    # Lock wait timed out — last-chance reuse check before giving up.
    if emulator_is_running; then
      log "lock timed out but an emulator is up — re-using $(emu_running_serial "$ADB_BIN")"
      return 0
    fi
    log "could not acquire the emulator lock and no emulator is running — aborting boot"
    return 1
  fi

  # Step 3: re-check under the lock — a peer may have booted while we waited.
  if emulator_is_running; then
    log "another session booted the emulator ($(emu_running_serial "$ADB_BIN")) — re-using"
    return 0
  fi

  # Step 4: RAM gate. Booting into a RAM-starved host is what causes the boot
  # failures this fix targets (#1647), so refuse rather than crash mid-boot.
  if ! emu_ram_allows_boot; then
    log "ABORT: insufficient free host RAM to boot a fresh emulator safely"
    log "       reuse a running emulator, close other apps, or lower EMU_MIN_FREE_RAM_MB"
    return 1
  fi

  # Step 5: boot with a RAM-scaled `-memory`. The AVD config pins hw.ramSize,
  # but `-memory` at launch wins and lets us right-size to the live host.
  local mem; mem="$(emu_recommended_memory_mb)"
  log "starting emulator $AVD_NAME (headless, no snapshot, -memory ${mem} MB)"
  # Use nohup + & so the emulator survives this script. Redirect to a log so we
  # can debug a failed boot without spinning up an interactive console.
  local emu_log="/tmp/sceneview-emulator-$AVD_NAME.log"
  nohup "$EMULATOR_BIN" -avd "$AVD_NAME" \
    -gpu host \
    -memory "$mem" \
    -no-snapshot-load \
    -no-audio \
    -no-boot-anim \
    -netdelay none -netspeed full \
    >"$emu_log" 2>&1 &
  log "emulator pid=$! log=$emu_log"
}

wait_for_boot() {
  log "waiting for boot complete (up to 180s)"
  local serial
  # Poll for first emulator-* device showing up
  for _ in $(seq 1 60); do
    serial="$("$ADB_BIN" devices | awk '/^emulator-[0-9]+/ && $2=="device" {print $1; exit}')"
    [[ -n "$serial" ]] && break
    sleep 2
  done
  if [[ -z "$serial" ]]; then
    log "no emulator-* device appeared after 120s — boot failed"
    return 1
  fi
  log "device $serial online, waiting for sys.boot_completed"
  "$ADB_BIN" -s "$serial" wait-for-device
  local i=0
  while [[ "$("$ADB_BIN" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" != "1" ]]; do
    i=$((i+1))
    if [[ $i -gt 90 ]]; then
      log "sys.boot_completed not set after 180s"
      return 1
    fi
    sleep 2
  done
  log "boot complete on $serial"
  export EMU_SERIAL="$serial"
}

if ! $CHECK_ONLY && ! $NO_BOOT; then
  # boot_emulator returns non-zero when it reused nothing AND refused a fresh
  # boot (RAM too tight, or the lock could not be acquired). In that case there
  # is no emulator to wait for — fail fast with a clear exit so the device-QA
  # orchestrator records a `skipped`/`failed` leg instead of hanging.
  if boot_emulator; then
    wait_for_boot
  else
    log "emulator boot was refused — see the reason above"
    exit 1
  fi
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
