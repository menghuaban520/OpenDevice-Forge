export type FactSource = "measured" | "public_reference" | "demo" | "unknown";

export interface DeviceFact<T> {
  value: T | null;
  source: FactSource;
}

export type DeviceConnection =
  | "ready"
  | "unauthorized"
  | "offline"
  | "adb_missing"
  | "not_connected"
  | "demo";

export interface DeviceSnapshot {
  sessionId: string;
  mode: "live" | "demo";
  connection: DeviceConnection;
  capturedAt: string;
  manufacturer: DeviceFact<string>;
  productName: DeviceFact<string>;
  model: DeviceFact<string>;
  androidVersion: DeviceFact<string>;
  abi: DeviceFact<string>;
  ramBytes: DeviceFact<number>;
  storageAvailableBytes: DeviceFact<number>;
  batteryPercent: DeviceFact<number>;
  rootSignals: DeviceFact<string[]>;
}

export type ReadinessStatus = "ready" | "limited" | "not_ready" | "unknown";

export interface ReadinessCheck {
  id: "architecture" | "memory" | "storage" | "android";
  label: string;
  status: ReadinessStatus;
  detail: string;
  source: FactSource;
}

export interface ReadinessAssessment {
  verdict: ReadinessStatus;
  evidenceQuality: "measured" | "reference_only" | "mixed" | "demo" | "insufficient";
  demo: boolean;
  summary: string;
  blocker?: "adb_missing" | "adb_unauthorized" | "device_offline" | "device_not_connected";
  checks: ReadinessCheck[];
}

export interface DeviceReport {
  schemaVersion: "1";
  generatedAt: string;
  mode: "live" | "demo";
  safety: {
    access: "只读";
    excludedActions: string[];
  };
  device: {
    connection: DeviceConnection;
    manufacturer: DeviceFact<string>;
    productName: DeviceFact<string>;
    model: DeviceFact<string>;
    androidVersion: DeviceFact<string>;
    abi: DeviceFact<string>;
    ramBytes: DeviceFact<number>;
    storageAvailableBytes: DeviceFact<number>;
    batteryPercent: DeviceFact<number>;
    rootSignals: DeviceFact<string[]>;
  };
  readiness: ReadinessAssessment;
}

