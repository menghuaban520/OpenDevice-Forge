import type { PluginConfiguration } from "@opendevice/core";

export type PluginConfigurationRecord = Record<string, PluginConfiguration>;

export const PLUGIN_CONFIG_STORAGE_KEY = "opendevice.plugin-config.v1";

export const loadPluginConfigurations = (
  storage: Storage,
): PluginConfigurationRecord => {
  try {
    const parsed = JSON.parse(
      storage.getItem(PLUGIN_CONFIG_STORAGE_KEY) ?? "null",
    ) as unknown;
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      return {};
    }
    return parsed as PluginConfigurationRecord;
  } catch {
    return {};
  }
};

export const savePluginConfigurations = (
  storage: Storage,
  configurations: PluginConfigurationRecord,
): void => {
  storage.setItem(PLUGIN_CONFIG_STORAGE_KEY, JSON.stringify(configurations));
};
