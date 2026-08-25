import Ajv2020, { type ErrorObject } from "ajv/dist/2020.js";
import schema from "../../core/schema/opendevice.module.v1.schema.json" with {
  type: "json",
};
import type {
  ContributeType,
  DefaultPlacement,
  ModuleManifestV1,
  RuntimeKind,
} from "./generated/module-manifest";

export interface ModuleHostContext {
  kernelVersion: string;
  androidSdk: number;
  abis: string[];
  availableRuntimes: RuntimeKind[];
}

export interface ModuleValidationError {
  code: string;
  path: string;
  message: string;
}

export type ModuleValidationResult =
  | { ok: true; errors: [] }
  | { ok: false; errors: ModuleValidationError[] };

const SEMVER = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?$/;

const PLACEMENT_RULES: Record<ContributeType, DefaultPlacement[]> = {
  navigation: ["sidebar"],
  overviewSection: ["overview"],
  overviewAction: ["overview", "shortcut"],
  configuration: ["context"],
  workflow: ["shortcut", "context"],
  service: ["service"],
  screen: ["sidebar", "fullscreen"],
  deviceControl: ["context", "fullscreen"],
  companionApp: ["service"],
  reportSection: ["report"],
};

const ajv = new Ajv2020({
  allErrors: true,
  strict: true,
  strictRequired: false,
  allowUnionTypes: true,
});
const validateSchema = ajv.compile<ModuleManifestV1>(schema);

const escapeJsonPointer = (value: string) =>
  value.replaceAll("~", "~0").replaceAll("/", "~1");

const schemaErrorPath = (error: ErrorObject): string => {
  if (error.keyword === "required") {
    const property = String(error.params.missingProperty ?? "");
    return `${error.instancePath}/${escapeJsonPointer(property)}`;
  }
  if (error.keyword === "additionalProperties") {
    const property = String(error.params.additionalProperty ?? "");
    return `${error.instancePath}/${escapeJsonPointer(property)}`;
  }
  return error.instancePath || "/";
};

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

const validateSemanticCompatibility = (
  manifest: ModuleManifestV1,
  host: ModuleHostContext,
): ModuleValidationResult => {
  const errors: ModuleValidationError[] = [];
  const add = (code: string, path: string, message: string) => {
    errors.push({ code, path, message });
  };

  if (
    Number.isNaN(compareVersions(host.kernelVersion, manifest.kernel.min)) ||
    Number.isNaN(compareVersions(host.kernelVersion, manifest.kernel.maxExclusive)) ||
    compareVersions(host.kernelVersion, manifest.kernel.min) < 0 ||
    compareVersions(host.kernelVersion, manifest.kernel.maxExclusive) >= 0
  ) {
    add("kernel_incompatible", "/kernel", "Module kernel range is incompatible.");
  }

  if (host.androidSdk < manifest.platform.android.minSdk) {
    add(
      "android_sdk_incompatible",
      "/platform/android/minSdk",
      "Host Android SDK is below the module minimum.",
    );
  }

  if (!manifest.platform.android.abis.every((abi) => host.abis.includes(abi))) {
    add(
      "abi_incompatible",
      "/platform/android/abis",
      "Host does not provide every required ABI.",
    );
  }

  if (!host.availableRuntimes.includes(manifest.runtime.kind)) {
    add(
      "runtime_unavailable",
      "/runtime/kind",
      "The requested runtime is unavailable on this host.",
    );
  }

  for (const [index, contribution] of manifest.contributes.entries()) {
    if (!PLACEMENT_RULES[contribution.type].includes(contribution.defaultPlacement)) {
      add(
        "placement_mismatch",
        `/contributes/${index}/defaultPlacement`,
        "Contribution placement is incompatible with its type.",
      );
    }
  }

  if (manifest.risk === "high" && manifest.audience !== "advanced") {
    add(
      "risk_audience_mismatch",
      "/audience",
      "High-risk modules must target the advanced audience.",
    );
  }

  return errors.length === 0 ? { ok: true, errors: [] } : { ok: false, errors };
};

export const validateModuleManifest = (
  value: unknown,
  host: ModuleHostContext,
): ModuleValidationResult => {
  const ok = validateSchema(value);
  if (!ok) {
    return {
      ok: false,
      errors: (validateSchema.errors ?? []).map((error) => ({
        code: `schema.${error.keyword}`,
        path: schemaErrorPath(error),
        message: error.message ?? "invalid module manifest",
      })),
    };
  }
  return validateSemanticCompatibility(value, host);
};
