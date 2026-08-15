import type { ReactNode } from "react";
import {
  Box,
  ClipboardList,
  Home,
  LockKeyhole,
  PackagePlus,
  PlugZap,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
} from "lucide-react";
import { isPluginEffective, type PluginRegistryState } from "@opendevice/core";

export type AppPage = "overview" | "plugins" | "layout" | "history" | "settings";

interface AppShellProps {
  children: ReactNode;
  page: AppPage;
  onNavigate: (page: AppPage) => void;
  onConnect: () => void;
  registry: PluginRegistryState;
  connectionLabel: string;
  onToggleSafeMode: () => void;
}

const navItems: Array<{ page: AppPage; label: string; icon: typeof Home }> = [
  { page: "overview", label: "设备总览", icon: Home },
  { page: "plugins", label: "插件市场", icon: PlugZap },
  { page: "history", label: "任务记录", icon: ClipboardList },
  { page: "settings", label: "设置", icon: Settings },
];

const enabledNames: Record<string, string> = {
  "dev.opendevice.device-inspection": "设备体检",
  "dev.opendevice.ai-readiness": "AI 节点",
  "dev.opendevice.remote-gateway": "远程网关",
  "dev.opendevice.report-export": "报告导出",
};

export function AppShell({
  children,
  page,
  onNavigate,
  onConnect,
  registry,
  connectionLabel,
  onToggleSafeMode,
}: AppShellProps) {
  const enabledPlugins = Object.values(registry.plugins).filter(
    (plugin) => isPluginEffective(registry, plugin.manifest.id) && !plugin.manifest.protected && enabledNames[plugin.manifest.id],
  );

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
          <img className="device-portrait" src="/assets/device-neutral.png" alt="无品牌旧安卓手机演示渲染" />
        </div>
        <h1>HUAWEI nova 7 SE 5G 乐活版</h1>
        <p className="device-model">CDL-AN50</p>
        <p className="demo-state"><span />演示视图 · 未连接真机</p>
        <button className="connect-button" type="button" onClick={onConnect}>
          <PlugZap size={17} />连接真机
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
                {item.page === "overview" || item.page === "plugins" ? <LockKeyhole size={13} /> : null}
              </button>
            );
          })}
        </nav>

        <div className="rail-divider" />
        <div className="rail-heading-row">
          <p className="rail-group-title">已启用插件</p>
          <button type="button" onClick={() => onNavigate("layout")}>编辑</button>
        </div>
        <div className="enabled-plugin-list">
          {enabledPlugins.map((plugin) => (
            <div key={plugin.manifest.id} className="enabled-plugin-row">
              <Box size={17} />
              <span>{enabledNames[plugin.manifest.id]}</span>
              <span className="mini-switch on" aria-hidden="true" />
            </div>
          ))}
        </div>
        <button className="add-source" type="button" onClick={() => onNavigate("plugins")}>
          <PackagePlus size={19} />添加插件来源
        </button>
      </aside>

      <main className="workspace">{children}</main>

      <footer className="safety-strip">
        <div><ShieldCheck size={16} /><strong>只读模式</strong></div>
        <span title={connectionLabel}>当前没有对手机执行任何修改</span>
        {registry.safeMode ? <span className="safe-mode-note">安全模式已开启，仅保留内核插件</span> : null}
        <button type="button" onClick={onToggleSafeMode}>
          <SlidersHorizontal size={15} />
          {registry.safeMode ? "退出安全模式" : "进入安全模式"}
        </button>
      </footer>
    </div>
  );
}
