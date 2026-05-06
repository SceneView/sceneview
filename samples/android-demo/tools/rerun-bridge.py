"""Rerun sidecar for SceneView's "AR Debug (Rerun)" sample demo.

Listens on TCP :9876 for the JSON-lines wire format emitted by the SceneView
Android RerunBridge (arsceneview.rerun.RerunBridge) and re-logs each event
into the Rerun viewer as the matching archetype:

  camera_pose  -> rr.Transform3D
  plane        -> rr.LineStrips3D  (closed world-space polygon)
  point_cloud  -> rr.Points3D
  anchor       -> rr.Transform3D
  hit_result   -> rr.Points3D      (single highlighted point)

By default the Rerun viewer is spawned automatically (live mode). Pass
`--save` to instead write a sharable .rrd recording to disk — perfect for
sending to a colleague or attaching to a bug report.

Setup on your dev machine:
    pip install rerun-sdk numpy
    python samples/android-demo/tools/rerun-bridge.py             # live viewer
    python samples/android-demo/tools/rerun-bridge.py --save      # save to ~/.sceneview/recordings/
    python samples/android-demo/tools/rerun-bridge.py --save out.rrd

Then on your Android device (connected via USB with adb):
    adb reverse tcp:9876 tcp:9876
    # launch the SceneView demo app
    # open the Samples tab -> AR Debug (Rerun)

Inside the app, tap the "Save & Share" button to ask this sidecar to flush
the current recording and print the path + viewer URL. The sidecar prints:

    [rerun-bridge] saved 1234 events -> /home/you/.sceneview/recordings/2026-05-06_23-30-12.rrd
    [rerun-bridge] open in browser -> https://sceneview.github.io/rerun/?url=file:///home/you/...

Drop the .rrd onto a public host (R2, GitHub release, S3) and pass its URL
to https://sceneview.github.io/rerun/?url=<encoded-url> to share.

This script stays in lockstep with the generator in mcp/packages/rerun/src/
python-sidecar.ts — keep both updated together (the rerun-3d-mcp package
ships an equivalent for users who don't clone this repo).
"""
from __future__ import annotations

import argparse
import datetime as _dt
import json
import os
import socket
import sys
from pathlib import Path
from typing import Any
from urllib.parse import quote as _urlquote

import numpy as np
import rerun as rr

HOST = "0.0.0.0"
PORT = 9876
APPLICATION_ID = "sceneview-ar-debug"
DEFAULT_SAVE_DIR = Path.home() / ".sceneview" / "recordings"
SHARE_BASE_URL = "https://sceneview.github.io/rerun/"


def _quat(xyzw: list[float]) -> rr.Quaternion:
    return rr.Quaternion(xyzw=xyzw)


def handle_event(ev: dict[str, Any]) -> None:
    """Dispatch a single JSON event to the matching Rerun archetype."""
    t = int(ev.get("t", 0))
    rr.set_time_nanos("device_clock", t)
    kind = ev.get("type")
    entity = ev.get("entity", "world/unknown")

    if kind == "camera_pose":
        rr.log(
            entity,
            rr.Transform3D(
                translation=ev["translation"],
                rotation=_quat(ev["quaternion"]),
            ),
        )
    elif kind == "plane":
        poly = np.array(ev["polygon"], dtype=np.float32)
        if len(poly) >= 3:
            # Close the loop so the viewer draws a full outline.
            closed = np.vstack([poly, poly[:1]])
            rr.log(entity, rr.LineStrips3D([closed]))
    elif kind == "point_cloud":
        positions = np.array(ev["positions"], dtype=np.float32)
        if positions.size > 0:
            rr.log(entity, rr.Points3D(positions, radii=0.005))
    elif kind == "anchor":
        rr.log(
            entity,
            rr.Transform3D(
                translation=ev["translation"],
                rotation=_quat(ev["quaternion"]),
            ),
        )
    elif kind == "hit_result":
        rr.log(
            entity,
            rr.Points3D(
                np.array([ev["translation"]], dtype=np.float32),
                radii=0.015,
                colors=[(255, 200, 0)],
            ),
        )
    elif kind == "camera_trail":
        # Tier-S "wow" feature: a 3D polyline of every accumulated camera
        # position so far — visually shows how the operator moved their
        # phone over the session. App-side keeps the buffer bounded.
        poly = np.array(ev["positions"], dtype=np.float32)
        if len(poly) >= 2:
            rr.log(entity, rr.LineStrips3D([poly], colors=[(120, 200, 255)]))
    elif kind == "scalar":
        # Generic per-frame timeseries (tracking quality, feature point
        # count, frame latency). Rerun ≥ 0.31 exposes Scalars; older
        # versions used Scalar (singular) — fall back so the sidecar
        # tolerates a wider range of installed rerun-sdk versions.
        value = float(ev.get("value", 0.0))
        archetype = getattr(rr, "Scalars", None) or getattr(rr, "Scalar", None)
        if archetype is not None:
            rr.log(entity, archetype(value))
    else:
        print(f"[rerun-bridge] unknown event type: {kind}", file=sys.stderr)


def _resolve_save_path(arg: str | None) -> Path:
    """Return the path the .rrd will be written to."""
    if arg:
        p = Path(arg).expanduser().resolve()
        if p.is_dir():
            p = p / _timestamp_filename()
    else:
        DEFAULT_SAVE_DIR.mkdir(parents=True, exist_ok=True)
        p = DEFAULT_SAVE_DIR / _timestamp_filename()
    return p


def _timestamp_filename() -> str:
    return _dt.datetime.now().strftime("%Y-%m-%d_%H-%M-%S") + ".rrd"


def _viewer_url_for_local_file(path: Path) -> str:
    """Build the SceneView viewer URL pointing at a local file:// URL.

    Mostly useful as a copy-paste hint — most browsers refuse to fetch
    file:// from an HTTPS page, so the user typically uploads first. We
    print this anyway so the workflow is discoverable.
    """
    return SHARE_BASE_URL + "?url=" + _urlquote(path.as_uri(), safe="")


def _send_control_ack(conn: socket.socket, payload: dict[str, Any]) -> None:
    """Best-effort write of a JSON-lines control acknowledgment."""
    try:
        conn.sendall((json.dumps(payload) + "\n").encode("utf-8"))
    except Exception as exc:
        print(f"[rerun-bridge] ack send failed: {exc}", file=sys.stderr)


def _flush_and_save(save_path: Path) -> None:
    """Write the in-memory recording to disk."""
    save_path.parent.mkdir(parents=True, exist_ok=True)
    rr.save(str(save_path))


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        prog="rerun-bridge",
        description="Receive SceneView AR JSON-lines events and either spawn the Rerun viewer (live) or save to .rrd (shareable).",
    )
    g = p.add_mutually_exclusive_group()
    g.add_argument(
        "--save",
        nargs="?",
        const="",
        metavar="PATH",
        help="Write the recording to PATH (a file or directory) instead of spawning the viewer. "
             "Defaults to ~/.sceneview/recordings/<timestamp>.rrd. Triggered when the client "
             "disconnects, or on demand via the in-app 'Save & Share' button (control message).",
    )
    g.add_argument(
        "--spawn",
        action="store_true",
        default=False,
        help="Force spawning the live Rerun viewer (the default when --save is omitted).",
    )
    p.add_argument("--port", type=int, default=PORT, help=f"TCP port to listen on (default: {PORT}).")
    p.add_argument("--host", default=HOST, help=f"Host interface to bind (default: {HOST}).")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)

    save_mode = args.save is not None
    save_path: Path | None = _resolve_save_path(args.save) if save_mode else None

    rr.init(APPLICATION_ID, spawn=not save_mode)

    if save_mode:
        print(f"[rerun-bridge] save mode -> will write {save_path}", flush=True)
    else:
        print("[rerun-bridge] live mode -> Rerun viewer should open shortly", flush=True)

    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind((args.host, args.port))
    srv.listen(1)
    print(f"[rerun-bridge] listening on {args.host}:{args.port}", flush=True)
    print(f"[rerun-bridge] run 'adb reverse tcp:{args.port} tcp:{args.port}' on your Mac, then", flush=True)
    print("[rerun-bridge] launch the SceneView demo app -> Samples -> AR Debug (Rerun)", flush=True)

    try:
        while True:
            conn, addr = srv.accept()
            print(f"[rerun-bridge] client connected: {addr}", flush=True)
            buf = b""
            events = 0
            saved_for_this_client = False
            try:
                while True:
                    chunk = conn.recv(65536)
                    if not chunk:
                        break
                    buf += chunk
                    while b"\n" in buf:
                        line, buf = buf.split(b"\n", 1)
                        if not line.strip():
                            continue
                        try:
                            ev = json.loads(line.decode("utf-8"))
                        except Exception as exc:
                            print(f"[rerun-bridge] skip malformed: {exc}", file=sys.stderr)
                            continue

                        # Control messages let the app trigger save-and-share
                        # without disconnecting the socket.
                        if ev.get("type") == "control":
                            cmd = ev.get("cmd")
                            if cmd == "save_now" and save_mode and save_path is not None:
                                _flush_and_save(save_path)
                                saved_for_this_client = True
                                url = _viewer_url_for_local_file(save_path)
                                print(
                                    f"[rerun-bridge] saved {events} events -> {save_path}",
                                    flush=True,
                                )
                                print(f"[rerun-bridge] open in browser -> {url}", flush=True)
                                _send_control_ack(conn, {
                                    "type": "control",
                                    "ack": "saved",
                                    "path": str(save_path),
                                    "events": events,
                                    "viewerUrl": url,
                                })
                            elif cmd == "save_now":
                                # Sidecar in live mode: nothing to save.
                                _send_control_ack(conn, {
                                    "type": "control",
                                    "ack": "save_unsupported",
                                    "reason": "sidecar started in live mode; relaunch with --save",
                                })
                            else:
                                print(f"[rerun-bridge] unknown control cmd: {cmd}", file=sys.stderr)
                            continue

                        try:
                            handle_event(ev)
                            events += 1
                        except Exception as exc:
                            print(f"[rerun-bridge] event skip: {exc}", file=sys.stderr)
            finally:
                conn.close()
                # Auto-save on disconnect if no explicit save_now happened.
                if save_mode and not saved_for_this_client and events > 0 and save_path is not None:
                    _flush_and_save(save_path)
                    url = _viewer_url_for_local_file(save_path)
                    print(
                        f"[rerun-bridge] saved {events} events on disconnect -> {save_path}",
                        flush=True,
                    )
                    print(f"[rerun-bridge] open in browser -> {url}", flush=True)
                else:
                    print(
                        f"[rerun-bridge] client disconnected (logged {events} events)",
                        flush=True,
                    )
    except KeyboardInterrupt:
        print("\n[rerun-bridge] shutting down", flush=True)
    finally:
        srv.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
