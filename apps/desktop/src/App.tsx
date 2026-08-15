import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  applyPluginAction,
  BUILTIN_PLUGINS,
  createPluginRegistry,
  isPluginEffective,
  isPluginRunnable,
  type DeviceSnapshot,
  type PluginConfiguration,
  type PluginManifest,
  type PluginRegistryAction,
  type PluginRegistryState,
} from "@opendevice/core";
import { AppShell, type AppPage } from "./components/AppShell";
import { DeviceOverview } from "./features/overview/DeviceOverview";
import { PluginMarket } from "./features/plugins/PluginMarket";
import { PluginWorkbench } from "./features/plugins/PluginWorkbench";
import { createDesktopPluginRuntimes } from "./features/plugins/runtime/registry";
import {
  loadPluginConfigurations,
  savePluginConfigurations,
  type PluginConfigurationRecord,
} from "./features/plugins/runtime/storage";
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
  BUILTIN_PLUGINS.filter((manifest) => manifest.id === "kernel.plugin-manager"),
);

const loadRegistry = (): PluginRegistryState => {
  try {
    const parsed = JSON.parse(window.localStorage.getItem("opendevice.registry.v2") ?? "null") as PluginRegistryState | null;
    if (parsed?.plugins?.["kernel.plugin-manager"]?.manifest.protected === true && Array.isArray(parsed.audit)) {
      return parsed;
    }
  } catch {
    // Invalid preferences are ignored so the protected kernel can recover.
  }
  return initialRegistry();
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
  const runtimes = useMemo(() => createDesktopPluginRuntimes(client), [client]);
  const [page, setPage] = useState<AppPage>("overview");
  const [registry, setRegistry] = useState(loadRegistry);
  const [configurations, setConfigurations] = useState<PluginConfigurationRecord>(
    () => loadPluginConfigurations(window.localStorage),
  );
  const [workbenchPluginId, setWorkbenchPluginId] = useState<string | null>(null);
  const [snapshot, setSnapshot] = useState<DeviceSnapshot>(() => emptySnapshot());
  const [devices, setDevices] = useState<AdbDeviceSummary[]>([]);
  const [selectedSerial, setSelectedSerial] = useState<string | null>(null);
  const [connectionMessage, setConnectionMessage] = useState("正在检测设备…");
  const refreshSequence = useRef(0);

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
    window.localStorage.setItem("opendevice.registry.v2", JSON.stringify(registry));
  }, [registry]);

  useEffect(() => {
    savePluginConfigurations(window.localStorage, configurations);
  }, [configurations]);

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

  const openPlugin = (manifest: PluginManifest) => {
    if (!isPluginRunnable(manifest, runtimes)) return;
    if (!isPluginEffective(registry, manifest.id)) return;
    setWorkbenchPluginId(manifest.id);
    setPage("workbench");
  };

  const workbench = useMemo(() => {
    const manifest = BUILTIN_PLUGINS.find((item) => item.id === workbenchPluginId);
    const entry = manifest?.runtime?.entry;
    const runtime = entry ? runtimes.get(entry) : undefined;
    return manifest && runtime ? { manifest, runtime } : null;
  }, [runtimes, workbenchPluginId]);

  const updatePluginConfiguration = (
    pluginId: string,
    configuration: PluginConfiguration,
  ) => {
    setConfigurations((current) => ({ ...current, [pluginId]: configuration }));
  };

  const content = page === "plugins" ? (
    <PluginMarket
      catalog={BUILTIN_PLUGINS}
      registry={registry}
      onAction={handlePluginAction}
      isRunnable={(manifest) => isPluginRunnable(manifest, runtimes)}
      onOpen={openPlugin}
      onToggleSafeMode={toggleSafeMode}
    />
  ) : page === "workbench" && workbench ? (
    <PluginWorkbench
      manifest={workbench.manifest}
      runtime={workbench.runtime}
      context={{ sessionSerial: selectedSerial }}
      configuration={configurations[workbench.manifest.id] ?? workbench.runtime.defaultConfiguration}
      onConfigurationChange={(configuration) => updatePluginConfiguration(workbench.manifest.id, configuration)}
      canRun={snapshot.connection === "ready" && selectedSerial !== null}
      unavailableReason="请先连接一台已授权的 Android 手机"
      onBack={() => setPage("plugins")}
    />
  ) : (
    <DeviceOverview
      snapshot={snapshot}
      connectionMessage={connectionMessage}
      onRefresh={refreshDevice}
      onNavigatePlugins={() => setPage("plugins")}
    />
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
    >
      {content}
    </AppShell>
  );
}
