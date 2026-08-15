import {
  BatteryMedium,
  BrainCircuit,
  ChevronRight,
  CircleAlert,
  CircleCheck,
  ClipboardCheck,
  Cloud,
  FileDown,
  HardDrive,
  HelpCircle,
  MoreHorizontal,
  PackageSearch,
  RefreshCw,
  Search,
  ShieldCheck,
  Stethoscope,
} from "lucide-react";
import type { DeviceSnapshot, ReadinessAssessment } from "@opendevice/core";

interface DeviceOverviewProps {
  snapshot: DeviceSnapshot;
  readiness: ReadinessAssessment;
  connectionMessage: string;
  onRefresh: () => void;
  onNavigatePlugins: () => void;
  onExportReport: () => void;
}

const factSource = (source: string) =>
  source === "measured" ? "真机读取" : source === "public_reference" ? "公开参考" : source === "demo" ? "演示值" : "待读取";

const value = <T,>(fact: { value: T | null; source: string }, formatter?: (value: T) => string) =>
  fact.value === null ? "待读取" : `${formatter ? formatter(fact.value) : String(fact.value)}（${factSource(fact.source)}）`;

export function DeviceOverview({
  snapshot,
  readiness,
  connectionMessage,
  onRefresh,
  onNavigatePlugins,
  onExportReport,
}: DeviceOverviewProps) {
  const actions = [
    { icon: Stethoscope, label: "完整检测", action: onRefresh },
    { icon: PackageSearch, label: "插件中心", action: onNavigatePlugins },
    { icon: BrainCircuit, label: "AI 节点", action: onNavigatePlugins },
    { icon: Cloud, label: "远程服务", action: onNavigatePlugins },
    { icon: FileDown, label: "导出报告", action: onExportReport },
    { icon: MoreHorizontal, label: "更多", action: onNavigatePlugins },
  ];

  return (
    <div className="page overview-page">
      <div className="page-toolbar">
        <div className="breadcrumb"><button type="button" aria-label="后退">‹</button><button type="button" aria-label="前进">›</button><span>nova 7 SE 乐活版</span><ChevronRight size={14} /><strong>现在</strong></div>
        <label className="search-box"><Search size={17} /><span className="sr-only">搜索</span><input placeholder="搜索" /></label>
        <button type="button" className="icon-button" aria-label="重新检测" onClick={onRefresh}><RefreshCw /></button>
        <button type="button" className="icon-button" aria-label="帮助"><HelpCircle /></button>
      </div>

      <div className="quick-actions">
        {actions.map(({ icon: Icon, label, action }) => (
          <button type="button" key={label} onClick={action}><Icon /><span>{label}</span></button>
        ))}
      </div>

      <div className="overview-grid">
        <section className="panel device-facts">
          <div className="panel-title"><h2>设备信息</h2><button type="button">显示全部</button></div>
          <dl>
            <div><dt>连接状态</dt><dd>{snapshot.mode === "demo" ? "未连接真机" : "已连接"}</dd></div>
            <div><dt>设备型号</dt><dd>{value(snapshot.model)}</dd></div>
            <div><dt>系统版本</dt><dd>{value(snapshot.androidVersion)}</dd></div>
            <div><dt>处理器架构</dt><dd>{value(snapshot.abi)}</dd></div>
            <div><dt>运行内存</dt><dd>{value(snapshot.ramBytes, (bytes) => `${(bytes / 1024 ** 3).toFixed(1)} GB`)}</dd></div>
            <div><dt>可用存储</dt><dd>{value(snapshot.storageAvailableBytes, (bytes) => `${(bytes / 1024 ** 3).toFixed(1)} GB`)}</dd></div>
            <div><dt>USB 调试</dt><dd>{snapshot.connection === "unauthorized" ? "等待手机允许" : "未检测"}</dd></div>
            <div><dt>ADB</dt><dd>{connectionMessage}</dd></div>
            <div><dt>Root 状态</dt><dd>无法确认</dd></div>
          </dl>
          <p className="source-note"><CircleAlert size={14} />参考值来自公开资料，连接后以真机为准</p>
        </section>

        <section className="panel action-panel">
          <h2>可执行动作</h2>
          {[
            [Stethoscope, "检查设备状态", onRefresh],
            [BrainCircuit, "评估 AI 节点", onNavigatePlugins],
            [Cloud, "配置远程访问", onNavigatePlugins],
            [PackageSearch, "浏览适配插件", onNavigatePlugins],
            [ShieldCheck, "高级权限检查", onNavigatePlugins],
          ].map(([Icon, label, action]) => {
            const ActionIcon = Icon as typeof Stethoscope;
            return <button type="button" key={label as string} onClick={action as () => void}><ActionIcon /><span>{label as string}</span><ChevronRight size={16} /></button>;
          })}
        </section>

        <section className="panel storage-panel">
          <h2>存储</h2>
          <div className="meter"><span style={{ width: snapshot.mode === "demo" ? "62%" : "12%" }} /></div>
          <p><HardDrive size={15} />可用容量需连接后读取</p>
        </section>

        <section className="panel battery-panel">
          <h2>电池</h2>
          <div><BatteryMedium size={32} /><p><strong>{snapshot.batteryPercent.value === null ? "未读取" : `${snapshot.batteryPercent.value}%（演示）`}</strong><span>健康度与温度需连接真机后读取</span></p></div>
        </section>

        <section className="panel findings-panel">
          <h2>发现与建议</h2>
          <div><CircleAlert /><span>{connectionMessage} — 连接前需要准备</span><button type="button" onClick={onRefresh}>重新检测</button></div>
          <div><CircleAlert /><span>系统版本尚未真机读取 — 不判断兼容限制</span><button type="button" onClick={onRefresh}>连接设备</button></div>
          <div><CircleCheck /><span>{readiness.summary}</span><button type="button" onClick={onNavigatePlugins}>查看评估</button></div>
        </section>

        <section className="panel service-panel">
          <h2>插件与服务</h2>
          <div><ClipboardCheck /><span>设备体检 · 内置</span><strong>就绪</strong></div>
          <div><BrainCircuit /><span>AI 节点 · 已启用</span><strong>{readiness.demo ? "演示评估" : readiness.verdict}</strong></div>
          <div><Cloud /><span>远程网关 · 已启用</span><strong>仅配置</strong></div>
          <button type="button" onClick={onNavigatePlugins}>打开插件市场</button>
        </section>
      </div>
    </div>
  );
}
