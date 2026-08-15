import type { PluginManifest } from "./types";

const base = (
  id: string,
  name: string,
  summary: string,
): Pick<
  PluginManifest,
  | "manifestVersion"
  | "id"
  | "name"
  | "summary"
  | "version"
  | "publisher"
  | "kernel"
  | "source"
  | "execution"
> => ({
  manifestVersion: "1",
  id,
  name,
  summary,
  version: "0.1.0",
  publisher: "OpenDevice Forge",
  kernel: { min: "0.1.0", maxExclusive: "1.0.0" },
  source: { kind: "official" },
  execution: "declarative",
});

export const BUILTIN_PLUGINS: PluginManifest[] = [
  {
    ...base("kernel.plugin-manager", "插件管理器", "安装、启停、更新、回退与恢复插件。"),
    audience: "general",
    risk: "low",
    permissions: [],
    contributes: [
      { id: "plugins", type: "navigation", label: "插件", defaultPlacement: "sidebar" },
    ],
    protected: true,
  },
  {
    ...base("dev.opendevice.device-inspection", "设备体检", "只读扫描设备状态并生成可解释结果。"),
    audience: "general",
    risk: "low",
    permissions: ["device.read", "device.report"],
    contributes: [
      { id: "inspect", type: "deviceProbe", label: "设备体检", defaultPlacement: "overview" },
      { id: "report", type: "reportSection", label: "体检报告", defaultPlacement: "report" },
    ],
  },
  {
    ...base("dev.opendevice.ai-readiness", "AI 节点", "解释旧手机运行轻量模型的准备情况。"),
    audience: "general",
    risk: "low",
    permissions: ["device.read"],
    contributes: [
      {
        id: "ai-readiness",
        type: "overviewSection",
        label: "AI 节点就绪度",
        defaultPlacement: "overview",
      },
    ],
  },
  {
    ...base("dev.opendevice.report-export", "报告导出", "导出不含个人文件内容的设备基线报告。"),
    audience: "general",
    risk: "low",
    permissions: ["device.report"],
    contributes: [
      { id: "export", type: "reportSection", label: "导出报告", defaultPlacement: "report" },
    ],
  },
  {
    ...base("dev.opendevice.remote-gateway", "远程网关", "规划本地服务端口与远程访问配置，不在 MVP 中启动公网服务。"),
    audience: "advanced",
    risk: "medium",
    permissions: ["network.outbound", "service.local"],
    contributes: [
      { id: "gateway", type: "service", label: "远程网关", defaultPlacement: "service" },
      { id: "gateway-config", type: "configuration", label: "网关配置", defaultPlacement: "context" },
    ],
  },
  {
    ...base("dev.opendevice.root-guide", "Root 实验室", "仅展示风险、条件与恢复知识，不执行 Root 或解锁。"),
    audience: "advanced",
    risk: "high",
    permissions: [],
    contributes: [
      { id: "root-guide", type: "configuration", label: "Root 实验室", defaultPlacement: "context" },
    ],
  },
];

