import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  LayoutChangeEvent,
} from 'react-native';

// ---------------------------------------------------------------------------
// Double Pendulum — cross-platform physics demo (issue #1332)
// ---------------------------------------------------------------------------
//
// #1221 shipped this chaotic two-link pendulum on Android, iOS and Web. The
// React Native sample app is a *bridge-feature showcase* — the Fabric
// SceneView bridge has no per-frame transform-mutation API, so rather than
// adding substantial new bridge surface (tracked as a follow-up), this tab
// renders the pendulum with plain React Native `View`s.
//
// The simulation itself (`stepDoublePendulum` below) is a 1:1 TypeScript port
// of the shared, platform-independent `DoublePendulum` simulation in
// `sceneview-core` (`sceneview-core/.../physics/DoublePendulum.kt`) — same
// symplectic (semi-implicit) Euler integrator, same Lagrangian
// angular-acceleration equations, same `1/240 s` sub-stepping. The web demo
// mirrors the same math in JavaScript; keep the three in sync. Adapted from
// @radcli14's MIT-licensed `twolinks` (https://github.com/radcli14/twolinks).
//
// Matches the Android / iOS / web behavior: link-length and gravity sliders
// plus a reset button, real-time chaotic motion.

// Largest dt a single integration sub-step uses — mirrors
// `DoublePendulum.MAX_TIME_STEP`. A raw 60 Hz frame drifts visibly;
// `1/240 s` keeps symplectic Euler's energy band tight.
const MAX_TIME_STEP = 1 / 240;
const HALF_PI = Math.PI / 2;

interface PendulumState {
  pivotX: number;
  pivotY: number;
  length1: number;
  length2: number;
  mass1: number;
  mass2: number;
  angle1: number;
  angle2: number;
  omega1: number;
  omega2: number;
  gravity: number;
  damping: number;
}

/**
 * Build a fresh simulation. Both links start raised so the first frame
 * already shows dramatic motion — identical seeding to the Android
 * `DoublePendulumDemo` and the web demo.
 */
function seedState(length1: number, length2: number, gravity: number): PendulumState {
  return {
    pivotX: 0,
    pivotY: 0,
    length1,
    length2,
    mass1: 1,
    mass2: 1,
    angle1: HALF_PI,
    angle2: HALF_PI * 0.6,
    omega1: 0,
    omega2: 0,
    gravity,
    damping: 0.04,
  };
}

/**
 * One symplectic-Euler integration sub-step of fixed `dt`. Verbatim port of
 * `DoublePendulum.integrate` — standard point-mass double-pendulum Lagrangian
 * form (https://en.wikipedia.org/wiki/Double_pendulum).
 */
function integrate(s: PendulumState, dt: number): void {
  const { length1: l1, length2: l2, mass1: m1, mass2: m2, gravity: g } = s;
  const a1 = s.angle1;
  const a2 = s.angle2;
  const w1 = s.omega1;
  const w2 = s.omega2;

  const delta = a1 - a2;
  const cosD = Math.cos(delta);
  const sinD = Math.sin(delta);

  const den1 = l1 * (2 * m1 + m2 - m2 * Math.cos(2 * delta));
  const den2 = l2 * (2 * m1 + m2 - m2 * Math.cos(2 * delta));
  // Guard against the degenerate zero-length / zero-mass case.
  if (den1 === 0 || den2 === 0) return;

  const acc1 =
    (-g * (2 * m1 + m2) * Math.sin(a1) -
      m2 * g * Math.sin(a1 - 2 * a2) -
      2 * sinD * m2 * (w2 * w2 * l2 + w1 * w1 * l1 * cosD)) /
    den1;

  const acc2 =
    (2 *
      sinD *
      (w1 * w1 * l1 * (m1 + m2) +
        g * (m1 + m2) * Math.cos(a1) +
        w2 * w2 * l2 * m2 * cosD)) /
    den2;

  // Symplectic Euler: update velocities first, then advance the angles
  // from the *new* velocities.
  const dampFactor = Math.min(1, Math.max(0, 1 - s.damping * dt));
  s.omega1 = (w1 + acc1 * dt) * dampFactor;
  s.omega2 = (w2 + acc2 * dt) * dampFactor;
  s.angle1 = a1 + s.omega1 * dt;
  s.angle2 = a2 + s.omega2 * dt;
}

/**
 * Advance the simulation by `deltaSeconds`, sub-stepping at MAX_TIME_STEP
 * resolution — mirrors `DoublePendulum.step`.
 */
function stepDoublePendulum(s: PendulumState, deltaSeconds: number): void {
  if (deltaSeconds <= 0) return;
  let remaining = Math.min(deltaSeconds, 1); // never simulate >1s at once
  while (remaining > 0) {
    const dt = Math.min(remaining, MAX_TIME_STEP);
    integrate(s, dt);
    remaining -= dt;
  }
}

/** Joint position (tip of the upper link) in pivot-relative meters, +Y up. */
function jointOf(s: PendulumState): [number, number] {
  return [
    s.pivotX + s.length1 * Math.sin(s.angle1),
    s.pivotY - s.length1 * Math.cos(s.angle1),
  ];
}

/** Free tip of the lower link in pivot-relative meters, +Y up. */
function tipOf(s: PendulumState): [number, number] {
  const [jx, jy] = jointOf(s);
  return [jx + s.length2 * Math.sin(s.angle2), jy - s.length2 * Math.cos(s.angle2)];
}

// ---------------------------------------------------------------------------
// Minimal slider built from a draggable track (no extra native deps)
// ---------------------------------------------------------------------------

interface SliderProps {
  label: string;
  value: number;
  min: number;
  max: number;
  display: string;
  onChange: (value: number) => void;
}

function Slider({ label, value, min, max, display, onChange }: SliderProps) {
  const [trackWidth, setTrackWidth] = useState(1);
  const fraction = (value - min) / (max - min);

  const handleTouch = useCallback(
    (locationX: number) => {
      const clamped = Math.min(Math.max(locationX / trackWidth, 0), 1);
      onChange(min + clamped * (max - min));
    },
    [trackWidth, min, max, onChange]
  );

  return (
    <View style={sliderStyles.row}>
      <Text style={sliderStyles.label}>{label}</Text>
      <View
        style={sliderStyles.track}
        onLayout={(e: LayoutChangeEvent) => setTrackWidth(e.nativeEvent.layout.width)}
        onStartShouldSetResponder={() => true}
        onMoveShouldSetResponder={() => true}
        onResponderGrant={(e) => handleTouch(e.nativeEvent.locationX)}
        onResponderMove={(e) => handleTouch(e.nativeEvent.locationX)}
      >
        <View style={[sliderStyles.fill, { width: `${fraction * 100}%` }]} />
        <View style={[sliderStyles.thumb, { left: `${fraction * 100}%` }]} />
      </View>
      <Text style={sliderStyles.value}>{display}</Text>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Tab component
// ---------------------------------------------------------------------------

export function DoublePendulumTab() {
  const [length1, setLength1] = useState(0.5);
  const [length2, setLength2] = useState(0.5);
  const [gravity, setGravity] = useState(9.8);
  const [running, setRunning] = useState(true);

  // The simulation is mutated in place inside the rAF loop; a frame counter
  // forces React to re-render the link views each animation frame.
  const stateRef = useRef<PendulumState>(seedState(length1, length2, gravity));
  const [, setFrame] = useState(0);
  const trailRef = useRef<Array<[number, number]>>([]);
  const [canvas, setCanvas] = useState({ width: 1, height: 1 });

  // Re-seed whenever a slider changes (mirrors the web demo's behavior).
  useEffect(() => {
    stateRef.current = seedState(length1, length2, gravity);
    trailRef.current = [];
  }, [length1, length2, gravity]);

  // Animation loop — sub-stepped, frame-rate independent.
  useEffect(() => {
    if (!running) return;
    let rafId: number;
    let last = 0;
    const loop = (timestamp: number) => {
      const dt = last > 0 ? (timestamp - last) / 1000 : 0;
      last = timestamp;
      stepDoublePendulum(stateRef.current, dt);
      const tip = tipOf(stateRef.current);
      trailRef.current.push(tip);
      if (trailRef.current.length > 48) trailRef.current.shift();
      setFrame((f) => f + 1);
      rafId = requestAnimationFrame(loop);
    };
    rafId = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(rafId);
  }, [running]);

  const reset = useCallback(() => {
    stateRef.current = seedState(length1, length2, gravity);
    trailRef.current = [];
    setRunning(true);
  }, [length1, length2, gravity]);

  // -- Project the sim into canvas pixel space --
  const s = stateRef.current;
  const pivotPx: [number, number] = [canvas.width / 2, canvas.height * 0.32];
  const maxReach = s.length1 + s.length2;
  const scale = (Math.min(canvas.width, canvas.height) * 0.4) / Math.max(maxReach, 0.001);
  // +Y is up in the sim, down in canvas space → negate Y.
  const toCanvas = ([x, y]: [number, number]): [number, number] => [
    pivotPx[0] + x * scale,
    pivotPx[1] - y * scale,
  ];
  const jointPx = toCanvas(jointOf(s));
  const tipPx = toCanvas(tipOf(s));

  return (
    <View style={styles.tabContent}>
      {/* Live chaotic simulation */}
      <View
        style={styles.canvas}
        onLayout={(e: LayoutChangeEvent) =>
          setCanvas({
            width: e.nativeEvent.layout.width,
            height: e.nativeEvent.layout.height,
          })
        }
      >
        {/* Fading tip trail */}
        {trailRef.current.map((pt, i) => {
          const [cx, cy] = toCanvas(pt);
          const t = (i + 1) / trailRef.current.length;
          return (
            <View
              key={`trail_${i}`}
              style={[
                styles.trailDot,
                {
                  left: cx - 2,
                  top: cy - 2,
                  opacity: 0.35 * t,
                  transform: [{ scale: 0.5 + t }],
                },
              ]}
            />
          );
        })}

        <LinkView from={pivotPx} to={jointPx} thickness={6} />
        <LinkView from={jointPx} to={tipPx} thickness={5} />

        {/* Masses */}
        <View style={[styles.mass, { left: jointPx[0] - 9, top: jointPx[1] - 9 }]} />
        <View
          style={[
            styles.mass,
            styles.tipMass,
            { left: tipPx[0] - 11, top: tipPx[1] - 11 },
          ]}
        />
        {/* Fixed pivot marker */}
        <View style={[styles.pivot, { left: pivotPx[0] - 6, top: pivotPx[1] - 6 }]} />
      </View>

      {/* Controls */}
      <View style={styles.controls}>
        <Text style={styles.description}>
          A chaotic two-link pendulum. The integrator mirrors the shared
          DoublePendulum simulation in sceneview-core (KMP) that drives the
          Android, iOS and web demos.
        </Text>

        <Slider
          label="Upper link"
          value={length1}
          min={0.2}
          max={0.8}
          display={`${length1.toFixed(2)} m`}
          onChange={setLength1}
        />
        <Slider
          label="Lower link"
          value={length2}
          min={0.2}
          max={0.8}
          display={`${length2.toFixed(2)} m`}
          onChange={setLength2}
        />
        <Slider
          label="Gravity"
          value={gravity}
          min={1.6}
          max={20}
          display={`${gravity.toFixed(1)} m/s²`}
          onChange={setGravity}
        />

        <View style={styles.actionRow}>
          <TouchableOpacity
            style={styles.actionButton}
            onPress={reset}
            activeOpacity={0.7}
          >
            <Text style={styles.actionButtonText}>Reset</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.actionButton, styles.actionButtonSecondary]}
            onPress={() => setRunning((r) => !r)}
            activeOpacity={0.7}
          >
            <Text style={styles.actionButtonSecondaryText}>
              {running ? 'Pause' : 'Resume'}
            </Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
}

/**
 * A single pendulum link — an absolutely-positioned thin rectangle rotated to
 * connect `from` and `to` (canvas-space pixels).
 *
 * RN's `transform: rotateZ` rotates about the view *center*, so the rectangle
 * is laid out centered on the segment midpoint and then rotated — no
 * `transformOrigin` needed (keeps RN 0.73 compatibility).
 */
function LinkView({
  from,
  to,
  thickness,
}: {
  from: [number, number];
  to: [number, number];
  thickness: number;
}) {
  const dx = to[0] - from[0];
  const dy = to[1] - from[1];
  const length = Math.max(Math.hypot(dx, dy), 0.001);
  const angle = Math.atan2(dy, dx);
  const midX = (from[0] + to[0]) / 2;
  const midY = (from[1] + to[1]) / 2;
  return (
    <View
      style={{
        position: 'absolute',
        left: midX - length / 2,
        top: midY - thickness / 2,
        width: length,
        height: thickness,
        backgroundColor: '#1E88E5',
        borderRadius: thickness / 2,
        transform: [{ rotateZ: `${angle}rad` }],
      }}
    />
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  tabContent: {
    flex: 1,
  },
  canvas: {
    flex: 1,
    minHeight: 260,
    backgroundColor: '#11151C',
  },
  trailDot: {
    position: 'absolute',
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#1E88E5',
  },
  mass: {
    position: 'absolute',
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: '#1E88E5',
  },
  tipMass: {
    width: 22,
    height: 22,
    borderRadius: 11,
  },
  pivot: {
    position: 'absolute',
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#FFB300',
  },
  controls: {
    padding: 16,
    borderTopWidth: 1,
    borderTopColor: '#1E2430',
  },
  description: {
    color: '#9CA3AF',
    fontSize: 13,
    lineHeight: 19,
    marginBottom: 12,
  },
  actionRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 8,
  },
  actionButton: {
    flex: 1,
    backgroundColor: '#1E88E5',
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: 'center',
  },
  actionButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  actionButtonSecondary: {
    backgroundColor: '#1A1F28',
    borderWidth: 1,
    borderColor: '#2A3040',
  },
  actionButtonSecondaryText: {
    color: '#9CA3AF',
    fontSize: 15,
    fontWeight: '700',
  },
});

const sliderStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  label: {
    color: '#D1D5DB',
    fontSize: 14,
    width: 82,
  },
  track: {
    flex: 1,
    height: 28,
    justifyContent: 'center',
    marginHorizontal: 8,
  },
  fill: {
    position: 'absolute',
    height: 4,
    borderRadius: 2,
    backgroundColor: '#1E88E5',
  },
  thumb: {
    position: 'absolute',
    width: 18,
    height: 18,
    borderRadius: 9,
    marginLeft: -9,
    backgroundColor: '#FFFFFF',
    borderWidth: 2,
    borderColor: '#1E88E5',
  },
  value: {
    color: '#9CA3AF',
    fontSize: 12,
    width: 62,
    textAlign: 'right',
  },
});
