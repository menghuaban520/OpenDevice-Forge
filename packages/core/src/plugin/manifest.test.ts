import { describe, expect, it } from "vitest";
import fixture from "../../../module-contract/fixtures/ai-node.json" with {
  type: "json",
};
import {
  validateModuleManifest,
  type ModuleManifestV1,
} from "@opendevice/module-contract";
import {
  moduleManifestToLegacyPlugin,
  validatePluginManifest,
} from "./manifest";
import type { PluginManifest } from "./types";

const validManifest: PluginManifest = {
  manifestVersion: "1",
  id: "dev.opendevice.ai-readiness",
  name: "AI 节点就绪度",
  summary: "读取设备能力并解释适合的轻量模型范围。",
  version: "0.1.0",
  publisher: "OpenDevice Forge",
  kernel: { min: "0.1.0", maxExclusive: "1.0.0" },
  source: { kind: "official" },
  execution: "declarative",
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
};

describe("validatePluginManifest", () => {
  it("accepts a compatible declarative manifest", () => {
    expect(validatePluginManifest(validManifest, "0.1.0")).toEqual({
      ok: true,
      errors: [],
    });
  });

  it("rejects a GitHub source that is not pinned to a release or commit", () => {
    const result = validatePluginManifest(
      {
        ...validManifest,
        source: {
          kind: "github",
          repository: "https://github.com/OpenDeviceForge/ai-readiness",
        },
      },
      "0.1.0",
    );

    expect(result.errors).toContainEqual(
      expect.objectContaining({ code: "source_unpinned" }),
    );
  });

  it("rejects native execution from community and GitHub sources", () => {
    for (const source of [
      { kind: "community", catalog: "forge.community" } as const,
      {
        kind: "github",
        repository: "https://github.com/example/plugin",
        commit: "4f7c2c1",
      } as const,
    ]) {
      const result = validatePluginManifest(
        { ...validManifest, source, execution: "native", risk: "high" },
        "0.1.0",
      );
      expect(result.errors).toContainEqual(
        expect.objectContaining({ code: "untrusted_native_execution" }),
      );
    }
  });

  it("rejects unknown permissions and invalid contribution placements", () => {
    const result = validatePluginManifest(
      {
        ...validManifest,
        permissions: ["device.read", "device.write" as "device.read"],
        contributes: [
          {
            id: "bad-service",
            type: "service",
            label: "Bad",
            defaultPlacement: "sidebar",
          },
        ],
      },
      "0.1.0",
    );

    expect(result.errors.map((error) => error.code)).toEqual(
      expect.arrayContaining(["permission_unknown", "placement_mismatch"]),
    );
  });

  it("rejects incompatible kernel versions and high-risk general plugins", () => {
    const result = validatePluginManifest(
      {
        ...validManifest,
        kernel: { min: "0.2.0", maxExclusive: "1.0.0" },
        risk: "high",
        audience: "general",
      },
      "0.1.0",
    );

    expect(result.errors.map((error) => error.code)).toEqual(
      expect.arrayContaining(["kernel_incompatible", "risk_audience_mismatch"]),
    );
  });

  it("rejects a service runtime without a service contribution", () => {
    const result = validatePluginManifest(
      {
        ...validManifest,
        runtime: { kind: "service", entry: "builtin:gateway" },
      },
      "0.1.0",
    );

    expect(result.errors).toContainEqual(
      expect.objectContaining({ code: "runtime_contribution_mismatch" }),
    );
  });

  it("rejects an unsafe runtime entry name", () => {
    const result = validatePluginManifest(
      {
        ...validManifest,
        runtime: { kind: "workflow", entry: "../outside runtime" },
      },
      "0.1.0",
    );

    expect(result.errors).toContainEqual(
      expect.objectContaining({ code: "runtime_invalid" }),
    );
  });

  it("adapts the canonical AI module without weakening the legacy validator", () => {
    const moduleManifest = fixture as unknown as ModuleManifestV1;
    expect(
      validateModuleManifest(moduleManifest, {
        kernelVersion: "0.1.0",
        androidSdk: 36,
        abis: ["arm64-v8a"],
        availableRuntimes: ["builtin"],
      }),
    ).toEqual({ ok: true, errors: [] });

    const legacy = moduleManifestToLegacyPlugin(moduleManifest);
    expect(legacy).toMatchObject({
      id: "dev.opendevice.module.ai-node",
      source: { kind: "official" },
      execution: "native",
      permissions: ["device.read", "network.outbound", "service.local"],
      runtime: { kind: "service", entry: "ai-node" },
      protected: false,
    });
    expect(validatePluginManifest(legacy, "0.1.0")).toEqual({
      ok: true,
      errors: [],
    });
  });

  it("maps the OpenAI-compatible capability to a legacy service contribution", () => {
    const moduleManifest = {
      ...fixture,
      contributes: fixture.contributes.filter(
        (contribution) => contribution.type !== "service",
      ),
    } as unknown as ModuleManifestV1;

    expect(moduleManifestToLegacyPlugin(moduleManifest).contributes).toContainEqual({
      id: "dev.opendevice.module.ai-node.openai-compatible",
      type: "service",
      label: "OpenAI 兼容接口",
      defaultPlacement: "service",
    });
  });
});
