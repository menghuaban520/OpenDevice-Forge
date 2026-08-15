import type { DeviceSnapshot } from "./types";

export const DEMO_NOVA7: DeviceSnapshot = {
  sessionId: "demo-nova7",
  mode: "demo",
  connection: "demo",
  capturedAt: "2026-08-16T00:00:00.000Z",
  manufacturer: { value: "HUAWEI", source: "public_reference" },
  productName: { value: "HUAWEI nova 7 SE 5G 乐活版", source: "public_reference" },
  model: { value: "CDL-AN50", source: "public_reference" },
  androidVersion: { value: "10（演示）", source: "demo" },
  abi: { value: "arm64-v8a", source: "demo" },
  ramBytes: { value: 8 * 1024 ** 3, source: "public_reference" },
  storageAvailableBytes: { value: Math.round(18.6 * 1024 ** 3), source: "demo" },
  batteryPercent: { value: 78, source: "demo" },
  rootSignals: { value: [], source: "demo" },
};

