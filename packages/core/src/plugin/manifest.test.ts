import { describe, expect, it } from "vitest";
import { validatePluginManifest } from "./manifest";
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
});

