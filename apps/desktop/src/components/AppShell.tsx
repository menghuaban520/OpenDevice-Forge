import type { ReactNode } from "react";
import { Home, LockKeyhole, PlugZap, ShieldCheck } from "lucide-react";
import type { DeviceSnapshot } from "@opendevice/core";
import type { AdbDeviceSummary } from "../lib/device-client";

export type AppPage = "overview" | "plugins" | "layout";

interface AppShellProps {
  children: ReactNode;
  page: AppPage;
  onNavigate: (page: AppPage) => void;
  onConnect: () => void;
  snapshot: DeviceSnapshot;
  devices: AdbDeviceSummary[];
  selectedSerial: string | null;
  onSelectDevice: (sessionSerial: string) => void;
  connectionLabel: string;
  safeMode: boolean;
}

const navItems: Array<{ page: "overview" | "plugins"; label: string; icon: typeof Home }> = [
  { page: "overview", label: "设备", icon: Home },
  { page: "plugins", label: "插件", icon: PlugZap },
];

const clean = (value: string | null) => value?.trim() || null;

export const deviceDisplayName = (snapshot: DeviceSnapshot): string => {
  const manufacturer = clean(snapshot.manufacturer.value);
  const model = clean(snapshot.model.value) ?? clean(snapshot.productName.value);
  if (!model) return "尚未连接设备";
  if (!manufacturer || model.toLocaleLowerCase().startsWith(manufacturer.toLocaleLowerCase())) return model;
  return `${manufacturer} ${model}`;
};

const secondaryIdentity = (snapshot: DeviceSnapshot): string => {
  const product = clean(snapshot.productName.value);
  const model = clean(snapshot.model.value);
  return product && product.toLocaleLowerCase() !== model?.toLocaleLowerCase()
    ? product
    : snapshot.connection === "ready"
      ? "Android 设备"
      : model ? "USB 设备 · 等待 ADB" : "等待识别 Android 设备";
};

const optionLabel = (device: AdbDeviceSummary): string => {
  const identity = clean(device.model) ?? clean(device.product) ?? "Android 设备";
  if (device.transport === "unauthorized") return `${identity}（等待授权）`;
  if (device.transport === "offline") return `${identity}（离线）`;
  return identity;
};

export function AppShell({
  children,
  page,
  onNavigate,
  onConnect,
  snapshot,
  devices,
  selectedSerial,
  onSelectDevice,
  connectionLabel,
  safeMode,
}: AppShellProps) {
  const displayName = deviceDisplayName(snapshot);

  return (
    <div className="app-window">
      <header className="titlebar">
        <div className="titlebar-brand">
          <img src="/assets/app-mark.svg" alt="" />
          <span>OpenDevice Forge</span>
        </div>
        <div className="window-controls" aria-hidden="true">
          <span>—</span><span>□</span><span>×</span>
        </div>
      </header>

      <aside className="device-rail">
        <div className="device-portrait-wrap">
          <img className="device-portrait" src="/assets/device-neutral.png" alt="Android 手机轮廓" />
        </div>
        <h1>{displayName}</h1>
        <p className="device-model">{secondaryIdentity(snapshot)}</p>
        <p className={`demo-state connection-${snapshot.connection}`}><span />{connectionLabel}</p>

        {devices.length > 1 ? (
          <label className="device-selector">
            <span>当前设备</span>
            <select
              aria-label="选择设备"
              value={selectedSerial ?? ""}
              onChange={(event) => onSelectDevice(event.target.value)}
            >
              {devices.map((device) => (
                <option key={device.sessionSerial} value={device.sessionSerial}>{optionLabel(device)}</option>
              ))}
            </select>
          </label>
        ) : null}

        <button className="connect-button" type="button" onClick={onConnect}>
          <PlugZap size={17} />{snapshot.connection === "ready" ? "重新检测" : "检测 Android 设备"}
        </button>

        <div className="rail-divider" />
        <p className="rail-group-title">核心（不可移除）</p>
        <nav className="rail-nav" aria-label="核心导航">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.page}
                type="button"
                className={page === item.page ? "active" : ""}
                onClick={() => onNavigate(item.page)}
                aria-label={item.label}
              >
                <Icon size={19} />
                <span>{item.label}</span>
                <LockKeyhole size={13} />
              </button>
            );
          })}
        </nav>

        <div className="rail-principle">
          <ShieldCheck size={17} />
          <div><strong>默认只读</strong><span>不刷机、不解锁、不修改手机</span></div>
        </div>
      </aside>

      <main className="workspace">{children}</main>

      <footer className="safety-strip">
        <div><ShieldCheck size={16} /><strong>只读模式</strong></div>
        <span title={connectionLabel}>当前没有对手机执行任何修改</span>
        {safeMode ? <span className="safe-mode-note">插件安全模式已开启</span> : null}
      </footer>
    </div>
  );
}
