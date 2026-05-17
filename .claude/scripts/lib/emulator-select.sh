#!/usr/bin/env bash
# emulator-select.sh — RAM-aware single-emulator selection for the device-QA harness.
#
# Why this exists (#1647):
#   The autonomous device-QA harness boots an Android emulator for its android
#   leg. When several Claude Code sessions / parallel agents run device-QA on the
#   same RAM-constrained Mac, each tries to boot its own emulator → resource
#   contention, boot failures, cross-session conflicts.
#
#   ⛔ This is NOT a multi-emulator pool. The harness keeps ONE shared emulator
#   and selects/reuses it intelligently:
#     1. RAM monitoring  — refuse to boot a fresh emulator when host RAM is tight.
#     2. Reuse           — if a suitable emulator is already up, reuse it.
#     3. Right-size      — scale the `-memory` flag to current RAM headroom.
#     4. Advisory lock   — two concurrent sessions cooperate on ONE emulator.
#
# Usage from another script:
#     source "$(dirname "$0")/lib/emulator-select.sh"
#     emu_running_serial                 # echoes serial of a booted emulator, or ""
#     emu_host_free_ram_mb               # echoes available host RAM in MB
#     emu_ram_allows_boot                # 0 if RAM allows a fresh boot, else 1
#     emu_recommended_memory_mb          # echoes a RAM-scaled `-memory` value
#     emu_lock_acquire / emu_lock_release  # advisory single-emulator lock

# This lib is sourced, not executed — do NOT `set -e` here (it would leak into
# the caller). Helpers report via return codes / stdout.

# --- Tunables ---------------------------------------------------------------
# Minimum host RAM (MB) that must be FREE before we will boot a fresh emulator.
# Below this, booting into a RAM-starved host is what causes the boot failures
# (#1647) — so we refuse and tell the caller to reuse / free RAM instead.
# Overridable via env for unusual hosts.
EMU_MIN_FREE_RAM_MB="${EMU_MIN_FREE_RAM_MB:-3072}"

# Emulator `-memory` floor / ceiling. The AVD config.ini pins hw.ramSize=4096,
# but on a tight host we pass a smaller `-memory` at boot time to avoid OOM.
# Floor: below this the emulator is too slow / unstable to QA with.
# Ceiling: never give the guest more than this even on a roomy host.
EMU_MEMORY_FLOOR_MB="${EMU_MEMORY_FLOOR_MB:-2048}"
EMU_MEMORY_CEILING_MB="${EMU_MEMORY_CEILING_MB:-4096}"
# Headroom (MB) the host keeps for itself — guest memory is capped so that
# host_free - guest_memory stays above this.
EMU_HOST_HEADROOM_MB="${EMU_HOST_HEADROOM_MB:-2048}"

# Advisory lock — a directory (mkdir is atomic) shared by all sessions on this
# host. The first session to mkdir it owns the boot; others detect the running
# emulator and reuse it.
EMU_LOCK_DIR="${EMU_LOCK_DIR:-${TMPDIR:-/tmp}/sceneview-device-qa-emulator.lock}"

_emu_log() { echo "[emu-select] $*" >&2; }

# --- RAM monitoring ---------------------------------------------------------
# emu_host_total_ram_mb — total physical RAM in MB.
emu_host_total_ram_mb() {
  case "$(uname -s)" in
    Darwin)
      local bytes
      bytes="$(sysctl -n hw.memsize 2>/dev/null || echo 0)"
      echo $(( bytes / 1024 / 1024 ))
      ;;
    Linux)
      awk '/^MemTotal:/ { printf "%d", $2 / 1024 }' /proc/meminfo 2>/dev/null || echo 0
      ;;
    *) echo 0 ;;
  esac
}

# emu_host_free_ram_mb — *available* RAM in MB (free + reclaimable).
# macOS: vm_stat reports pages — available ≈ (free + inactive) * page_size.
#        inactive pages are reclaimable, so counting them matches what the OS
#        will actually hand a new process.
# Linux: MemAvailable already accounts for reclaimable cache.
emu_host_free_ram_mb() {
  case "$(uname -s)" in
    Darwin)
      vm_stat 2>/dev/null | awk '
        /page size of/      { for (i=1;i<=NF;i++) if ($i ~ /^[0-9]+$/) ps=$i }
        /Pages free/        { gsub(/\./,"",$NF); free=$NF }
        /Pages inactive/    { gsub(/\./,"",$NF); inactive=$NF }
        END {
          if (ps == "") ps = 4096
          printf "%d", (free + inactive) * ps / 1024 / 1024
        }'
      ;;
    Linux)
      awk '/^MemAvailable:/ { printf "%d", $2 / 1024 }' /proc/meminfo 2>/dev/null || echo 0
      ;;
    *) echo 0 ;;
  esac
}

# emu_ram_allows_boot — return 0 if there is enough free RAM to safely boot a
# fresh emulator, 1 otherwise. Logs a clear message either way.
emu_ram_allows_boot() {
  local free; free="$(emu_host_free_ram_mb)"
  if [[ "$free" -le 0 ]]; then
    # Could not measure (unknown OS) — don't block the harness, but say so.
    _emu_log "could not measure host RAM — proceeding without a RAM gate"
    return 0
  fi
  if [[ "$free" -lt "$EMU_MIN_FREE_RAM_MB" ]]; then
    _emu_log "host free RAM ${free} MB is below the ${EMU_MIN_FREE_RAM_MB} MB boot threshold"
    _emu_log "refusing to boot a fresh emulator — reuse a running one or free RAM first"
    return 1
  fi
  _emu_log "host free RAM ${free} MB — OK to boot (threshold ${EMU_MIN_FREE_RAM_MB} MB)"
  return 0
}

# emu_recommended_memory_mb — echo a RAM-scaled `-memory` value (MB) for a
# fresh boot. Scales to current headroom, clamped to [floor, ceiling].
emu_recommended_memory_mb() {
  local free mem
  free="$(emu_host_free_ram_mb)"
  if [[ "$free" -le 0 ]]; then
    # Unknown host — use the floor, the safe choice.
    echo "$EMU_MEMORY_FLOOR_MB"
    return 0
  fi
  # Leave the host its headroom; give the rest (capped at the ceiling) to guest.
  mem=$(( free - EMU_HOST_HEADROOM_MB ))
  [[ "$mem" -gt "$EMU_MEMORY_CEILING_MB" ]] && mem="$EMU_MEMORY_CEILING_MB"
  [[ "$mem" -lt "$EMU_MEMORY_FLOOR_MB" ]]   && mem="$EMU_MEMORY_FLOOR_MB"
  echo "$mem"
}

# --- Reuse detection --------------------------------------------------------
# emu_running_serial [adb_bin] — echo the serial of an already-booted emulator
# (state "device"), or nothing. Empty stdout + return 1 means none is up.
# We deliberately keep ONE emulator: the FIRST booted emulator-* device wins.
emu_running_serial() {
  local adb_bin="${1:-adb}"
  local serial
  serial="$("$adb_bin" devices 2>/dev/null \
    | awk '/^emulator-[0-9]+/ && $2=="device" { print $1; exit }')"
  if [[ -n "$serial" ]]; then
    echo "$serial"
    return 0
  fi
  return 1
}

# emu_count_running [adb_bin] — number of emulator-* devices in "device" state.
emu_count_running() {
  local adb_bin="${1:-adb}"
  "$adb_bin" devices 2>/dev/null \
    | awk '/^emulator-[0-9]+/ && $2=="device" { n++ } END { print n+0 }'
}

# --- Advisory single-emulator lock -----------------------------------------
# mkdir-based lock: mkdir is atomic on every POSIX filesystem, so exactly one
# of N racing sessions creates the directory and "owns" the emulator boot.
# A `pid` file inside records the owner so a stale lock (owner crashed, no
# emulator alive) can be reclaimed instead of deadlocking.

# _emu_lock_is_stale — 0 if the lock dir exists but is stale (no live owner
# process AND no emulator running), 1 otherwise.
_emu_lock_is_stale() {
  [[ -d "$EMU_LOCK_DIR" ]] || return 1
  local owner=""
  [[ -f "$EMU_LOCK_DIR/pid" ]] && owner="$(cat "$EMU_LOCK_DIR/pid" 2>/dev/null)"
  # Owner process still alive → not stale.
  if [[ -n "$owner" ]] && kill -0 "$owner" 2>/dev/null; then
    return 1
  fi
  # Owner gone, but if an emulator is actually running the lock still reflects
  # reality (someone reused it) — keep it. Only stale when nothing is alive.
  if emu_running_serial >/dev/null 2>&1; then
    return 1
  fi
  return 0
}

# emu_lock_acquire [timeout_sec] — acquire the advisory lock.
#   return 0 → this caller now OWNS the lock (it should boot/select).
#   return 0 is also returned to a caller that finds an emulator already up
#            while waiting — see emu_select_or_boot, which checks reuse first.
#   return 1 → timed out waiting for the lock.
emu_lock_acquire() {
  local timeout="${1:-300}"
  local waited=0
  while true; do
    if mkdir "$EMU_LOCK_DIR" 2>/dev/null; then
      echo "$$" > "$EMU_LOCK_DIR/pid"
      _emu_log "acquired emulator lock ($EMU_LOCK_DIR)"
      return 0
    fi
    # Lock held — reclaim it if stale.
    if _emu_lock_is_stale; then
      _emu_log "reclaiming stale emulator lock (owner gone, no emulator alive)"
      rm -rf "$EMU_LOCK_DIR" 2>/dev/null || true
      continue
    fi
    # A peer holds the lock and is booting/using the emulator — a caller that
    # only wants to reuse should poll emu_running_serial; here we just wait.
    if [[ "$waited" -ge "$timeout" ]]; then
      _emu_log "timed out after ${timeout}s waiting for the emulator lock"
      return 1
    fi
    sleep 3
    waited=$(( waited + 3 ))
  done
}

# emu_lock_release — release the lock IF this process owns it.
emu_lock_release() {
  [[ -d "$EMU_LOCK_DIR" ]] || return 0
  local owner=""
  [[ -f "$EMU_LOCK_DIR/pid" ]] && owner="$(cat "$EMU_LOCK_DIR/pid" 2>/dev/null)"
  if [[ "$owner" == "$$" ]]; then
    rm -rf "$EMU_LOCK_DIR" 2>/dev/null || true
    _emu_log "released emulator lock"
  fi
}
