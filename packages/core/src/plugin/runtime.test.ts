import { describe, expect, it } from "vitest";
import { BUILTIN_PLUGINS } from "./builtins";
import {
  isPluginRunnable,
  validateRuntimeDefinition,
  type ServiceRuntimeDefinition,
  type WorkflowRuntimeDefinition,
} from "./runtime";

describe("plugin runtime contracts", () => {
  const inspection = BUILTIN_PLUGINS.find(
    (manifest) => manifest.id === "dev.opendevice.device-inspection",
  )!;

  it("requires a registered runtime whose plugin id and kind match the manifest", () => {
    const runtime: WorkflowRuntimeDefinition<{ sessionSerial: string }> = {
      pluginId: inspection.id,
      kind: "workflow",
      defaultConfiguration: { groups: ["identity"] },
      validate: () => ({}),
      execute: async () => ({ groups: [] }),
    };
    const runtimes = new Map([["builtin:device-inspection", runtime]]);

    expect(validateRuntimeDefinition(inspection, runtime)).toEqual({
      ok: true,
      errors: [],
    });
    expect(isPluginRunnable(inspection, runtimes)).toBe(true);
    expect(isPluginRunnable(inspection, new Map())).toBe(false);
  });

  it("rejects a service runtime that escapes the local package boundary", () => {
    const manifest = {
      ...inspection,
      id: "dev.example.local-service",
      runtime: { kind: "service" as const, entry: "builtin:local-service" },
    };
    const invalidRuntime = {
      pluginId: manifest.id,
      kind: "service",
      localOnly: false,
      fixedArguments: false,
      defaultHost: "0.0.0.0",
      launch: {
        packageEntry: "../outside/server",
        arguments: ["--port", "11434"],
      },
      lifecycle: ["start", "stop", "restart"],
      defaultConfiguration: {},
      validate: () => ({}),
    } as unknown as ServiceRuntimeDefinition;

    expect(validateRuntimeDefinition(manifest, invalidRuntime)).toEqual({
      ok: false,
      errors: [
        "service_not_local_only",
        "service_host_not_local",
        "service_arguments_not_fixed",
        "service_entry_not_package_relative",
      ],
    });

    const validRuntime: ServiceRuntimeDefinition = {
      pluginId: manifest.id,
      kind: "service",
      localOnly: true,
      fixedArguments: true,
      defaultHost: "127.0.0.1",
      launch: {
        packageEntry: "bin/model-server",
        arguments: ["--port", "11434"],
      },
      lifecycle: ["start", "stop", "restart"],
      defaultConfiguration: { port: 11434 },
      validate: () => ({}),
    };

    expect(validateRuntimeDefinition(manifest, validRuntime)).toEqual({
      ok: true,
      errors: [],
    });

    expect(
      validateRuntimeDefinition(manifest, {
        ...validRuntime,
        launch: {
          ...validRuntime.launch,
          arguments: ["--port\n11434"],
        },
      }),
    ).toEqual({ ok: false, errors: ["service_arguments_not_fixed"] });
  });

  it("rejects a registered runtime whose plugin identity or kind is wrong", () => {
    const wrongId: WorkflowRuntimeDefinition = {
      pluginId: "dev.example.wrong-plugin",
      kind: "workflow",
      defaultConfiguration: {},
      validate: () => ({}),
      execute: async () => ({ groups: [] }),
    };
    expect(validateRuntimeDefinition(inspection, wrongId)).toEqual({
      ok: false,
      errors: ["plugin_id_mismatch"],
    });

    const wrongKind = {
      pluginId: inspection.id,
      kind: "service",
      localOnly: true,
      fixedArguments: true,
      defaultHost: "127.0.0.1",
      launch: { packageEntry: "bin/worker", arguments: [] },
      lifecycle: ["start", "stop", "restart"],
      defaultConfiguration: {},
      validate: () => ({}),
    } as ServiceRuntimeDefinition;
    expect(validateRuntimeDefinition(inspection, wrongKind)).toEqual({
      ok: false,
      errors: ["runtime_kind_mismatch"],
    });
  });
});
