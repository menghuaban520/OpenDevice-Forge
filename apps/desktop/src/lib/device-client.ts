export interface AdbProbeResult {
  available: boolean;
  source: "bundled" | "path" | null;
}

export interface AdbDeviceSummary {
  sessionSerial: string;
  transport: "ready" | "unauthorized" | "offline";
  model: string | null;
  product: string | null;
}

export interface DeviceInspection {
  manufacturer: string | null;
  productName: string | null;
  model: string | null;
  androidVersion: string | null;
  abi: string | null;
  ramBytes: number | null;
  storageAvailableBytes: number | null;
  batteryPercent: number | null;
  rootSignals: string[];
}

export interface DeviceClient {
  probeAdb(): Promise<AdbProbeResult>;
  listDevices(): Promise<AdbDeviceSummary[]>;
  inspectDevice(sessionSerial: string): Promise<DeviceInspection>;
}

const browserClient: DeviceClient = {
  probeAdb: async () => ({ available: false, source: null }),
  listDevices: async () => [],
  inspectDevice: async () => {
    throw new Error("not_connected");
  },
};

const isTauri = (): boolean => "__TAURI_INTERNALS__" in window;

export const createDeviceClient = (): DeviceClient => {
  if (!isTauri()) return browserClient;
  return {
    probeAdb: async () => {
      const { invoke } = await import("@tauri-apps/api/core");
      return invoke<AdbProbeResult>("probe_adb");
    },
    listDevices: async () => {
      const { invoke } = await import("@tauri-apps/api/core");
      return invoke<AdbDeviceSummary[]>("list_devices");
    },
    inspectDevice: async (sessionSerial) => {
      const { invoke } = await import("@tauri-apps/api/core");
      return invoke<DeviceInspection>("inspect_device", { sessionSerial });
    },
  };
};

