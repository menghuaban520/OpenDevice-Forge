import { createHash, generateKeyPairSync, sign } from "node:crypto";
import fixture from "../fixtures/device-info.json" with { type: "json" };
import interopFixture from "../fixtures/signed-device-info-v1.json" with { type: "json" };
import { describe, expect, it } from "vitest";
import type { ModuleManifestV1 } from "./generated/module-manifest";
import {
  canonicalizeUnsignedManifest,
  createModulePackageSignaturePayload,
  planModulePackageInstall,
  verifyModulePackage,
  type ModulePackageComponents,
} from "./integrity";

const host = {
  kernelVersion: "0.1.0",
  androidSdk: 29,
  abis: ["arm64-v8a" as const],
  availableRuntimes: ["declarative" as const],
};

const sha256 = (value: Uint8Array) =>
  createHash("sha256").update(value).digest("hex");

const signedPackage = (
  mutate: (manifest: ModuleManifestV1) => ModuleManifestV1 = (manifest) => manifest,
) => {
  const payloadBytes = Buffer.from("OpenDevice declarative module payload", "utf8");
  const { privateKey, publicKey } = generateKeyPairSync("ec", {
    namedCurve: "prime256v1",
  });
  const publisherPublicKeySpki = publicKey.export({ type: "spki", format: "der" });
  const unsigned = mutate({
    ...(fixture as unknown as ModuleManifestV1),
    source: {
      kind: "official",
      repository: "https://github.com/opendevice-forge/modules",
      revision: "release-v0.1.0",
    },
    runtime: {
      ...(fixture.runtime as ModuleManifestV1["runtime"]),
      kind: "declarative",
      entry: "device-info.json",
    },
    protected: false,
    integrity: {
      kind: "package",
      sha256: sha256(payloadBytes),
      publisherKeySha256: sha256(publisherPublicKeySpki),
      publisherSignature: null,
      rollbackVersion: null,
    },
  });
  const manifestBytes = canonicalizeUnsignedManifest(unsigned);
  const publisherSignature = sign(
    "sha256",
    createModulePackageSignaturePayload(manifestBytes),
    privateKey,
  );
  const components: ModulePackageComponents = {
    manifestBytes,
    payloadBytes,
    publisherPublicKeySpki,
    publisherSignature,
  };

  return {
    components,
    keySha256: sha256(publisherPublicKeySpki),
  };
};

const verify = (
  components: ModulePackageComponents,
  trustedPublisherKeySha256: ReadonlySet<string>,
) =>
  verifyModulePackage(components, {
    host,
    trustedPublisherKeySha256,
  });

describe("verifyModulePackage", () => {
  it("verifies the shared Node-signed interoperability fixture", () => {
    const components: ModulePackageComponents = {
      manifestBytes: Buffer.from(interopFixture.manifest, "utf8"),
      payloadBytes: Buffer.from(interopFixture.payloadBase64, "base64"),
      publisherPublicKeySpki: Buffer.from(
        interopFixture.publisherPublicKeySpkiBase64,
        "base64",
      ),
      publisherSignature: Buffer.from(
        interopFixture.publisherSignatureBase64,
        "base64",
      ),
    };
    const parsed = JSON.parse(interopFixture.manifest) as ModuleManifestV1;

    expect(
      verify(components, new Set([parsed.integrity.publisherKeySha256 ?? ""])).status,
    ).toBe("verified");
  });

  it("verifies a canonical full-manifest ECDSA package from a trusted publisher", () => {
    const signed = signedPackage();

    const result = verify(signed.components, new Set([signed.keySha256]));

    expect(result.status).toBe("verified");
    if (result.status !== "verified") return;
    expect(result.manifest.integrity.publisherSignature).toBe(
      Buffer.from(signed.components.publisherSignature).toString("base64"),
    );
    expect(result.manifest.runtime.kind).toBe("declarative");
  });

  it("rejects permission tampering even when the payload hash is unchanged", () => {
    const signed = signedPackage();
    const parsed = JSON.parse(
      new TextDecoder().decode(signed.components.manifestBytes),
    ) as ModuleManifestV1;
    parsed.permissions = [...parsed.permissions, "network.outbound"];

    const result = verify(
      {
        ...signed.components,
        manifestBytes: canonicalizeUnsignedManifest(parsed),
      },
      new Set([signed.keySha256]),
    );

    expect(result.status).toBe("signature_mismatch");
  });

  it("rejects independently corrupted payload bytes", () => {
    const signed = signedPackage();

    const result = verify(
      {
        ...signed.components,
        payloadBytes: Buffer.from("corrupted"),
      },
      new Set([signed.keySha256]),
    );

    expect(result.status).toBe("hash_mismatch");
  });

  it("rejects a package signed by a key outside the explicit trust store", () => {
    const signed = signedPackage();

    expect(verify(signed.components, new Set()).status).toBe("untrusted_publisher");
  });

  it("rejects non-canonical or oversized manifest input before signature use", () => {
    const signed = signedPackage();
    const pretty = Buffer.from(
      JSON.stringify(
        JSON.parse(new TextDecoder().decode(signed.components.manifestBytes)),
        null,
        2,
      ),
    );

    expect(
      verify(
        { ...signed.components, manifestBytes: pretty },
        new Set([signed.keySha256]),
      ).status,
    ).toBe("manifest_not_canonical");
    expect(
      verifyModulePackage(signed.components, {
        host,
        trustedPublisherKeySha256: new Set([signed.keySha256]),
        limits: { maxManifestBytes: 16, maxPayloadBytes: 1024 },
      }).status,
    ).toBe("package_too_large");
    const abusiveNumber = Buffer.from(
      new TextDecoder()
        .decode(signed.components.manifestBytes)
        .replace('"minSdk":28', '"minSdk":1e1000000000'),
    );
    expect(
      verify(
        { ...signed.components, manifestBytes: abusiveNumber },
        new Set([signed.keySha256]),
      ).status,
    ).toBe("manifest_json_invalid");
  });

  it("uses cross-host decimal canonicalization and rejects unpaired surrogates", () => {
    const signed = signedPackage((manifest) => ({
      ...manifest,
      configuration: {
        fields: [
          {
            key: "threshold",
            label: "Threshold",
            type: "number",
            required: false,
            default: 1e-7,
            minimum: 1e-7,
            maximum: 1e21,
            pattern: null,
            options: [],
          },
        ],
      },
    }));
    const canonical = new TextDecoder().decode(signed.components.manifestBytes);

    expect(canonical).toContain('"default":0.0000001');
    expect(canonical).toContain('"maximum":1000000000000000000000');
    expect(() =>
      canonicalizeUnsignedManifest({
        ...(fixture as unknown as ModuleManifestV1),
        name: "broken\ud800",
      }),
    ).toThrow("unpaired JSON surrogate");
  });

  it("rejects external builtin code even when signed", () => {
    const signed = signedPackage((manifest) => ({
      ...manifest,
      runtime: { ...manifest.runtime, kind: "builtin" },
    }));

    expect(verify(signed.components, new Set([signed.keySha256])).status).toBe(
      "runtime_forbidden",
    );
  });

  it("never throws for attacker-controlled key or signature bytes", () => {
    const signed = signedPackage();
    const invalid = {
      ...signed.components,
      publisherPublicKeySpki: Buffer.from("not-a-key"),
      publisherSignature: Buffer.from("not-a-signature"),
    };

    expect(() => verify(invalid, new Set([signed.keySha256]))).not.toThrow();
    expect(verify(invalid, new Set([signed.keySha256])).status).toBe(
      "publisher_key_mismatch",
    );
  });
});

describe("planModulePackageInstall", () => {
  const verifiedManifest = () => {
    const signed = signedPackage();
    const result = verify(signed.components, new Set([signed.keySha256]));
    if (result.status !== "verified") throw new Error(result.status);
    return result.manifest;
  };

  it("installs new packages disabled and requires review of declared permissions", () => {
    const candidate = verifiedManifest();

    expect(planModulePackageInstall(undefined, candidate)).toEqual({
      status: "ready",
      kind: "install",
      enabledAfterApply: false,
      addedPermissions: candidate.permissions,
      runtimeChanged: true,
      requiresReview: true,
    });
  });

  it("accepts only a newer update that declares the installed rollback version", () => {
    const current = verifiedManifest();
    const candidate = {
      ...current,
      version: "0.2.0",
      integrity: { ...current.integrity, rollbackVersion: current.version },
    };

    expect(planModulePackageInstall(current, candidate)).toMatchObject({
      status: "ready",
      kind: "update",
      enabledAfterApply: false,
      addedPermissions: [],
    });
    expect(
      planModulePackageInstall(current, {
        ...candidate,
        integrity: { ...candidate.integrity, rollbackVersion: null },
      }),
    ).toEqual({ status: "rejected", reason: "rollback_version_mismatch" });
    expect(planModulePackageInstall(candidate, current)).toEqual({
      status: "rejected",
      reason: "version_not_newer",
    });
  });

  it("rejects an update that changes module identity", () => {
    const current = verifiedManifest();
    expect(
      planModulePackageInstall(current, { ...current, id: "dev.attacker.module" }),
    ).toEqual({ status: "rejected", reason: "identity_mismatch" });
  });
});
