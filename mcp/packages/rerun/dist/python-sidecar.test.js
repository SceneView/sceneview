import { describe, it, expect } from "vitest";
import { generatePythonSidecar } from "./python-sidecar.js";
describe("generatePythonSidecar", () => {
    it("emits a script that uses rerun-sdk", () => {
        const src = generatePythonSidecar();
        expect(src).toContain("import rerun as rr");
        expect(src).toContain("rr.init(");
        expect(src).toContain("rr.Transform3D");
        expect(src).toContain("rr.Points3D");
        expect(src).toContain("rr.LineStrips3D");
    });
    it("defaults to port 9876", () => {
        const src = generatePythonSidecar();
        expect(src).toContain("PORT = 9876");
    });
    it("honours a custom port", () => {
        const src = generatePythonSidecar({ port: 12345 });
        expect(src).toContain("PORT = 12345");
    });
    it("rejects out-of-range ports", () => {
        expect(() => generatePythonSidecar({ port: 0 })).toThrow(/invalid port/i);
        expect(() => generatePythonSidecar({ port: 70000 })).toThrow(/invalid port/i);
    });
    it("defaults to spawning the viewer", () => {
        const src = generatePythonSidecar();
        expect(src).toContain("DEFAULT_SPAWN = True");
    });
    it("disables default spawn when spawnViewer=false", () => {
        const src = generatePythonSidecar({ spawnViewer: false });
        expect(src).toContain("DEFAULT_SPAWN = False");
        expect(src).not.toContain("DEFAULT_SPAWN = True");
    });
    it("supports save mode + control protocol", () => {
        const src = generatePythonSidecar();
        expect(src).toContain("--save");
        expect(src).toContain('cmd == "save_now"');
        expect(src).toContain("rr.save(");
        expect(src).toContain('"ack": "saved"');
    });
    it("includes share base URL", () => {
        const src = generatePythonSidecar();
        expect(src).toContain('SHARE_BASE_URL = "https://sceneview.github.io/rerun/"');
    });
    it("honours a custom share base URL", () => {
        const src = generatePythonSidecar({ shareBaseUrl: "https://example.dev/v/" });
        expect(src).toContain('SHARE_BASE_URL = "https://example.dev/v/"');
    });
    it("defaults application id to sceneview-bridge", () => {
        const src = generatePythonSidecar();
        expect(src).toContain('APPLICATION_ID = "sceneview-bridge"');
    });
    it("honours a custom recordingName", () => {
        const src = generatePythonSidecar({ recordingName: "robot-lab" });
        expect(src).toContain('APPLICATION_ID = "robot-lab"');
    });
    it("rejects an empty recordingName", () => {
        expect(() => generatePythonSidecar({ recordingName: "  " })).toThrow(/cannot be empty/i);
    });
    it("dispatches all 5 event types", () => {
        const src = generatePythonSidecar();
        expect(src).toContain('kind == "camera_pose"');
        expect(src).toContain('kind == "plane"');
        expect(src).toContain('kind == "point_cloud"');
        expect(src).toContain('kind == "anchor"');
        expect(src).toContain('kind == "hit_result"');
    });
});
