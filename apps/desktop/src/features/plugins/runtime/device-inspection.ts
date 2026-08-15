import type {
  PluginConfiguration,
  PluginResultGroup,
  PluginResultItem,
  WorkflowRuntimeDefinition,
} from "@opendevice/core";
import type {
  DeviceClient,
  DeviceInspection,
  InspectionSelection,
} from "../../../lib/device-client";
import type { DesktopPluginContext } from "./registry";

export const DEVICE_INSPECTION_ENTRY = "builtin:device-inspection";
export const DEVICE_INSPECTION_ID = "dev.opendevice.device-inspection";

const GROUP_IDS = ["identity", "performance", "power", "system"] as const;
type InspectionGroupId = (typeof GROUP_IDS)[number];

export const DEFAULT_INSPECTION_CONFIG: PluginConfiguration = {
  groups: [...GROUP_IDS],
};

const textItem = (
  id: string,
  label: string,
  value: string | number | null,
  formatter: (value: string | number) => string = String,
): PluginResultItem => value === null
  ? { id, label, value: "设备未提供", status: "unknown" }
  : { id, label, value: formatter(value), status: "ok" };

const gibibytes = (value: string | number) =>
  `${(Number(value) / 1024 ** 3).toFixed(1)} GB`;

const groupsFrom = (
  selected: InspectionGroupId[],
  inspection: DeviceInspection,
): PluginResultGroup[] => {
  const groups: PluginResultGroup[] = [];
  if (selected.includes("identity")) {
    groups.push({
      id: "identity",
      label: "设备身份",
      items: [
        textItem("manufacturer", "厂商", inspection.manufacturer),
        textItem("model", "型号", inspection.model),
        textItem("productName", "产品代号", inspection.productName),
        textItem("androidVersion", "Android 版本", inspection.androidVersion),
        textItem("abi", "处理器架构", inspection.abi),
      ],
    });
  }
  if (selected.includes("performance")) {
    groups.push({
      id: "performance",
      label: "性能基础",
      items: [
        textItem("ramBytes", "运行内存", inspection.ramBytes, gibibytes),
        textItem(
          "storageAvailableBytes",
          "可用存储",
          inspection.storageAvailableBytes,
          gibibytes,
        ),
      ],
    });
  }
  if (selected.includes("power")) {
    groups.push({
      id: "power",
      label: "电源状态",
      items: [
        textItem(
          "batteryPercent",
          "当前电量",
          inspection.batteryPercent,
          (value) => `${value}%`,
        ),
      ],
    });
  }
  if (selected.includes("system")) {
    groups.push({
      id: "system",
      label: "系统状态",
      items: [
        { id: "adb", label: "ADB 连接", value: "已连接", status: "ok" },
        {
          id: "rootSignals",
          label: "只读 Root 信号",
          value: inspection.rootSignals.length > 0
            ? inspection.rootSignals.join("、")
            : "未发现",
          status: "ok",
        },
      ],
    });
  }
  return groups;
};

const selectedGroups = (
  configuration: PluginConfiguration,
): InspectionGroupId[] => {
  const groups = configuration.groups;
  if (!Array.isArray(groups)) return [];
  return groups.filter(
    (group): group is InspectionGroupId =>
      typeof group === "string" && GROUP_IDS.includes(group as InspectionGroupId),
  );
};

const selectionFrom = (groups: InspectionGroupId[]): InspectionSelection => ({
  identity: groups.includes("identity"),
  performance: groups.includes("performance"),
  power: groups.includes("power"),
  system: groups.includes("system"),
});

export const createDeviceInspectionRuntime = (
  client: DeviceClient,
): WorkflowRuntimeDefinition<DesktopPluginContext> => ({
  pluginId: DEVICE_INSPECTION_ID,
  kind: "workflow",
  configuration: {
    version: "1",
    fields: [{
      id: "groups",
      type: "checkbox-group",
      label: "检测项目",
      options: [
        { value: "identity", label: "设备身份", description: "厂商、型号、系统与架构" },
        { value: "performance", label: "性能基础", description: "运行内存与可用存储" },
        { value: "power", label: "电源状态", description: "当前电量" },
        { value: "system", label: "系统状态", description: "ADB 与只读 Root 信号" },
      ],
    }],
  },
  defaultConfiguration: DEFAULT_INSPECTION_CONFIG,
  validate: (configuration) => selectedGroups(configuration).length > 0
    ? {}
    : { groups: "至少选择一个检测项目" },
  execute: async ({ context, configuration, signal, onProgress }) => {
    const groups = selectedGroups(configuration);
    if (groups.length === 0) throw new Error("configuration_invalid");
    if (!context.sessionSerial) throw new Error("device_not_connected");
    if (signal.aborted) throw new Error("cancelled");

    onProgress({ phase: "reading", message: "正在读取设备…" });
    const inspection = await client.inspectDevice(
      context.sessionSerial,
      selectionFrom(groups),
    );
    if (signal.aborted) throw new Error("cancelled");

    return { groups: groupsFrom(groups, inspection) };
  },
});
