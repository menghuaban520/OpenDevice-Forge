import { describe, expect, it } from "vitest";
import {
  applyPluginAction,
  createPluginRegistry,
  isPluginEffective,
} from "./registry";
import type { PluginManifest, PluginRegistryState } from "./types";

const plugin = (version = "0.1.0", id = "dev.example.plugin"): PluginManifest => ({
  manifestVersion: "1",
  id,
  name: "Example",
  summary: "Example declarative plugin",
  version,
  publisher: "Example",
  kernel: { min: "0.1.0", maxExclusive: "1.0.0" },
  source: { kind: "community", catalog: "forge.community" },
  execution: "declarative",
  audience: "general",
  risk: "low",
  permissions: ["device.read"],
  contributes: [
    { id: "example", type: "navigation", label: "Example", defaultPlacement: "sidebar" },
  ],
});

const apply = (
  state: PluginRegistryState,
  action: Parameters<typeof applyPluginAction>[1],
) => applyPluginAction(state, action).state;

describe("plugin registry", () => {
  it("installs disabled, then enables and disables with a deterministic audit log", () => {
    let state = createPluginRegistry();
    state = apply(state, { type: "install", manifest: plugin() });
    expect(state.plugins["dev.example.plugin"]?.enabled).toBe(false);

    state = apply(state, { type: "enable", pluginId: "dev.example.plugin" });
    expect(isPluginEffective(state, "dev.example.plugin")).toBe(true);

    state = apply(state, { type: "disable", pluginId: "dev.example.plugin" });
    expect(isPluginEffective(state, "dev.example.plugin")).toBe(false);
    expect(state.audit.map((event) => [event.sequence, event.action])).toEqual([
      [1, "install"],
      [2, "enable"],
      [3, "disable"],
    ]);
  });

  it("updates and rolls back without losing the enabled state", () => {
    let state = createPluginRegistry();
    state = apply(state, { type: "install", manifest: plugin("0.1.0") });
    state = apply(state, { type: "enable", pluginId: "dev.example.plugin" });
    state = apply(state, { type: "update", manifest: plugin("0.2.0") });
    expect(state.plugins["dev.example.plugin"]?.manifest.version).toBe("0.2.0");

    state = apply(state, { type: "rollback", pluginId: "dev.example.plugin" });
    expect(state.plugins["dev.example.plugin"]?.manifest.version).toBe("0.1.0");
    expect(state.plugins["dev.example.plugin"]?.enabled).toBe(true);
  });

  it("uninstalls ordinary plugins but protects the kernel plugin manager", () => {
    const protectedPlugin = { ...plugin("0.1.0", "kernel.plugin-manager"), protected: true };
    let state = createPluginRegistry([protectedPlugin]);
    const result = applyPluginAction(state, {
      type: "uninstall",
      pluginId: "kernel.plugin-manager",
    });
    expect(result.ok).toBe(false);
    expect(result.error).toBe("plugin_protected");

    state = apply(state, { type: "install", manifest: plugin() });
    state = apply(state, { type: "uninstall", pluginId: "dev.example.plugin" });
    expect(state.plugins["dev.example.plugin"]).toBeUndefined();
  });

  it("safe mode makes only protected plugins effective and restores prior choices", () => {
    const protectedPlugin = { ...plugin("0.1.0", "kernel.plugin-manager"), protected: true };
    let state = createPluginRegistry([protectedPlugin]);
    state = apply(state, { type: "install", manifest: plugin() });
    state = apply(state, { type: "enable", pluginId: "dev.example.plugin" });
    state = apply(state, { type: "setSafeMode", enabled: true });

    expect(isPluginEffective(state, "kernel.plugin-manager")).toBe(true);
    expect(isPluginEffective(state, "dev.example.plugin")).toBe(false);

    state = apply(state, { type: "setSafeMode", enabled: false });
    expect(isPluginEffective(state, "dev.example.plugin")).toBe(true);
  });
});

