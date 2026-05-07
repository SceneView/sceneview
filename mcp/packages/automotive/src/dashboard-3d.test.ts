import { describe, it, expect } from "vitest";
import {
  generateDashboard3d,
  GAUGE_TYPES,
  DASHBOARD_THEMES,
} from "./dashboard-3d.js";

describe("GAUGE_TYPES", () => {
  it("has at least 6 gauge types", () => {
    expect(GAUGE_TYPES.length).toBeGreaterThanOrEqual(6);
  });

  it("includes core gauges", () => {
    expect(GAUGE_TYPES).toContain("speedometer");
    expect(GAUGE_TYPES).toContain("tachometer");
    expect(GAUGE_TYPES).toContain("fuel");
    expect(GAUGE_TYPES).toContain("temperature");
  });
});

describe("DASHBOARD_THEMES", () => {
  it("has at least 4 themes", () => {
    expect(DASHBOARD_THEMES.length).toBeGreaterThanOrEqual(4);
  });

  it("includes core themes", () => {
    expect(DASHBOARD_THEMES).toContain("classic");
    expect(DASHBOARD_THEMES).toContain("digital");
    expect(DASHBOARD_THEMES).toContain("sport");
    expect(DASHBOARD_THEMES).toContain("electric");
  });
});

describe("generateDashboard3d", () => {
  it("generates valid Kotlin code with speedometer", () => {
    const code = generateDashboard3d({ gauges: ["speedometer"] });
    expect(code).toContain("package com.example.automotive.dashboard");
    expect(code).toContain("import io.github.sceneview.SceneView");
    expect(code).toContain("@Composable");
    expect(code).toContain("rememberEngine()");
    expect(code).toContain("rememberModelInstance");
  });

  it("uses correct dashboard model path", () => {
    const code = generateDashboard3d({ gauges: ["speedometer"], theme: "sport" });
    expect(code).toContain("models/dashboard/sport_cluster.glb");
  });

  it("includes speedometer gauge", () => {
    const code = generateDashboard3d({ gauges: ["speedometer"] });
    expect(code).toContain("speed");
    expect(code).toContain("SPEED");
    expect(code).toContain("km/h");
  });

  it("includes tachometer gauge", () => {
    const code = generateDashboard3d({ gauges: ["tachometer"] });
    expect(code).toContain("rpm");
    expect(code).toContain("RPM");
  });

  it("includes fuel gauge state", () => {
    const code = generateDashboard3d({ gauges: ["fuel"] });
    expect(code).toContain("fuelLevel");
  });

  it("includes temperature gauge state", () => {
    const code = generateDashboard3d({ gauges: ["temperature"] });
    expect(code).toContain("coolantTemp");
  });

  it("includes oil pressure state", () => {
    const code = generateDashboard3d({ gauges: ["oil-pressure"] });
    expect(code).toContain("oilPressure");
  });

  it("includes battery state", () => {
    const code = generateDashboard3d({ gauges: ["battery"] });
    expect(code).toContain("batteryVoltage");
  });

  it("includes boost gauge state", () => {
    const code = generateDashboard3d({ gauges: ["boost"] });
    expect(code).toContain("boostPressure");
  });

  it("includes odometer state", () => {
    const code = generateDashboard3d({ gauges: ["odometer"] });
    expect(code).toContain("odometer");
  });

  it("includes animation when animated=true", () => {
    const code = generateDashboard3d({ gauges: ["speedometer"], animated: true });
    expect(code).toContain("animateFloatAsState");
    expect(code).toContain("spring");
  });

  it("excludes animation when animated=false", () => {
    const code = generateDashboard3d({ gauges: ["speedometer"], animated: false });
    expect(code).not.toContain("animateFloatAsState");
  });

  it("includes interactive controls when interactive=true", () => {
    const code = generateDashboard3d({ gauges: ["speedometer"], interactive: true });
    expect(code).toContain("DashboardControls");
    expect(code).toContain("Slider");
  });

  it("excludes interactive controls when interactive=false", () => {
    const code = generateDashboard3d({ gauges: ["speedometer"], interactive: false });
    expect(code).not.toContain("DashboardControls");
  });

  it("generates AR code when ar=true", () => {
    const code = generateDashboard3d({ gauges: ["speedometer"], ar: true });
    expect(code).toContain("ARSceneView(");
    expect(code).toContain("android.permission.CAMERA");
    expect(code).toContain("arsceneview:4.0.9");
  });

  it("includes LightNode with named apply parameter", () => {
    const code = generateDashboard3d({ gauges: ["speedometer"] });
    expect(code).toContain("LightNode(");
    expect(code).toContain("apply = {");
    expect(code).toContain("intensity(");
  });

  it("includes loading indicator", () => {
    const code = generateDashboard3d({ gauges: ["speedometer"] });
    expect(code).toContain("CircularProgressIndicator");
    expect(code).toContain("dashboardModel == null");
  });

  it("handles null model instance", () => {
    const code = generateDashboard3d({ gauges: ["speedometer"] });
    expect(code).toContain("dashboardModel?.let");
  });

  it("includes GaugeView with Canvas rendering", () => {
    const code = generateDashboard3d({ gauges: ["speedometer"] });
    expect(code).toContain("GaugeView");
    expect(code).toContain("Canvas");
    expect(code).toContain("drawArc");
  });

  it("supports red zone indicator", () => {
    const code = generateDashboard3d({ gauges: ["tachometer"] });
    expect(code).toContain("redZone");
  });

  it("uses ViewNode for gauge rendering", () => {
    const code = generateDashboard3d({ gauges: ["speedometer"] });
    expect(code).toContain("ViewNode");
    expect(code).toContain("Position(");
  });

  it("generates code for every dashboard theme", () => {
    for (const theme of DASHBOARD_THEMES) {
      const code = generateDashboard3d({ gauges: ["speedometer"], theme });
      expect(code).toContain("@Composable");
    }
  });
});
