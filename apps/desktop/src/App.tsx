import { useCallback, useEffect, useMemo, useState } from "react";
import {
  applyPluginAction,
  assessReadiness,
  BUILTIN_PLUGINS,
  createDeviceReport,
  createPluginRegistry,
  DEMO_NOVA7,
  mergeDeviceSnapshots,
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
import { createDeviceClient, type DeviceClient, type DeviceInspection } from "./lib/device-client";
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

const applyInspection = (inspection: DeviceInspection): Partial<DeviceSnapshot> => ({
  mode: "live",
  connection: "ready",
  capturedAt: new Date().toISOString(),
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

export function App({ deviceClient }: AppProps) {
  const client = useMemo(() => deviceClient ?? createDeviceClient(), [deviceClient]);
  const [page, setPage] = useState<AppPage>("overview");
  const [registry, setRegistry] = useState(loadRegistry);
  const [profiles, setProfiles] = useState<LayoutProfile[]>(loadProfiles);
  const [snapshot, setSnapshot] = useState<DeviceSnapshot>(DEMO_NOVA7);
  const [connectionMessage, setConnectionMessage] = useState("正在检测设备…");
  const readiness = useMemo(() => assessReadiness(snapshot), [snapshot]);

  const refreshDevice = useCallback(async () => {
    setConnectionMessage("正在检测设备…");
    try {
      const probe = await client.probeAdb();
      if (!probe.available) {
        setSnapshot({ ...DEMO_NOVA7, connection: "adb_missing" });
        setConnectionMessage("电脑尚未安装 ADB");
        return;
      }
      const devices = await client.listDevices();
      if (devices.length === 0) {
        setSnapshot({ ...DEMO_NOVA7, connection: "not_connected" });
        setConnectionMessage("未发现已连接的手机");
        return;
      }
      const ready = devices.find((device) => device.transport === "ready");
      if (!ready) {
        const unauthorized = devices.some((device) => device.transport === "unauthorized");
        setSnapshot({
          ...DEMO_NOVA7,
          connection: unauthorized ? "unauthorized" : "offline",
        });
        setConnectionMessage(unauthorized ? "手机尚未允许 USB 调试" : "手机连接离线，请重新插拔");
        return;
      }
      const inspection = await client.inspectDevice(ready.sessionSerial);
      setSnapshot(mergeDeviceSnapshots(DEMO_NOVA7, applyInspection(inspection)));
      setConnectionMessage("真机已连接 · 只读检查完成");
    } catch {
      setSnapshot({ ...DEMO_NOVA7, connection: "offline" });
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
    <PluginMarket catalog={BUILTIN_PLUGINS} registry={registry} onAction={handlePluginAction} onEditLayout={() => setPage("layout")} />
  ) : page === "layout" ? (
    <LayoutEditor profiles={profiles} registry={registry} onProfilesChange={setProfiles} onBack={() => setPage("plugins")} />
  ) : page === "history" ? (
    <SimplePage title="任务记录" subtitle="所有插件变更只记录可恢复的本地动作。">
      {registry.audit.length === 0 ? <p className="empty-note">还没有插件任务。安装、启停或布局调整后会显示在这里。</p> : registry.audit.map((event) => <div className="audit-row" key={event.sequence}><strong>#{event.sequence}</strong><span>{event.action}</span><code>{event.pluginId}</code></div>)}
    </SimplePage>
  ) : page === "settings" ? (
    <SimplePage title="设置" subtitle="内核、安全模式与本地数据边界。">
      <div className="settings-card"><h3>只读设备访问</h3><p>当前版本只允许固定 ADB 读取命令，不提供任意 shell、安装、解锁或文件提取。</p></div>
      <div className="settings-card"><h3>插件恢复</h3><p>可以停用插件、回退版本、恢复默认布局；核心插件管理器不可移除。</p></div>
    </SimplePage>
  ) : (
    <DeviceOverview snapshot={snapshot} readiness={readiness} connectionMessage={connectionMessage} onRefresh={refreshDevice} onNavigatePlugins={() => setPage("plugins")} onExportReport={exportReport} />
  );

  return (
    <AppShell
      page={page}
      onNavigate={setPage}
      onConnect={refreshDevice}
      registry={registry}
      connectionLabel={connectionMessage}
      onToggleSafeMode={toggleSafeMode}
    >
      {content}
    </AppShell>
  );
}

function SimplePage({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return <div className="page simple-page"><div className="simple-page-header"><h2>{title}</h2><p>{subtitle}</p></div><div className="simple-page-content">{children}</div></div>;
}
