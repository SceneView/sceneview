#!/usr/bin/env bash
#
# validate-release-artifact.sh — Pre-upload guard for the Play Store AAB/APK (#1301).
#
# `play-store.yml` hands the gradle-produced `.aab` straight to the Play Store
# upload step. A stale or wrong-variant artifact (wrong package, mismatched
# versionName, off-by-one versionCode) would only surface as a Play Console
# rejection — minutes of runner time after the build, and a polluted Edit.
#
# This script introspects the built artifact's manifest (via the
# `android_cli_describe` helper in .claude/scripts/lib/android-cli.sh) and
# asserts, *before* the upload step:
#
#   1. the artifact file exists,
#   2. `package` matches the expected applicationId,
#   3. `versionName` matches the expected value (the one gradle was told to
#      build with — i.e. play-store.yml's `Calculate version` output),
#   4. `versionCode` matches the expected value AND is a positive integer.
#
# Tooling note: the `android` CLI (the repo's QA toolchain standard) has no
# artifact-introspection mode in v0.7 — its `describe` subcommand analyzes a
# *project directory*, not a built `.apk`/`.aab`. The correct tool for reading
# a built artifact's manifest is `aapt2`, which ships in the Android SDK
# build-tools the release runner already provisions via `setup-gradle`.
# `android_cli_describe` therefore wraps `aapt2` (badging for `.apk`, xmltree of
# the protobuf base manifest for `.aab`).
#
# ⛔ A pre-upload *validation guard* must NEVER block a release because of its
# OWN missing tooling. So this script draws a hard line between two failure
# kinds:
#
#   * "the artifact is wrong"  → exit 1 (BLOCK the release — that is the point)
#   * "the validation tooling
#      is unavailable"          → exit 0 (WARN + SKIP — a release must not be
#                                 blocked because `aapt2` couldn't be located)
#
# The `android_cli_describe` helper signals these apart: it returns 2 when
# `aapt2` itself is missing, and 1 when the artifact genuinely can't be read.
#
# Usage:
#   validate-release-artifact.sh <artifact> <expected-package> \
#       <expected-version-name> <expected-version-code>
#
# Exit codes:
#   0 = artifact OK, OR validation skipped because tooling is unavailable
#   1 = genuine mismatch (wrong package / versionName / versionCode) or bad args

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/android-cli.sh
source "$SCRIPT_DIR/lib/android-cli.sh"

if [[ $# -ne 4 ]]; then
  echo "usage: $0 <artifact> <expected-package> <expected-version-name> <expected-version-code>" >&2
  exit 1
fi

ARTIFACT="$1"
EXPECT_PKG="$2"
EXPECT_NAME="$3"
EXPECT_CODE="$4"

fail() {
  # `::error::` so the message is surfaced as a GitHub Actions annotation.
  # Used ONLY for genuine artifact problems — these MUST block the release.
  echo "::error::$*" >&2
  exit 1
}

skip() {
  # `::warning::` so the message is surfaced as a GitHub Actions annotation
  # without failing the job. Used when the *validation tooling* is unavailable —
  # a guard must never block a release because of its own missing tooling.
  echo "::warning::$*" >&2
  echo "[validate-artifact] SKIPPED — validation tooling unavailable, not blocking the release." >&2
  exit 0
}

[[ -f "$ARTIFACT" ]] || fail "release artifact not found: $ARTIFACT"

echo "[validate-artifact] describing $ARTIFACT"
# `android_cli_describe` exit codes: 0 = OK, 1 = artifact unreadable,
# 2 = `aapt2` tooling unavailable. `set -e` would abort on any non-zero, so
# capture the status explicitly and branch: tooling failure → warn+skip,
# artifact failure → hard fail.
DESC=""
DESCRIBE_RC=0
DESC="$(android_cli_describe "$ARTIFACT")" || DESCRIBE_RC=$?
if [[ "$DESCRIBE_RC" -eq 2 ]]; then
  skip "AAB manifest validation skipped — 'aapt2' could not be located" \
       "(not on PATH, not under \$ANDROID_SDK_ROOT/build-tools). This is a" \
       "tooling gap, not an artifact problem; the upload proceeds unvalidated."
elif [[ "$DESCRIBE_RC" -ne 0 ]]; then
  fail "could not introspect $ARTIFACT — the artifact appears corrupt or is" \
       "an unsupported format. Refusing to upload an unverifiable release."
fi

echo "$DESC"

# Parse package / versionName / versionCode out of whichever `aapt2` format the
# describing helper emitted. Two formats are possible:
#
#   `aapt2 dump badging` (.apk):
#     package: name='io.github.sceneview.demo' versionCode='4321' versionName='4.5.0' ...
#
#   `aapt2 dump xmltree` (.aab base manifest, protobuf):
#     E: manifest (line=…)
#       A: package="io.github.sceneview.demo" (Raw: "io.github.sceneview.demo")
#       A: http://schemas.android.com/apk/res/android:versionCode(0x0101021b)=(type 0x10)0x10e1
#       A: http://schemas.android.com/apk/res/android:versionName(0x0101021c)="4.5.0" (Raw: …)
#
# The xmltree versionCode is a hex int literal — parsed and normalised to
# decimal so it compares cleanly with the decimal value gradle was given.
read -r GOT_PKG GOT_NAME GOT_CODE < <(python3 - "$DESC" <<'PYEOF'
import re, sys
desc = sys.argv[1]

def first(*patterns):
    for pat in patterns:
        m = re.search(pat, desc)
        if m:
            return m.group(1)
    return ""

pkg = first(r"name='([^']+)'", r"package=\"([^\"]+)\"", r"package[ :=]+([A-Za-z0-9._]+)")
name = first(r"versionName='([^']+)'", r"versionName[^=]*=\"([^\"]+)\"",
             r"versionName[ :=]+([A-Za-z0-9._\-]+)")
code_raw = first(r"versionCode='([^']+)'", r"versionCode[^=]*=\(type[^)]*\)(0x[0-9a-fA-F]+)",
                 r"versionCode[^=]*=([0-9]+)", r"versionCode[ :=]+([0-9]+)")
code = ""
if code_raw:
    try:
        code = str(int(code_raw, 0))  # base 0 → handles "0x10e1" and "4321"
    except ValueError:
        code = ""

print(pkg or "-", name or "-", code or "-")
PYEOF
)
# Normalise the python "-" sentinels back to empty strings.
[[ "$GOT_PKG"  == "-" ]] && GOT_PKG=""
[[ "$GOT_NAME" == "-" ]] && GOT_NAME=""
[[ "$GOT_CODE" == "-" ]] && GOT_CODE=""

echo "[validate-artifact] parsed: package=$GOT_PKG versionName=$GOT_NAME versionCode=$GOT_CODE"
echo "[validate-artifact] expect: package=$EXPECT_PKG versionName=$EXPECT_NAME versionCode=$EXPECT_CODE"

[[ -n "$GOT_PKG"  ]] || fail "could not parse 'package' from artifact manifest"
[[ -n "$GOT_NAME" ]] || fail "could not parse 'versionName' from artifact manifest"
[[ -n "$GOT_CODE" ]] || fail "could not parse 'versionCode' from artifact manifest"

[[ "$GOT_PKG" == "$EXPECT_PKG" ]] \
  || fail "package mismatch — built '$GOT_PKG', expected '$EXPECT_PKG'. Wrong variant or applicationId?"
[[ "$GOT_NAME" == "$EXPECT_NAME" ]] \
  || fail "versionName mismatch — built '$GOT_NAME', expected '$EXPECT_NAME'. Stale artifact?"
[[ "$GOT_CODE" == "$EXPECT_CODE" ]] \
  || fail "versionCode mismatch — built '$GOT_CODE', expected '$EXPECT_CODE'. Stale artifact?"
[[ "$GOT_CODE" =~ ^[0-9]+$ ]] && [[ "$GOT_CODE" -gt 0 ]] \
  || fail "versionCode '$GOT_CODE' is not a positive integer"

echo "[validate-artifact] OK — $ARTIFACT is $GOT_PKG $GOT_NAME ($GOT_CODE)"
