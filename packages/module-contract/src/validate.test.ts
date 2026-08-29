import fixture from "../fixtures/ai-node.json" with { type: "json" };
import deviceInfo from "../fixtures/device-info.json" with { type: "json" };
import { describe, expect, it } from "vitest";
import {
  validateModuleManifest,
  type ModuleHostContext,
  type ModuleValidationResult,
} from "./validate";

const host = {
  kernelVersion: "0.1.0",
  androidSdk: 36,
  abis: ["arm64-v8a"],
  availableRuntimes: ["builtin"],
} satisfies ModuleHostContext;

const expectError = (
  result: ModuleValidationResult,
  code: string,
  path: string,
) => {
  expect(result.ok).toBe(false);
  expect(result.errors).toContainEqual(expect.objectContaining({ code, path }));
};

describe("validateModuleManifest", () => {
  it("accepts device info as an ordinary module without AI or networking", () => {
    expect(validateModuleManifest(deviceInfo, host)).toEqual({ ok: true, errors: [] });
    expect(deviceInfo.permissions).toEqual(["device.read"]);
    expect(deviceInfo.service).toBeNull();
    expect(deviceInfo.protected).toBe(false);
  });
  it("accepts the built-in AI node fixture", () => {
    expect(validateModuleManifest(fixture, host)).toEqual({ ok: true, errors: [] });
  });

  it("rejects a missing immutable module id", () => {
    const { id: _id, ...invalid } = fixture;

    expectError(validateModuleManifest(invalid, host), "schema.required", "/id");
  });

  it("rejects unknown permissions", () => {
    const invalid = {
      ...fixture,
      permissions: [...fixture.permissions, "device.write"],
    };

    expectError(
      validateModuleManifest(invalid, host),
      "schema.enum",
      "/permissions/4",
    );
  });

  it("rejects a GitHub source without an immutable revision", () => {
    const invalid = {
      ...fixture,
      source: {
        kind: "github",
        repository: "https://github.com/OpenDeviceForge/ai-node",
      },
    };

    expectError(
      validateModuleManifest(invalid, host),
      "schema.required",
      "/source/revision",
    );
  });

  it("rejects a package integrity record without hashes and signature", () => {
    const invalid = {
      ...fixture,
      integrity: {
        ...fixture.integrity,
        kind: "package",
        sha256: null,
        publisherSignature: null,
      },
    };
    const result = validateModuleManifest(invalid, host);

    expectError(result, "schema.type", "/integrity/sha256");
    expectError(result, "schema.type", "/integrity/publisherSignature");
  });

  it("rejects a kernel version outside the supported interval", () => {
    expectError(
      validateModuleManifest(fixture, { ...host, kernelVersion: "1.0.0" }),
      "kernel_incompatible",
      "/kernel",
    );
  });

  it("rejects an Android SDK below the declared minimum", () => {
    expectError(
      validateModuleManifest(fixture, { ...host, androidSdk: 27 }),
      "android_sdk_incompatible",
      "/platform/android/minSdk",
    );
  });

  it("rejects a host without every required ABI", () => {
    expectError(
      validateModuleManifest(fixture, { ...host, abis: ["x86_64"] }),
      "abi_incompatible",
      "/platform/android/abis",
    );
  });

  it("rejects a runtime unavailable on the host", () => {
    expectError(
      validateModuleManifest(fixture, { ...host, availableRuntimes: ["sandbox"] }),
      "runtime_unavailable",
      "/runtime/kind",
    );
  });

  it("rejects a contribution in an incompatible placement", () => {
    const invalid = {
      ...fixture,
      contributes: fixture.contributes.map((contribution, index) =>
        index === 0 ? { ...contribution, defaultPlacement: "sidebar" } : contribution,
      ),
    };

    expectError(
      validateModuleManifest(invalid, host),
      "placement_mismatch",
      "/contributes/0/defaultPlacement",
    );
  });

  it("rejects a high-risk module aimed at the general audience", () => {
    const invalid = { ...fixture, risk: "high", audience: "general" };

    expectError(
      validateModuleManifest(invalid, host),
      "risk_audience_mismatch",
      "/audience",
    );
  });

  it("rejects unknown fields instead of silently accepting them", () => {
    const invalid = { ...fixture, undocumentedCapability: true };

    expectError(
      validateModuleManifest(invalid, host),
      "schema.additionalProperties",
      "/undocumentedCapability",
    );
  });
});
