import {
  ChevronRight,
  PackageSearch,
  ShieldCheck,
  Smartphone,
} from "lucide-react";
import type { DeviceSnapshot } from "@opendevice/core";
import { deviceDisplayName } from "../../components/AppShell";

interface DeviceOverviewProps {
  snapshot: DeviceSnapshot;
  connectionMessage: string;
  onRefresh: () => void;
  onNavigatePlugins: () => void;
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
  connectionMessage,
  onRefresh,
  onNavigatePlugins,
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
        <span className="toolbar-context">Android 设备入口</span>
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

        <section className="panel plugin-entry-panel">
          <PackageSearch />
          <div>
            <h2>用插件继续</h2>
            <p>检测、实验和本机服务都从插件市场安装，再进入独立工作台运行。</p>
          </div>
          <button className="primary-button" type="button" onClick={onNavigatePlugins}>浏览插件市场</button>
        </section>
      </div>
    </div>
  );
}
