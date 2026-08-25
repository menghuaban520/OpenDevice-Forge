import { createHash, generateKeyPairSync, sign } from "node:crypto";
import fixture from "../fixtures/ai-node.json" with { type: "json" };
import { describe, expect, it } from "vitest";
import type { ModuleManifestV1 } from "./generated/module-manifest";
import { verifyPackageIntegrity } from "./integrity";

const sha256 = (value: Uint8Array) =>
  createHash("sha256").update(value).digest("hex");

const createSignedPackage = () => {
  const packageBytes = Buffer.from("OpenDevice signed module package", "utf8");
  const { privateKey, publicKey } = generateKeyPairSync("ed25519");
  const publicKeySpki = publicKey.export({ type: "spki", format: "der" });
  const packageSha256 = sha256(packageBytes);
  const publisherKeySha256 = sha256(publicKeySpki);
  const payload = Buffer.from(
    `${fixture.id}\n${fixture.version}\n${packageSha256}\n`,
    "utf8",
  );
  const publisherSignature = sign(null, payload, privateKey).toString("base64");
  const manifest = {
    ...fixture,
    integrity: {
      kind: "package",
      sha256: packageSha256,
      publisherKeySha256,
      publisherSignature,
      rollbackVersion: null,
    },
  } as unknown as ModuleManifestV1;

  return { manifest, packageBytes, publicKeySpki };
};

describe("verifyPackageIntegrity", () => {
  it("verifies the package hash, publisher key, and Ed25519 signature", () => {
    const signed = createSignedPackage();

    expect(
      verifyPackageIntegrity(
        signed.manifest,
        signed.packageBytes,
        signed.publicKeySpki,
      ),
    ).toEqual({ status: "verified" });
  });

  it("does not treat a host APK manifest as a package", () => {
    expect(
      verifyPackageIntegrity(
        fixture as unknown as ModuleManifestV1,
        Buffer.from("unused"),
        Buffer.from("unused"),
      ),
    ).toEqual({ status: "not_package" });
  });

  it("detects independently corrupted package bytes", () => {
    const signed = createSignedPackage();

    expect(
      verifyPackageIntegrity(
        signed.manifest,
        Buffer.from("corrupted package"),
        signed.publicKeySpki,
      ),
    ).toEqual({ status: "hash_mismatch" });
  });

  it("detects a different publisher key", () => {
    const signed = createSignedPackage();
    const { publicKey } = generateKeyPairSync("ed25519");
    const wrongKey = publicKey.export({ type: "spki", format: "der" });

    expect(
      verifyPackageIntegrity(signed.manifest, signed.packageBytes, wrongKey),
    ).toEqual({ status: "publisher_key_mismatch" });
  });

  it("returns a mismatch for attacker-controlled signature bytes without throwing", () => {
    const signed = createSignedPackage();
    const invalid = {
      ...signed.manifest,
      integrity: {
        ...signed.manifest.integrity,
        publisherSignature: "not-valid-base64***",
      },
    };

    expect(() =>
      verifyPackageIntegrity(invalid, signed.packageBytes, signed.publicKeySpki),
    ).not.toThrow();
    expect(
      verifyPackageIntegrity(invalid, signed.packageBytes, signed.publicKeySpki),
    ).toEqual({ status: "signature_mismatch" });
  });
});
