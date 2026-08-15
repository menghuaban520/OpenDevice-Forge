import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  applyPluginAction,
  assessReadiness,
  BUILTIN_PLUGINS,
  createDeviceReport,
  createPluginRegistry,
  toMarkdownReport,
  type DeviceSnapshot,
  type LayoutProfile,
  type PluginRegistryAction,
  type PluginRegistryState,
} from "@opendevice/core";
import { AppShell, type AppPage } from "./components/AppShell";
import { DeviceOverview } from "./features/overview/DeviceOverview";
import { createAcceptedLayoutProfiles, LayoutEditor } from "./features/plugins/LayoutEditor";
import { PluginMarket } from "./features/plugins/PluginMarket";
import {
  ALL_INSPECTION_GROUPS,
  createDeviceClient,
  type AdbDeviceSummary,
  type DeviceClient,
  type DeviceInspection,
  type UsbDeviceHint,
} from "./lib/device-client";
import "./styles.css";

interface AppProps {
  deviceClient?: DeviceClient;
}

const initialRegistry = () => createPluginRegistry(
  BUILTIN_PLUGINS.filter((manifest) => [
    "kernel.plugin-manager",
    "dev.opendevice.device-inspection",
    "dev.opendevice.ai-readiness",
    "dev.opendevice.remote-gateway",
  ].includes(manifest.id)),
);

const loadRegistry = (): PluginRegistryState => {
  try {
    const parsed = JSON.parse(window.localStorage.getItem("opendevice.registry.v1") ?? "null") as PluginRegistryState | null;
    if (parsed?.plugins?.["kernel.plugin-manager"]?.manifest.protected === true && Array.isArray(parsed.audit)) {
      return parsed;
    }
  } catch {
    // Invalid preferences are ignored so the protected kernel can recover.
  }
  return initialRegistry();
};

const loadProfiles = (): LayoutProfile[] => {
  try {
    const parsed = JSON.parse(window.localStorage.getItem("opendevice.layouts.v1") ?? "null") as LayoutProfile[] | null;
    if (Array.isArray(parsed) && parsed.some((profile) => profile.id === "default")) return parsed;
  } catch {
    // Invalid layout data falls back to the accepted profile set.
  }
  return createAcceptedLayoutProfiles();
};

const fact = <T,>(value: T | null) =>
  value === null ? { value: null, source: "unknown" as const } : { value, source: "measured" as const };

const emptySnapshot = (
  connection: DeviceSnapshot["connection"] = "not_connected",
  summary?: AdbDeviceSummary,
): DeviceSnapshot => ({
  sessionId: "device-session",
  mode: "live",
  connection,
  capturedAt: new Date().toISOString(),
  manufacturer: fact(null),
  productName: fact(summary?.product ?? null),
  model: fact(summary?.model ?? null),
  androidVersion: fact(null),
  abi: fact(null),
  ramBytes: fact(null),
  storageAvailableBytes: fact(null),
  batteryPercent: fact(null),
  rootSignals: fact<string[]>(null),
});

const snapshotFromInspection = (inspection: DeviceInspection): DeviceSnapshot => ({
  ...emptySnapshot("ready"),
  manufacturer: fact(inspection.manufacturer),
  productName: fact(inspection.productName),
  model: fact(inspection.model),
  androidVersion: fact(inspection.androidVersion),
  abi: fact(inspection.abi),
  ramBytes: fact(inspection.ramBytes),
  storageAvailableBytes: fact(inspection.storageAvailableBytes),
  batteryPercent: fact(inspection.batteryPercent),
  rootSignals: fact(inspection.rootSignals),
});

const snapshotFromUsbHint = (hint: UsbDeviceHint): DeviceSnapshot => ({
  ...emptySnapshot("not_connected"),
  manufacturer: fact(hint.manufacturer),
  model: fact(hint.product),
});

export function App({ deviceClient }: AppProps) {
  const client = useMemo(() => deviceClient ?? createDeviceClient(), [deviceClient]);
  const [page, setPage] = useState<AppPage>("overview");
  const [registry, setRegistry] = useState(loadRegistry);
  const [profiles, setProfiles] = useState<LayoutProfile[]>(loadProfiles);
  const [snapshot, setSnapshot] = useState<DeviceSnapshot>(() => emptySnapshot());
  const [devices, setDevices] = useState<AdbDeviceSummary[]>([]);
  const [selectedSerial, setSelectedSerial] = useState<string | null>(null);
  const [connectionMessage, setConnectionMessage] = useState("正在检测设备…");
  const refreshSequence = useRef(0);
  const readiness = useMemo(() => assessReadiness(snapshot), [snapshot]);

  const refreshDevice = useCallback(async (preferredSerial?: string) => {
    const sequence = ++refreshSequence.current;
    setConnectionMessage("正在检测设备…");
    try {
      const probe = await client.probeAdb();
      if (sequence !== refreshSequence.current) return;
      if (!probe.available) {
        setDevices([]);
        setSelectedSerial(null);
        setSnapshot(emptySnapshot("adb_missing"));
        setConnectionMessage("电脑尚未安装 ADB");
        return;
      }
      const discovered = await client.listDevices();
      if (sequence !== refreshSequence.current) return;
      setDevices(discovered);
      if (discovered.length === 0) {
        setSelectedSerial(null);
        const usbHint = await client.probeUsbDevice?.();
        if (sequence !== refreshSequence.current) return;
        if (usbHint && (usbHint.manufacturer || usbHint.product)) {
          setSnapshot(snapshotFromUsbHint(usbHint));
          setConnectionMessage("USB 已连接，等待调试授权");
          return;
        }
        setSnapshot(emptySnapshot("not_connected"));
        setConnectionMessage("未发现已连接的手机");
        return;
      }
      const preferred = preferredSerial
        ? discovered.find((device) => device.sessionSerial === preferredSerial)
        : undefined;
      const selected = preferred
        ?? discovered.find((device) => device.transport === "ready")
        ?? discovered[0]!;
      setSelectedSerial(selected.sessionSerial);

      if (selected.transport !== "ready") {
        const connection = selected.transport === "unauthorized" ? "unauthorized" : "offline";
        setSnapshot(emptySnapshot(connection, selected));
        setConnectionMessage(connection === "unauthorized" ? "手机尚未允许 USB 调试" : "手机连接离线，请重新插拔");
        return;
      }
      const inspection = await client.inspectDevice(
        selected.sessionSerial,
        ALL_INSPECTION_GROUPS,
      );
      if (sequence !== refreshSequence.current) return;
      setSnapshot(snapshotFromInspection(inspection));
      setConnectionMessage("真机已连接 · 只读检查完成");
    } catch {
      if (sequence !== refreshSequence.current) return;
      setSnapshot(emptySnapshot("offline"));
      setConnectionMessage("检测失败，设备没有被修改");
    }
  }, [client]);

  useEffect(() => {
    void refreshDevice();
  }, [refreshDevice]);

  useEffect(() => {
    window.localStorage.setItem("opendevice.registry.v1", JSON.stringify(registry));
  }, [registry]);

  useEffect(() => {
    window.localStorage.setItem("opendevice.layouts.v1", JSON.stringify(profiles));
  }, [profiles]);

  const handlePluginAction = (action: PluginRegistryAction) => {
    setRegistry((current) => applyPluginAction(current, action).state);
  };

  const toggleSafeMode = () => {
    setRegistry((current) => applyPluginAction(current, {
      type: "setSafeMode",
      enabled: !current.safeMode,
    }).state);
  };

  const selectDevice = (sessionSerial: string) => {
    setSelectedSerial(sessionSerial);
    void refreshDevice(sessionSerial);
  };

  const exportReport = () => {
    const markdown = toMarkdownReport(createDeviceReport(snapshot, readiness));
    if (typeof URL.createObjectURL !== "function") return;
    const href = URL.createObjectURL(new Blob([markdown], { type: "text/markdown;charset=utf-8" }));
    const anchor = document.createElement("a");
    anchor.href = href;
    anchor.download = "opendevice-forge-report.md";
    anchor.click();
    URL.revokeObjectURL(href);
  };

  const content = page === "plugins" ? (
    <PluginMarket
      catalog={BUILTIN_PLUGINS}
      registry={registry}
      onAction={handlePluginAction}
      onEditLayout={() => setPage("layout")}
      onToggleSafeMode={toggleSafeMode}
    />
  ) : page === "layout" ? (
    <LayoutEditor snapshot={snapshot} profiles={profiles} registry={registry} onProfilesChange={setProfiles} onBack={() => setPage("plugins")} />
  ) : (
    <DeviceOverview snapshot={snapshot} readiness={readiness} connectionMessage={connectionMessage} onRefresh={refreshDevice} onNavigatePlugins={() => setPage("plugins")} onExportReport={exportReport} />
  );

  return (
    <AppShell
      page={page}
      onNavigate={setPage}
      onConnect={refreshDevice}
      snapshot={snapshot}
      devices={devices}
      selectedSerial={selectedSerial}
      onSelectDevice={selectDevice}
      connectionLabel={connectionMessage}
      safeMode={registry.safeMode}
    >
      {content}
    </AppShell>
  );
}
