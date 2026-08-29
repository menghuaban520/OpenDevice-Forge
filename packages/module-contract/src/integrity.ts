import {
  createHash,
  createPublicKey,
  timingSafeEqual,
  verify,
} from "node:crypto";
import type {
  ModuleManifestV1,
  Permission,
  RuntimeKind,
  SourceKind,
} from "./generated/module-manifest";
import {
  validateModuleManifest,
  type ModuleHostContext,
  type ModuleValidationError,
} from "./validate";

const SIGNATURE_DOMAIN = Buffer.from("OpenDevice Module Package v1\0", "utf8");
const DEFAULT_MAX_MANIFEST_BYTES = 256 * 1024;
const DEFAULT_MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;
const SEMVER = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?$/;
const REMOTE_SOURCE_KINDS = new Set<SourceKind>([
  "official",
  "community",
  "github",
]);

export interface ModulePackageComponents {
  /** Canonical UTF-8 JSON with integrity.publisherSignature set to null. */
  manifestBytes: Uint8Array;
  payloadBytes: Uint8Array;
  publisherPublicKeySpki: Uint8Array;
  /** ASN.1 DER ECDSA P-256 signature over createModulePackageSignaturePayload(). */
  publisherSignature: Uint8Array;
}

export interface ModulePackageLimits {
  maxManifestBytes: number;
  maxPayloadBytes: number;
}

export interface ModulePackageVerificationOptions {
  host: ModuleHostContext;
  trustedPublisherKeySha256: ReadonlySet<string>;
  limits?: ModulePackageLimits;
  allowedRuntimes?: ReadonlySet<RuntimeKind>;
  allowedSources?: ReadonlySet<SourceKind>;
}

export type ModulePackageVerificationResult =
  | {
      status: "verified";
      manifest: ModuleManifestV1;
      publisherKeySha256: string;
    }
  | {
      status:
        | "package_too_large"
        | "manifest_encoding_invalid"
        | "manifest_json_invalid"
        | "manifest_not_canonical"
        | "manifest_invalid"
        | "not_package"
        | "signature_not_detached"
        | "hash_mismatch"
        | "publisher_key_mismatch"
        | "publisher_key_invalid"
        | "signature_mismatch"
        | "untrusted_publisher"
        | "source_forbidden"
        | "runtime_forbidden"
        | "protected_package_forbidden";
      errors?: ModuleValidationError[];
    };

export type PackageIntegrityResult =
  | { status: "verified" }
  | { status: "not_package" }
  | { status: "hash_mismatch" }
  | { status: "publisher_key_mismatch" }
  | { status: "signature_mismatch" };

export type ModulePackageInstallPlan =
  | {
      status: "ready";
      kind: "install" | "update";
      enabledAfterApply: false;
      addedPermissions: Permission[];
      runtimeChanged: boolean;
      requiresReview: boolean;
    }
  | {
      status: "rejected";
      reason:
        | "identity_mismatch"
        | "version_not_newer"
        | "rollback_version_mismatch";
    };

const sha256 = (value: Uint8Array) =>
  createHash("sha256").update(value).digest("hex");

const equalBytes = (left: Uint8Array, right: Uint8Array): boolean =>
  left.byteLength === right.byteLength &&
  timingSafeEqual(Buffer.from(left), Buffer.from(right));

const canonicalString = (value: string): string => {
  for (let index = 0; index < value.length; index += 1) {
    const codeUnit = value.charCodeAt(index);
    if (codeUnit >= 0xd800 && codeUnit <= 0xdbff) {
      const next = value.charCodeAt(index + 1);
      if (!(next >= 0xdc00 && next <= 0xdfff)) {
        throw new TypeError("unpaired JSON surrogate");
      }
      index += 1;
    } else if (codeUnit >= 0xdc00 && codeUnit <= 0xdfff) {
      throw new TypeError("unpaired JSON surrogate");
    }
  }
  return JSON.stringify(value);
};

const canonicalNumber = (value: number): string => {
  if (!Number.isFinite(value)) throw new TypeError("non-finite JSON number");
  if (Object.is(value, -0)) return "0";
  const serialized = value.toString();
  if (!/[eE]/.test(serialized)) return serialized;
  const match = /^(-?)(\d+)(?:\.(\d+))?[eE]([+-]?\d+)$/.exec(serialized);
  if (!match) throw new TypeError("invalid JSON number");
  const sign = match[1] ?? "";
  const integer = match[2] ?? "";
  const fraction = match[3] ?? "";
  const exponent = Number(match[4]);
  const digits = integer + fraction;
  const decimalPosition = integer.length + exponent;
  if (decimalPosition <= 0) {
    return `${sign}0.${"0".repeat(-decimalPosition)}${digits}`;
  }
  if (decimalPosition >= digits.length) {
    return `${sign}${digits}${"0".repeat(decimalPosition - digits.length)}`;
  }
  return `${sign}${digits.slice(0, decimalPosition)}.${digits.slice(decimalPosition)}`;
};

const canonicalJson = (value: unknown): string => {
  if (value === null) return "null";
  if (typeof value === "string") return canonicalString(value);
  if (typeof value === "boolean") return JSON.stringify(value);
  if (typeof value === "number") return canonicalNumber(value);
  if (Array.isArray(value)) {
    return `[${value.map(canonicalJson).join(",")}]`;
  }
  if (typeof value === "object") {
    const record = value as Record<string, unknown>;
    return `{${Object.keys(record)
      .sort()
      .map((key) => `${canonicalString(key)}:${canonicalJson(record[key])}`)
      .join(",")}}`;
  }
  throw new TypeError("value is not JSON");
};

export const canonicalizeUnsignedManifest = (
  manifest: ModuleManifestV1,
): Uint8Array => {
  const unsigned: ModuleManifestV1 = {
    ...manifest,
    integrity: {
      ...manifest.integrity,
      publisherSignature: null,
    },
  };
  return Buffer.from(canonicalJson(unsigned), "utf8");
};

export const createModulePackageSignaturePayload = (
  canonicalUnsignedManifestBytes: Uint8Array,
): Uint8Array =>
  Buffer.concat([
    SIGNATURE_DOMAIN,
    Buffer.from(canonicalUnsignedManifestBytes),
  ]);

const parseVersion = (version: string): [number, number, number] | undefined => {
  const match = SEMVER.exec(version);
  if (!match) return undefined;
  return [Number(match[1]), Number(match[2]), Number(match[3])];
};

const compareVersions = (left: string, right: string): number => {
  const a = parseVersion(left);
  const b = parseVersion(right);
  if (!a || !b) return Number.NaN;
  for (let index = 0; index < 3; index += 1) {
    const difference = (a[index] ?? 0) - (b[index] ?? 0);
    if (difference !== 0) return difference;
  }
  return 0;
};

const isRemoteHttpsSource = (manifest: ModuleManifestV1): boolean => {
  if (!REMOTE_SOURCE_KINDS.has(manifest.source.kind)) return false;
  if (!manifest.source.repository || !manifest.source.revision) return false;
  try {
    return new URL(manifest.source.repository).protocol === "https:";
  } catch {
    return false;
  }
};

const createVerifiedPublicKey = (spki: Uint8Array) => {
  const key = createPublicKey({
    key: Buffer.from(spki),
    format: "der",
    type: "spki",
  });
  if (
    key.asymmetricKeyType !== "ec" ||
    key.asymmetricKeyDetails?.namedCurve !== "prime256v1"
  ) {
    throw new TypeError("publisher key must be ECDSA P-256");
  }
  return key;
};

export const verifyModulePackage = (
  components: ModulePackageComponents,
  options: ModulePackageVerificationOptions,
): ModulePackageVerificationResult => {
  const limits = options.limits ?? {
    maxManifestBytes: DEFAULT_MAX_MANIFEST_BYTES,
    maxPayloadBytes: DEFAULT_MAX_PAYLOAD_BYTES,
  };
  if (
    components.manifestBytes.byteLength === 0 ||
    components.manifestBytes.byteLength > limits.maxManifestBytes ||
    components.payloadBytes.byteLength > limits.maxPayloadBytes
  ) {
    return { status: "package_too_large" };
  }

  let manifestText: string;
  try {
    manifestText = new TextDecoder("utf-8", { fatal: true }).decode(
      components.manifestBytes,
    );
  } catch {
    return { status: "manifest_encoding_invalid" };
  }

  let unsignedValue: unknown;
  try {
    unsignedValue = JSON.parse(manifestText);
  } catch {
    return { status: "manifest_json_invalid" };
  }
  try {
    if (
      !equalBytes(
        components.manifestBytes,
        Buffer.from(canonicalJson(unsignedValue), "utf8"),
      )
    ) {
      return { status: "manifest_not_canonical" };
    }
  } catch {
    return { status: "manifest_json_invalid" };
  }

  if (!unsignedValue || typeof unsignedValue !== "object") {
    return { status: "manifest_invalid" };
  }
  const unsigned = unsignedValue as ModuleManifestV1;
  if (unsigned.integrity?.kind !== "package") return { status: "not_package" };
  if (unsigned.integrity.publisherSignature !== null) {
    return { status: "signature_not_detached" };
  }

  if (
    typeof unsigned.integrity.sha256 !== "string" ||
    sha256(components.payloadBytes) !== unsigned.integrity.sha256
  ) {
    return { status: "hash_mismatch" };
  }
  const publisherKeySha256 = sha256(components.publisherPublicKeySpki);
  if (
    typeof unsigned.integrity.publisherKeySha256 !== "string" ||
    publisherKeySha256 !== unsigned.integrity.publisherKeySha256
  ) {
    return { status: "publisher_key_mismatch" };
  }

  const allowedSources =
    options.allowedSources ?? new Set<SourceKind>(REMOTE_SOURCE_KINDS);
  if (
    !allowedSources.has(unsigned.source?.kind) ||
    !isRemoteHttpsSource(unsigned)
  ) {
    return { status: "source_forbidden" };
  }
  const allowedRuntimes =
    options.allowedRuntimes ?? new Set<RuntimeKind>(["declarative"]);
  if (!allowedRuntimes.has(unsigned.runtime?.kind)) {
    return { status: "runtime_forbidden" };
  }
  if (unsigned.protected !== false) {
    return { status: "protected_package_forbidden" };
  }
  if (!options.trustedPublisherKeySha256.has(publisherKeySha256)) {
    return { status: "untrusted_publisher" };
  }

  try {
    const publicKey = createVerifiedPublicKey(components.publisherPublicKeySpki);
    if (
      !verify(
        "sha256",
        createModulePackageSignaturePayload(components.manifestBytes),
        publicKey,
        components.publisherSignature,
      )
    ) {
      return { status: "signature_mismatch" };
    }
  } catch {
    return { status: "publisher_key_invalid" };
  }

  const manifest: ModuleManifestV1 = {
    ...unsigned,
    integrity: {
      ...unsigned.integrity,
      publisherSignature: Buffer.from(
        components.publisherSignature,
      ).toString("base64"),
    },
  };
  const validation = validateModuleManifest(manifest, options.host);
  if (!validation.ok) {
    return { status: "manifest_invalid", errors: validation.errors };
  }
  return { status: "verified", manifest, publisherKeySha256 };
};

const decodeBase64 = (value: string): Uint8Array | undefined => {
  if (
    value.length === 0 ||
    value.length % 4 !== 0 ||
    !/^[A-Za-z0-9+/]+={0,2}$/.test(value)
  ) {
    return undefined;
  }
  const bytes = Buffer.from(value, "base64");
  return bytes.toString("base64") === value ? bytes : undefined;
};

/**
 * Backward-compatible cryptographic check. New install paths should use
 * verifyModulePackage so trust, compatibility, source, runtime and size policy
 * are all enforced together.
 */
export const verifyPackageIntegrity = (
  manifest: ModuleManifestV1,
  packageBytes: Uint8Array,
  publisherPublicKeySpki: Uint8Array,
): PackageIntegrityResult => {
  if (manifest.integrity.kind !== "package") return { status: "not_package" };
  if (
    typeof manifest.integrity.sha256 !== "string" ||
    sha256(packageBytes) !== manifest.integrity.sha256
  ) {
    return { status: "hash_mismatch" };
  }
  if (
    typeof manifest.integrity.publisherKeySha256 !== "string" ||
    sha256(publisherPublicKeySpki) !== manifest.integrity.publisherKeySha256
  ) {
    return { status: "publisher_key_mismatch" };
  }
  const signature =
    typeof manifest.integrity.publisherSignature === "string"
      ? decodeBase64(manifest.integrity.publisherSignature)
      : undefined;
  if (!signature) return { status: "signature_mismatch" };
  try {
    const publicKey = createVerifiedPublicKey(publisherPublicKeySpki);
    const unsignedManifestBytes = canonicalizeUnsignedManifest(manifest);
    return verify(
      "sha256",
      createModulePackageSignaturePayload(unsignedManifestBytes),
      publicKey,
      signature,
    )
      ? { status: "verified" }
      : { status: "signature_mismatch" };
  } catch {
    return { status: "signature_mismatch" };
  }
};

export const planModulePackageInstall = (
  current: ModuleManifestV1 | undefined,
  candidate: ModuleManifestV1,
): ModulePackageInstallPlan => {
  if (current && current.id !== candidate.id) {
    return { status: "rejected", reason: "identity_mismatch" };
  }
  if (!current) {
    return {
      status: "ready",
      kind: "install",
      enabledAfterApply: false,
      addedPermissions: [...candidate.permissions],
      runtimeChanged: true,
      requiresReview: true,
    };
  }
  if (compareVersions(candidate.version, current.version) <= 0) {
    return { status: "rejected", reason: "version_not_newer" };
  }
  if (candidate.integrity.rollbackVersion !== current.version) {
    return { status: "rejected", reason: "rollback_version_mismatch" };
  }

  const currentPermissions = new Set(current.permissions);
  const addedPermissions = candidate.permissions.filter(
    (permission) => !currentPermissions.has(permission),
  );
  const runtimeChanged =
    candidate.runtime.kind !== current.runtime.kind ||
    candidate.runtime.entry !== current.runtime.entry ||
    candidate.runtime.companionPackage !== current.runtime.companionPackage;
  const publisherChanged =
    candidate.integrity.publisherKeySha256 !==
    current.integrity.publisherKeySha256;
  return {
    status: "ready",
    kind: "update",
    enabledAfterApply: false,
    addedPermissions,
    runtimeChanged,
    requiresReview:
      addedPermissions.length > 0 ||
      runtimeChanged ||
      publisherChanged ||
      candidate.risk !== current.risk,
  };
};
