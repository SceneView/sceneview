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
#   3. Boots it via `android emulator start` — Google's `android` CLI, which
#      blocks until the device is fully booted (raw `emulator` binary + a poll
#      loop as fallback when the CLI is absent).
#   4. Verifies Google Play Services for AR (com.google.ar.core) is installed;
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
# Google's `android` CLI — the AI-agent front-end for emulator/sdk/screen ops.
# Used for the emulator lifecycle (start/stop) and SDK installs; the raw
# emulator/adb/sdkmanager binaries are the fallback when it is not installed.
ANDROID_CLI_BIN="$(command -v android 2>/dev/null || echo "$HOME/.local/bin/android")"
ANDROID_CLI=(--no-metrics)   # global flags: never phone home from this repo
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
# Data-partition size is bounded by FREE DISK: the emulator pre-allocates the
# whole partition + ~2.5 GB of working images when it first boots a fresh AVD,
# and refuses to start if that doesn't fit (`Not enough space to create
# userdata partition`). A fixed 12 GB therefore makes a recreate fail outright
# on a full disk. Size it to free space instead — reserve 5 GB for the
# emulator's overhead + a margin, clamp to [4 GB, 8 GB]. 8 GB is ample for QA;
# the storage-degradation issue (#... AVD fills after ~6 runs) is handled by the
# periodic `--clean` rebuild, not by an oversized partition.
detect_target_data_gb() {
  local free_gb
  free_gb="$(df -g "$AVD_HOME" 2>/dev/null | awk 'NR==2{print $4}')"
  [[ "$free_gb" =~ ^[0-9]+$ ]] || { echo 6; return; }   # detection failed
  local data=$(( free_gb - 5 ))
  (( data < 4 )) && data=4
  (( data > 8 )) && data=8
  echo "$data"
}
TARGET_DATA_PARTITION="$(detect_target_data_gb)G"
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
  if [[ -x "$ANDROID_CLI_BIN" ]] && "$ANDROID_CLI_BIN" "${ANDROID_CLI[@]}" sdk install "$SYSTEM_IMAGE"; then
    log "system image installed via android CLI"
  else
    log "android CLI unavailable/failed — falling back to sdkmanager"
    yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" "$SYSTEM_IMAGE" || {
      log "sdkmanager failed; check ANDROID_SDK_ROOT and licenses"; exit 1
    }
  fi
fi

# --- step 3: create or recreate the AVD ---------------------------------------
recreate_avd() {
  # The emulator pre-allocates the data partition + ~2.5 GB of working images
  # the first time it boots a fresh AVD. Warn early if the disk can't fit it,
  # so the failure mode is a clear message here rather than a cryptic emulator
  # error five minutes into the boot.
  local free_gb need_gb
  free_gb="$(df -g "$AVD_HOME" 2>/dev/null | awk 'NR==2{print $4}')"
  need_gb=$(( ${TARGET_DATA_PARTITION%G} + 3 ))
  if [[ "$free_gb" =~ ^[0-9]+$ ]] && (( free_gb < need_gb )); then
    log "WARNING: only ${free_gb} GB free on the AVD volume; a fresh AVD needs"
    log "  ~${need_gb} GB — the emulator may refuse to create the userdata"
    log "  partition. Free disk space before this boot."
  fi
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
# `android emulator start` (Google's CLI) blocks until the device is fully
# booted AND ready, so we don't hand-roll a boot-wait poll loop whose timeout
# has to be guessed against cold-boot time. The raw `emulator` binary + a poll
# loop stays as a fallback for hosts without the CLI. GPU mode (hw.gpu.mode)
# and core count (hw.cpu.ncore) come from config.ini, so they apply either way.
emulator_is_running() {
  "$ADB_BIN" devices 2>/dev/null | awk 'NR>1 && /emulator-/ && $2=="device" {found=1} END {exit !found}'
}

current_emulator_serial() {
  "$ADB_BIN" devices | awk '/^emulator-[0-9]+/ && $2=="device" {print $1; exit}'
}

# We always cold-boot, so a saved snapshot is dead weight — drop it to keep the
# disk lean (the QA AVD otherwise accretes snapshot images across runs).
purge_snapshots() {
  rm -rf "$AVD_HOME/$AVD_NAME.avd/snapshots" 2>/dev/null || true
}

# Fallback boot path: raw emulator binary + poll loop. Used only when the
# `android` CLI is absent. A fresh-userdata cold boot of the heavier AVD takes
# minutes — keep the window generous so we don't bail mid-boot.
boot_emulator_raw() {
  local emu_log="/tmp/sceneview-emulator-$AVD_NAME.log"
  log "starting emulator $AVD_NAME via raw emulator binary; log=$emu_log"
  nohup "$EMULATOR_BIN" -avd "$AVD_NAME" \
    -gpu host -cores "$TARGET_NCORE" \
    -no-snapshot-load -no-snapshot-save -no-audio -no-boot-anim \
    -netdelay none -netspeed full \
    >"$emu_log" 2>&1 &
  log "emulator pid=$! — waiting for boot (can take 5+ min)"
  local serial=""
  for _ in $(seq 1 180); do
    serial="$(current_emulator_serial)"
    [[ -n "$serial" ]] && break
    sleep 2
  done
  [[ -z "$serial" ]] && { log "no emulator-* device appeared after 360s — boot failed"; return 1; }
  "$ADB_BIN" -s "$serial" wait-for-device
  local i=0
  while [[ "$("$ADB_BIN" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" != "1" ]]; do
    i=$((i+1))
    [[ $i -gt 150 ]] && { log "sys.boot_completed not set after 300s"; return 1; }
    sleep 2
  done
  EMU_SERIAL="$serial"
}

boot_emulator() {
  if emulator_is_running; then
    log "emulator already running — re-using"
    EMU_SERIAL="$(current_emulator_serial)"
    export EMU_SERIAL
    return 0
  fi
  purge_snapshots
  if [[ -x "$ANDROID_CLI_BIN" ]]; then
    log "starting emulator $AVD_NAME via android CLI ('emulator start --cold')"
    if "$ANDROID_CLI_BIN" "${ANDROID_CLI[@]}" emulator start --cold "$AVD_NAME"; then
      EMU_SERIAL="$(current_emulator_serial)"
      if [[ -n "$EMU_SERIAL" ]]; then
        export EMU_SERIAL
        log "emulator ready: $EMU_SERIAL"
        return 0
      fi
      log "android CLI returned but no emulator serial visible — falling back"
    else
      log "android CLI 'emulator start' failed — falling back to raw boot"
    fi
  else
    log "android CLI not found at $ANDROID_CLI_BIN — using raw emulator binary"
  fi
  boot_emulator_raw && export EMU_SERIAL
}

if ! $CHECK_ONLY && ! $NO_BOOT; then
  boot_emulator
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
  # Retry: right after boot the package manager can still reject installs while
  # it settles, so a single attempt is flaky.
  local attempt err
  for attempt in 1 2 3; do
    err="$("$ADB_BIN" -s "$serial" install -r "$tmp_apk" 2>&1)" && return 0
    log "ARCore install attempt $attempt/3 failed${err:+: ${err##*$'\n'}} — retrying in 3s"
    sleep 3
  done
  log "ARCore install failed after 3 attempts"
  return 1
}

# `android emulator start` hands back control at "ready", but the guest package
# manager can still be settling — `pm list`/`pm install` race right after boot
# (observed: ARCore install failed once, succeeded seconds later). Poll until pm
# answers before touching packages.
wait_for_pm() {
  local serial="$1" _
  for _ in $(seq 1 30); do
    "$ADB_BIN" -s "$serial" shell pm path android >/dev/null 2>&1 && return 0
    sleep 2
  done
  log "package manager not responding after 60s on $serial"
  return 1
}

serial="${EMU_SERIAL:-$("$ADB_BIN" devices | awk '/^emulator-[0-9]+/ && $2=="device" {print $1; exit}')}"
if [[ -n "$serial" ]] && ! $CHECK_ONLY && ! $NO_BOOT; then
  wait_for_pm "$serial" || true
fi
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
  if [[ -x "$ANDROID_CLI_BIN" ]]; then
    "$ANDROID_CLI_BIN" "${ANDROID_CLI[@]}" emulator stop "$AVD_NAME" >/dev/null 2>&1 \
      || "$ADB_BIN" -s "$serial" emu kill >/dev/null 2>&1 || true
  else
    "$ADB_BIN" -s "$serial" emu kill >/dev/null 2>&1 || true
  fi
fi

# --- step 8: summary ----------------------------------------------------------
log "done."
if $STOP_AFTER; then
  log "emulator stopped. Re-run without --stop to leave it warm for QA."
else
  log "emulator left running for QA. next steps:"
  log "  source .claude/scripts/lib/android-cli.sh && android_cli_ensure"
  log "  android run --apks <apk> --activity io.github.sceneview.demo/.MainActivity"
  log "  stop it with: android emulator stop $AVD_NAME"
fi
