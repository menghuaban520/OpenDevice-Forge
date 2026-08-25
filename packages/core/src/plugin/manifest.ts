import type {
  ContributeType as ModuleContributionType,
  ModuleManifestV1,
  SourceKind as ModuleSourceKind,
} from "@opendevice/module-contract";
import type {
  ContributionType,
  ManifestValidationError,
  ManifestValidationResult,
  PlacementSlot,
  PluginContribution,
  PluginManifest,
  PluginPermission,
  PluginSource,
} from "./types";

const SEMVER = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?$/;
const PLUGIN_ID = /^(?:[a-z0-9]+[.-])+[a-z0-9-]+$/;
const RUNTIME_ENTRY = /^[a-z0-9][a-z0-9:._/-]*$/;

const ALLOWED_PERMISSIONS = new Set<PluginPermission>([
  "device.read",
  "device.report",
  "network.outbound",
  "service.local",
]);

const PLACEMENT_RULES: Record<ContributionType, PlacementSlot[]> = {
  navigation: ["sidebar"],
  overviewAction: ["overview", "shortcut"],
  overviewSection: ["overview"],
  deviceProbe: ["overview", "report"],
  configuration: ["context"],
  workflow: ["shortcut", "context"],
  service: ["service"],
  companion: ["service"],
  reportSection: ["report"],
  contextAction: ["context"],
};

type SharedPermission = Extract<
  PluginPermission,
  ModuleManifestV1["permissions"][number]
>;

const isSharedPermission = (
  permission: ModuleManifestV1["permissions"][number],
): permission is SharedPermission =>
  permission === "device.read" ||
  permission === "network.outbound" ||
  permission === "service.local";

const MODULE_CONTRIBUTION_TYPES: Record<
  ModuleContributionType,
  ContributionType
> = {
  navigation: "navigation",
  overviewAction: "overviewAction",
  overviewSection: "overviewSection",
  configuration: "configuration",
  workflow: "workflow",
  service: "service",
  screen: "navigation",
  deviceControl: "workflow",
  companionApp: "companion",
  reportSection: "reportSection",
};

const legacySource = (
  kind: ModuleSourceKind,
  manifest: ModuleManifestV1,
): PluginSource => {
  switch (kind) {
    case "builtin":
    case "official":
      return { kind: "official" };
    case "community":
      return { kind: "community", catalog: manifest.source.repository ?? "" };
    case "github":
      return manifest.source.revision
        ? {
            kind: "github",
            repository: manifest.source.repository ?? "",
            commit: manifest.source.revision,
          }
        : {
            kind: "github",
            repository: manifest.source.repository ?? "",
          };
    case "local":
      return {
        kind: "local",
        fingerprint: manifest.integrity.sha256 ?? manifest.source.revision ?? "",
      };
  }
};

const legacyContribution = (
  contribution: ModuleManifestV1["contributes"][number],
): PluginContribution => {
  const base = {
    id: contribution.id,
    type: MODULE_CONTRIBUTION_TYPES[contribution.type],
    label: contribution.label,
  };
  return contribution.defaultPlacement === "fullscreen"
    ? base
    : { ...base, defaultPlacement: contribution.defaultPlacement };
};

export const moduleManifestToLegacyPlugin = (
  manifest: ModuleManifestV1,
): PluginManifest => {
  const contributes = manifest.contributes.map(legacyContribution);
  if (
    manifest.capabilities.includes("api.openai-compatible") &&
    !contributes.some((contribution) => contribution.type === "service")
  ) {
    contributes.push({
      id: `${manifest.id}.openai-compatible`,
      type: "service",
      label: "OpenAI 兼容接口",
      defaultPlacement: "service",
    });
  }

  const runtimeKind = contributes.some(
    (contribution) => contribution.type === "service",
  )
    ? "service"
    : "workflow";

  return {
    manifestVersion: "1",
    id: manifest.id,
    name: manifest.name,
    summary: manifest.summary,
    version: manifest.version,
    publisher: manifest.publisher,
    kernel: manifest.kernel,
    source: legacySource(manifest.source.kind, manifest),
    execution:
      manifest.runtime.kind === "builtin" || manifest.runtime.kind === "companion"
        ? "native"
        : "declarative",
    audience: manifest.audience,
    risk: manifest.risk,
    permissions: manifest.permissions.filter(isSharedPermission),
    contributes,
    runtime: { kind: runtimeKind, entry: manifest.runtime.entry },
    protected: manifest.protected,
  };
};

const parseVersion = (version: string): [number, number, number] | undefined => {
  const match = SEMVER.exec(version);
  if (!match) return undefined;
  return [Number(match[1]), Number(match[2]), Number(match[3])];
};

export const compareVersions = (left: string, right: string): number => {
  const a = parseVersion(left);
  const b = parseVersion(right);
  if (!a || !b) return Number.NaN;
  for (let index = 0; index < 3; index += 1) {
    const difference = (a[index] ?? 0) - (b[index] ?? 0);
    if (difference !== 0) return difference;
  }
  return 0;
};

export const validatePluginManifest = (
  manifest: PluginManifest,
  kernelVersion: string,
): ManifestValidationResult => {
  const errors: ManifestValidationError[] = [];
  const add = (
    code: ManifestValidationError["code"],
    path: string,
    message: string,
  ) => errors.push({ code, path, message });

  if (manifest.manifestVersion !== "1") {
    add("manifest_version_unsupported", "manifestVersion", "仅支持清单版本 1。");
  }
  if (!PLUGIN_ID.test(manifest.id)) {
    add("identity_invalid", "id", "插件 ID 必须是稳定的反向域名格式。");
  }
  if (!parseVersion(manifest.version)) {
    add("version_invalid", "version", "插件版本必须使用语义化版本。");
  }

  const kernelIsValid = parseVersion(kernelVersion);
  const minIsValid = parseVersion(manifest.kernel.min);
  const maxIsValid = parseVersion(manifest.kernel.maxExclusive);
  if (
    !kernelIsValid ||
    !minIsValid ||
    !maxIsValid ||
    compareVersions(kernelVersion, manifest.kernel.min) < 0 ||
    compareVersions(kernelVersion, manifest.kernel.maxExclusive) >= 0
  ) {
    add("kernel_incompatible", "kernel", "插件与当前内核版本不兼容。");
  }

  if (manifest.source.kind === "github") {
    if (!manifest.source.repository.startsWith("https://github.com/")) {
      add("source_invalid", "source.repository", "GitHub 来源必须使用仓库 HTTPS 地址。");
    }
    if (!manifest.source.release && !manifest.source.commit) {
      add("source_unpinned", "source", "GitHub 插件必须固定到发布版本或提交。");
    }
  }
  if (manifest.source.kind === "local" && manifest.source.fingerprint.length < 8) {
    add("source_invalid", "source.fingerprint", "本地插件必须带有可核对的指纹。");
  }
  if (
    manifest.execution === "native" &&
    (manifest.source.kind === "community" || manifest.source.kind === "github")
  ) {
    add(
      "untrusted_native_execution",
      "execution",
      "社区与 GitHub 来源在 MVP 中只能使用声明式能力。",
    );
  }

  for (const [index, permission] of manifest.permissions.entries()) {
    if (!ALLOWED_PERMISSIONS.has(permission)) {
      add("permission_unknown", `permissions.${index}`, `未知权限：${String(permission)}`);
    }
  }

  if (manifest.contributes.length === 0) {
    add("contribution_empty", "contributes", "插件至少需要声明一个贡献点。");
  }
  const contributionIds = new Set<string>();
  for (const [index, contribution] of manifest.contributes.entries()) {
    if (contributionIds.has(contribution.id)) {
      add(
        "contribution_duplicate",
        `contributes.${index}.id`,
        `贡献点 ID 重复：${contribution.id}`,
      );
    }
    contributionIds.add(contribution.id);
    if (
      contribution.defaultPlacement &&
      !PLACEMENT_RULES[contribution.type].includes(contribution.defaultPlacement)
    ) {
      add(
        "placement_mismatch",
        `contributes.${index}.defaultPlacement`,
        `${contribution.type} 不能放在 ${contribution.defaultPlacement}。`,
      );
    }
  }

  if (manifest.runtime) {
    if (!RUNTIME_ENTRY.test(manifest.runtime.entry)) {
      add("runtime_invalid", "runtime.entry", "运行器入口格式无效。");
    }
    const contributionTypes = new Set(
      manifest.contributes.map((contribution) => contribution.type),
    );
    const matchesContribution = manifest.runtime.kind === "service"
      ? contributionTypes.has("service")
      : ["deviceProbe", "workflow", "overviewSection", "reportSection"].some(
          (type) => contributionTypes.has(type as ContributionType),
        );
    if (!matchesContribution) {
      add(
        "runtime_contribution_mismatch",
        "runtime.kind",
        "运行器类型与插件贡献点不匹配。",
      );
    }
  }

  if (manifest.risk === "high" && manifest.audience !== "advanced") {
    add(
      "risk_audience_mismatch",
      "audience",
      "高风险插件必须明确标记为高级用户能力。",
    );
  }

  return { ok: errors.length === 0, errors };
};
