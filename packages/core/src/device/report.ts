import type {
  DeviceFact,
  DeviceReport,
  DeviceSnapshot,
  FactSource,
  ReadinessAssessment,
} from "./types";

const EXCLUDED_ACTIONS = [
  "Root",
  "解锁 Bootloader",
  "安装 APK",
  "修改设备标识",
  "绕过设备锁",
  "提取个人文件",
];

export const createDeviceReport = (
  snapshot: DeviceSnapshot,
  readiness: ReadinessAssessment,
): DeviceReport => ({
  schemaVersion: "1",
  generatedAt: snapshot.capturedAt,
  mode: snapshot.mode,
  safety: {
    access: "只读",
    excludedActions: [...EXCLUDED_ACTIONS],
  },
  device: {
    connection: snapshot.connection,
    manufacturer: snapshot.manufacturer,
    productName: snapshot.productName,
    model: snapshot.model,
    androidVersion: snapshot.androidVersion,
    abi: snapshot.abi,
    ramBytes: snapshot.ramBytes,
    storageAvailableBytes: snapshot.storageAvailableBytes,
    batteryPercent: snapshot.batteryPercent,
    rootSignals: snapshot.rootSignals,
  },
  readiness,
});

export const toJsonReport = (report: DeviceReport): string =>
  `${JSON.stringify(report, null, 2)}\n`;

const sourceLabel: Record<FactSource, string> = {
  measured: "真机实测",
  public_reference: "公开参考",
  demo: "演示值",
  unknown: "未知",
};

const displayFact = <T>(fact: DeviceFact<T>): string => {
  const value = fact.value === null
    ? "未读取"
    : Array.isArray(fact.value)
      ? fact.value.join("、") || "未发现"
      : String(fact.value);
  return `${value}（${sourceLabel[fact.source]}）`;
};

export const toMarkdownReport = (report: DeviceReport): string => {
  const demoNotice = report.mode === "demo"
    ? "> 演示数据，不代表已连接真机。\n\n"
    : "";
  const checks = report.readiness.checks
    .map((check) => `- ${check.label}：${check.detail}（${check.status}，${sourceLabel[check.source]}）`)
    .join("\n");
  return `# OpenDevice Forge 设备报告

${demoNotice}生成时间：${report.generatedAt}

## 设备事实

- 设备：${displayFact(report.device.productName)}
- 型号：${displayFact(report.device.model)}
- Android：${displayFact(report.device.androidVersion)}
- 架构：${displayFact(report.device.abi)}
- 内存字节：${displayFact(report.device.ramBytes)}
- 可用存储字节：${displayFact(report.device.storageAvailableBytes)}
- 电量：${displayFact(report.device.batteryPercent)}

## AI 节点就绪度

结论：${report.readiness.verdict}。${report.readiness.summary}

${checks}

## 安全边界

本报告仅使用只读检查；不执行 Root、解锁、安装或文件提取，也不修改设备标识或绕过设备锁。
`;
};

