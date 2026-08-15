import {
  BatteryMedium,
  ChevronRight,
  CircleAlert,
  CircleCheck,
  Cpu,
  FileDown,
  HardDrive,
  PackageSearch,
  RefreshCw,
  ShieldCheck,
  Smartphone,
  Usb,
} from "lucide-react";
import type { DeviceSnapshot, ReadinessAssessment } from "@opendevice/core";
import { deviceDisplayName } from "../../components/AppShell";

interface DeviceOverviewProps {
  snapshot: DeviceSnapshot;
  readiness: ReadinessAssessment;
  connectionMessage: string;
  onRefresh: () => void;
  onNavigatePlugins: () => void;
  onExportReport: () => void;
}

const value = <T,>(fact: { value: T | null }, formatter?: (value: T) => string) =>
  fact.value === null ? "待读取" : formatter ? formatter(fact.value) : String(fact.value);

const connectionTitle = (connection: DeviceSnapshot["connection"]): string => {
  if (connection === "ready") return "设备已识别";
  if (connection === "unauthorized") return "已发现手机，等待授权";
  if (connection === "offline") return "手机当前离线";
  if (connection === "adb_missing") return "缺少 Android 设备桥接";
  return "连接一台 Android 手机";
};

const connectionHelp = (connection: DeviceSnapshot["connection"]): string => {
  if (connection === "ready") return "型号与硬件信息来自当前手机，不使用预置机型资料覆盖。";
  if (connection === "unauthorized") return "解锁手机，在 USB 调试弹窗中允许这台电脑，然后再次检测。";
  if (connection === "offline") return "重新插拔数据线，并将 USB 用途切换为文件传输后再试。";
  if (connection === "adb_missing") return "先安装 Android Platform-Tools；OpenDevice Forge 检测到 ADB 后会自动读取设备。";
  return "打开开发者选项与 USB 调试，用可传输数据的线连接电脑。";
};

export function DeviceOverview({
  snapshot,
  readiness,
  connectionMessage,
  onRefresh,
  onNavigatePlugins,
  onExportReport,
}: DeviceOverviewProps) {
  const displayName = deviceDisplayName(snapshot);
  const connected = snapshot.connection === "ready";
  const usbVisible = !connected && snapshot.model.value !== null;
  const title = usbVisible ? "USB 已连接，等待调试授权" : connectionTitle(snapshot.connection);
  const help = usbVisible
    ? snapshot.manufacturer.value?.toLocaleUpperCase() === "HUAWEI"
      ? "手机已被 macOS 识别。请在开发人员选项中打开“USB 调试”和“仅充电模式下允许 ADB 调试”。"
      : "手机已被电脑识别。请打开 USB 调试，并在手机上允许这台电脑。"
    : connectionHelp(snapshot.connection);

  return (
    <div className="page overview-page">
      <div className="page-toolbar overview-toolbar">
        <div className="breadcrumb"><span>{displayName}</span><ChevronRight size={14} /><strong>设备</strong></div>
        <span className="toolbar-context">Android · 只读检测</span>
        <button type="button" className="icon-button" aria-label="重新检测" onClick={onRefresh}><RefreshCw /></button>
      </div>

      <div className="quick-actions compact-actions">
        <button type="button" onClick={onRefresh}><RefreshCw /><span>重新检测</span></button>
        <button type="button" onClick={onExportReport} disabled={!connected}><FileDown /><span>生成报告</span></button>
        <button type="button" onClick={onNavigatePlugins}><PackageSearch /><span>查找插件</span></button>
      </div>

      <div className="overview-grid device-first-grid">
        <section className={`panel connection-panel connection-${snapshot.connection}`}>
          <div className="connection-icon"><Smartphone /></div>
          <div><h2>{title}</h2><p>{help}</p><small>{connectionMessage}</small></div>
          <button className="primary-button" type="button" onClick={onRefresh}>{connected ? "再次读取" : "重新检测"}</button>
        </section>

        <section className="panel device-facts essential-facts">
          <div className="panel-title"><h2>这台设备</h2><span>{connected ? "真机读取" : "等待连接"}</span></div>
          <dl>
            <div><dt>设备名称</dt><dd>{displayName}</dd></div>
            <div><dt>产品代号</dt><dd>{value(snapshot.productName)}</dd></div>
            <div><dt>Android 版本</dt><dd>{value(snapshot.androidVersion)}</dd></div>
            <div><dt>处理器架构</dt><dd>{value(snapshot.abi)}</dd></div>
            <div><dt>运行内存</dt><dd>{value(snapshot.ramBytes, (bytes) => `${(bytes / 1024 ** 3).toFixed(1)} GB`)}</dd></div>
            <div><dt>可用存储</dt><dd>{value(snapshot.storageAvailableBytes, (bytes) => `${(bytes / 1024 ** 3).toFixed(1)} GB`)}</dd></div>
            <div><dt>当前电量</dt><dd>{value(snapshot.batteryPercent, (percent) => `${percent}%`)}</dd></div>
          </dl>
          <p className="source-note"><ShieldCheck size={14} />基础识别读取 Android 标准属性，不依赖品牌机型名单</p>
        </section>

        <section className="panel action-panel next-step-panel">
          <h2>接下来</h2>
          <button type="button" onClick={onRefresh}><Usb /><span>{connected ? "更新设备信息" : "完成连接检查"}</span><ChevronRight size={16} /></button>
          <button type="button" onClick={onNavigatePlugins}><PackageSearch /><span>查看适合当前设备的插件</span><ChevronRight size={16} /></button>
          <button type="button" onClick={onExportReport} disabled={!connected}><FileDown /><span>导出只读检测报告</span><ChevronRight size={16} /></button>
        </section>

        <section className="panel readiness-panel">
          <div className="readiness-heading">
            {connected ? (readiness.verdict === "not_ready" ? <CircleAlert /> : <CircleCheck />) : <CircleAlert />}
            <div><h2>{connected ? "轻量模型基础条件" : "连接后再判断适配"}</h2><p>{connected ? readiness.summary : "未取得真机数据前，不判断内存、架构或模型兼容性。"}</p></div>
          </div>
          {connected ? (
            <div className="readiness-facts">
              <span><Cpu />{value(snapshot.abi)}</span>
              <span><HardDrive />{value(snapshot.storageAvailableBytes, (bytes) => `${(bytes / 1024 ** 3).toFixed(1)} GB 可用`)}</span>
              <span><BatteryMedium />{value(snapshot.batteryPercent, (percent) => `${percent}%`)}</span>
            </div>
          ) : null}
        </section>
      </div>
    </div>
  );
}
