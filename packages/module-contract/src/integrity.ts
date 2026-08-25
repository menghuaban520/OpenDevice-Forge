import { createHash, createPublicKey, verify } from "node:crypto";
import type { ModuleManifestV1 } from "./generated/module-manifest";

export type PackageIntegrityResult =
  | { status: "verified" }
  | { status: "not_package" }
  | { status: "hash_mismatch" }
  | { status: "publisher_key_mismatch" }
  | { status: "signature_mismatch" };

const sha256 = (value: Uint8Array) =>
  createHash("sha256").update(value).digest("hex");

export const verifyPackageIntegrity = (
  manifest: ModuleManifestV1,
  packageBytes: Uint8Array,
  publisherPublicKeySpki: Uint8Array,
): PackageIntegrityResult => {
  const integrity = manifest.integrity;
  if (integrity.kind !== "package") return { status: "not_package" };

  if (
    typeof integrity.sha256 !== "string" ||
    sha256(packageBytes) !== integrity.sha256
  ) {
    return { status: "hash_mismatch" };
  }

  if (
    typeof integrity.publisherKeySha256 !== "string" ||
    sha256(publisherPublicKeySpki) !== integrity.publisherKeySha256
  ) {
    return { status: "publisher_key_mismatch" };
  }

  if (typeof integrity.publisherSignature !== "string") {
    return { status: "signature_mismatch" };
  }

  try {
    const publicKey = createPublicKey({
      key: Buffer.from(publisherPublicKeySpki),
      format: "der",
      type: "spki",
    });
    const payload = Buffer.from(
      `${manifest.id}\n${manifest.version}\n${integrity.sha256}\n`,
      "utf8",
    );
    const signature = Buffer.from(integrity.publisherSignature, "base64");
    return verify(null, payload, publicKey, signature)
      ? { status: "verified" }
      : { status: "signature_mismatch" };
  } catch {
    return { status: "signature_mismatch" };
  }
};
