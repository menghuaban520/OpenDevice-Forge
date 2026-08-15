import { compareVersions, validatePluginManifest } from "./manifest";
import type {
  InstalledPlugin,
  PluginAuditAction,
  PluginAuditEvent,
  PluginManifest,
  PluginRegistryAction,
  PluginRegistryResult,
  PluginRegistryState,
} from "./types";

const appendAudit = (
  state: PluginRegistryState,
  action: PluginAuditAction,
  pluginId: string,
  versions: { fromVersion?: string; toVersion?: string } = {},
): PluginRegistryState => {
  const event: PluginAuditEvent = {
    sequence: state.audit.length + 1,
    action,
    pluginId,
    ...versions,
  };
  return { ...state, audit: [...state.audit, event] };
};

const withPlugin = (
  state: PluginRegistryState,
  pluginId: string,
  plugin: InstalledPlugin,
): PluginRegistryState => ({
  ...state,
  plugins: { ...state.plugins, [pluginId]: plugin },
});

const failure = (
  state: PluginRegistryState,
  error: NonNullable<PluginRegistryResult["error"]>,
): PluginRegistryResult => ({ ok: false, state, error });

export const createPluginRegistry = (
  builtins: PluginManifest[] = [],
  kernelVersion = "0.1.0",
): PluginRegistryState => {
  const plugins: Record<string, InstalledPlugin> = {};
  for (const manifest of builtins) {
    plugins[manifest.id] = { manifest, enabled: true, previousVersions: [] };
  }
  return { kernelVersion, safeMode: false, plugins, audit: [] };
};

export const isPluginEffective = (
  state: PluginRegistryState,
  pluginId: string,
): boolean => {
  const plugin = state.plugins[pluginId];
  if (!plugin?.enabled) return false;
  return !state.safeMode || plugin.manifest.protected === true;
};

export const applyPluginAction = (
  state: PluginRegistryState,
  action: PluginRegistryAction,
): PluginRegistryResult => {
  if (action.type === "setSafeMode") {
    if (state.safeMode === action.enabled) return { ok: true, state };
    const next = appendAudit(
      { ...state, safeMode: action.enabled },
      action.enabled ? "safe_mode_on" : "safe_mode_off",
      "kernel",
    );
    return { ok: true, state: next };
  }

  if (action.type === "install") {
    if (!validatePluginManifest(action.manifest, state.kernelVersion).ok) {
      return failure(state, "manifest_invalid");
    }
    if (state.plugins[action.manifest.id]) {
      return failure(state, "plugin_already_installed");
    }
    const next = withPlugin(state, action.manifest.id, {
      manifest: action.manifest,
      enabled: false,
      previousVersions: [],
    });
    return {
      ok: true,
      state: appendAudit(next, "install", action.manifest.id, {
        toVersion: action.manifest.version,
      }),
    };
  }

  const pluginId = action.type === "update" ? action.manifest.id : action.pluginId;
  const installed = state.plugins[pluginId];
  if (!installed) return failure(state, "plugin_not_installed");

  if (action.type === "enable") {
    if (installed.enabled) return { ok: true, state };
    const next = withPlugin(state, pluginId, { ...installed, enabled: true });
    return { ok: true, state: appendAudit(next, "enable", pluginId) };
  }

  if (action.type === "disable") {
    if (installed.manifest.protected) return failure(state, "plugin_protected");
    if (!installed.enabled) return { ok: true, state };
    const next = withPlugin(state, pluginId, { ...installed, enabled: false });
    return { ok: true, state: appendAudit(next, "disable", pluginId) };
  }

  if (action.type === "update") {
    if (!validatePluginManifest(action.manifest, state.kernelVersion).ok) {
      return failure(state, "manifest_invalid");
    }
    if (compareVersions(action.manifest.version, installed.manifest.version) <= 0) {
      return failure(state, "version_not_newer");
    }
    const next = withPlugin(state, pluginId, {
      ...installed,
      manifest: action.manifest,
      previousVersions: [...installed.previousVersions, installed.manifest],
    });
    return {
      ok: true,
      state: appendAudit(next, "update", pluginId, {
        fromVersion: installed.manifest.version,
        toVersion: action.manifest.version,
      }),
    };
  }

  if (action.type === "rollback") {
    const previous = installed.previousVersions.at(-1);
    if (!previous) return failure(state, "rollback_unavailable");
    const next = withPlugin(state, pluginId, {
      ...installed,
      manifest: previous,
      previousVersions: installed.previousVersions.slice(0, -1),
    });
    return {
      ok: true,
      state: appendAudit(next, "rollback", pluginId, {
        fromVersion: installed.manifest.version,
        toVersion: previous.version,
      }),
    };
  }

  if (installed.manifest.protected) return failure(state, "plugin_protected");
  const plugins = { ...state.plugins };
  delete plugins[pluginId];
  return {
    ok: true,
    state: appendAudit({ ...state, plugins }, "uninstall", pluginId, {
      fromVersion: installed.manifest.version,
    }),
  };
};

