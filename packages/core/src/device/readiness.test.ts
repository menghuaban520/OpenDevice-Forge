import { describe, expect, it } from "vitest";
import { DEMO_NOVA7 } from "./demo";
import { assessReadiness, mergeDeviceSnapshots } from "./readiness";
import type { DeviceSnapshot } from "./types";

const measuredDevice = (overrides: Partial<DeviceSnapshot> = {}): DeviceSnapshot => ({
  sessionId: "device-session-1",
  mode: "live",
  connection: "ready",
  capturedAt: "2026-08-16T00:00:00.000Z",
  manufacturer: { value: "Example", source: "measured" },
  productName: { value: "Example Phone", source: "measured" },
  model: { value: "EX-1", source: "measured" },
  androidVersion: { value: "12", source: "measured" },
  abi: { value: "arm64-v8a", source: "measured" },
  ramBytes: { value: 8 * 1024 ** 3, source: "measured" },
  storageAvailableBytes: { value: 12 * 1024 ** 3, source: "measured" },
  batteryPercent: { value: 76, source: "measured" },
  rootSignals: { value: [], source: "measured" },
  ...overrides,
});

describe("assessReadiness", () => {
  it.each([
    [8, "ready"],
    [4, "limited"],
    [3, "not_ready"],
  ] as const)("classifies %s GiB RAM as %s", (gib, status) => {
    const result = assessReadiness(
      measuredDevice({ ramBytes: { value: gib * 1024 ** 3, source: "measured" } }),
    );
    expect(result.checks.find((check) => check.id === "memory")?.status).toBe(status);
  });

  it.each([
    ["arm64-v8a", "ready"],
    ["armeabi-v7a", "limited"],
    ["x86", "not_ready"],
    [null, "unknown"],
  ] as const)("classifies ABI %s as %s", (abi, status) => {
    const result = assessReadiness(
      measuredDevice({ abi: { value: abi, source: abi === null ? "unknown" : "measured" } }),
    );
    expect(result.checks.find((check) => check.id === "architecture")?.status).toBe(status);
  });

  it.each([
    [8, "ready"],
    [4, "limited"],
    [2, "not_ready"],
  ] as const)("classifies %s GiB free storage as %s", (gib, status) => {
    const result = assessReadiness(
      measuredDevice({
        storageAvailableBytes: { value: gib * 1024 ** 3, source: "measured" },
      }),
    );
    expect(result.checks.find((check) => check.id === "storage")?.status).toBe(status);
  });

  it("keeps missing Huawei fields indeterminate instead of inventing compatibility", () => {
    const result = assessReadiness(
      measuredDevice({
        manufacturer: { value: "HUAWEI", source: "measured" },
        model: { value: "CDL-AN50", source: "public_reference" },
        abi: { value: null, source: "unknown" },
        ramBytes: { value: null, source: "unknown" },
      }),
    );

    expect(result.verdict).toBe("unknown");
    expect(result.evidenceQuality).toBe("mixed");
    expect(result.summary).toContain("仍需真机读取");
  });

  it("labels demo readiness and never presents it as measured evidence", () => {
    const result = assessReadiness(DEMO_NOVA7);
    expect(result.evidenceQuality).toBe("demo");
    expect(result.demo).toBe(true);
    expect(result.summary).toContain("演示");
  });

  it("lets measured fields override public references while preserving source labels", () => {
    const merged = mergeDeviceSnapshots(DEMO_NOVA7, {
      mode: "live",
      connection: "ready",
      capturedAt: "2026-08-16T00:05:00.000Z",
      ramBytes: { value: 7_845_888_000, source: "measured" },
      abi: { value: "arm64-v8a", source: "measured" },
    });

    expect(merged.ramBytes).toEqual({ value: 7_845_888_000, source: "measured" });
    expect(merged.model).toEqual({ value: "CDL-AN50", source: "public_reference" });
    expect(merged.mode).toBe("live");
  });

  it("blocks assessment when ADB authorization is missing", () => {
    const result = assessReadiness(
      measuredDevice({ connection: "unauthorized", abi: { value: null, source: "unknown" } }),
    );
    expect(result.verdict).toBe("unknown");
    expect(result.blocker).toBe("adb_unauthorized");
  });
});

