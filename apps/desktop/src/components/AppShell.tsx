import type { ReactNode } from "react";
import { Home, PlugZap } from "lucide-react";
import type { DeviceSnapshot } from "@opendevice/core";
import type { AdbDeviceSummary } from "../lib/device-client";

export type AppPage = "overview" | "plugins" | "workbench";

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
}

const navItems: Array<{ page: "overview" | "plugins"; label: string; icon: typeof Home }> = [
  { page: "overview", label: "设备", icon: Home },
  { page: "plugins", label: "模块", icon: PlugZap },
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
}: AppShellProps) {
  const displayName = deviceDisplayName(snapshot);

  return (
    <div className="app-window">
      <header className="titlebar">
        <div className="titlebar-brand">
          <img src="/assets/app-mark.svg" alt="" />
          <span>OpenDevice Forge</span>
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
        <nav className="rail-nav" aria-label="主导航">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = item.page === "plugins"
              ? page === "plugins" || page === "workbench"
              : page === item.page;
            return (
              <button
                key={item.page}
                type="button"
                className={active ? "active" : ""}
                onClick={() => onNavigate(item.page)}
                aria-label={item.label}
              >
                <Icon size={19} />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
      </aside>

      <main className="workspace">{children}</main>
    </div>
  );
}
