export const PLACEMENT_SLOTS = [
  "sidebar",
  "overview",
  "shortcut",
  "service",
  "report",
  "context",
] as const;

export type PlacementSlot = (typeof PLACEMENT_SLOTS)[number];

export type ContributionType =
  | "navigation"
  | "overviewAction"
  | "overviewSection"
  | "deviceProbe"
  | "configuration"
  | "workflow"
  | "service"
  | "companion"
  | "reportSection"
  | "contextAction";

export type PluginPermission =
  | "device.read"
  | "device.report"
  | "network.outbound"
  | "service.local";

export type PluginRuntimeKind = "workflow" | "service";

export interface PluginRuntimeReference {
  kind: PluginRuntimeKind;
  entry: string;
}

export type PluginSource =
  | { kind: "official" }
  | { kind: "community"; catalog: string }
  | {
      kind: "github";
      repository: string;
      release?: string;
      commit?: string;
    }
  | { kind: "local"; fingerprint: string };

export interface PluginContribution {
  id: string;
  type: ContributionType;
  label: string;
  defaultPlacement?: PlacementSlot;
}

export interface PluginManifest {
  manifestVersion: "1";
  id: string;
  name: string;
  summary: string;
  version: string;
  publisher: string;
  kernel: {
    min: string;
    maxExclusive: string;
  };
  source: PluginSource;
  execution: "declarative" | "native";
  audience: "general" | "advanced";
  risk: "low" | "medium" | "high";
  permissions: PluginPermission[];
  contributes: PluginContribution[];
  runtime?: PluginRuntimeReference;
  protected?: boolean;
}

export interface ManifestValidationError {
  code:
    | "manifest_version_unsupported"
    | "identity_invalid"
    | "version_invalid"
    | "kernel_incompatible"
    | "source_unpinned"
    | "source_invalid"
    | "untrusted_native_execution"
    | "permission_unknown"
    | "contribution_empty"
    | "contribution_duplicate"
    | "placement_mismatch"
    | "runtime_invalid"
    | "runtime_contribution_mismatch"
    | "risk_audience_mismatch";
  message: string;
  path: string;
}

export interface ManifestValidationResult {
  ok: boolean;
  errors: ManifestValidationError[];
}

export interface InstalledPlugin {
  manifest: PluginManifest;
  enabled: boolean;
  previousVersions: PluginManifest[];
}

export type PluginAuditAction =
  | "install"
  | "enable"
  | "disable"
  | "update"
  | "rollback"
  | "uninstall"
  | "safe_mode_on"
  | "safe_mode_off";

export interface PluginAuditEvent {
  sequence: number;
  action: PluginAuditAction;
  pluginId: string;
  fromVersion?: string;
  toVersion?: string;
}

export interface PluginRegistryState {
  kernelVersion: string;
  safeMode: boolean;
  plugins: Record<string, InstalledPlugin>;
  audit: PluginAuditEvent[];
}

export type PluginRegistryAction =
  | { type: "install"; manifest: PluginManifest }
  | { type: "enable"; pluginId: string }
  | { type: "disable"; pluginId: string }
  | { type: "update"; manifest: PluginManifest }
  | { type: "rollback"; pluginId: string }
  | { type: "uninstall"; pluginId: string }
  | { type: "setSafeMode"; enabled: boolean };

export interface PluginRegistryResult {
  ok: boolean;
  state: PluginRegistryState;
  error?:
    | "manifest_invalid"
    | "plugin_already_installed"
    | "plugin_not_installed"
    | "plugin_protected"
    | "rollback_unavailable"
    | "version_not_newer";
}

export type LayoutScope =
  | { kind: "all" }
  | { kind: "current" }
  | { kind: "named"; deviceIds: string[] };

export interface PlacementEntry {
  pluginId: string;
  visible: boolean;
}

export interface LayoutProfile {
  id: string;
  name: string;
  scope: LayoutScope;
  placements: Record<PlacementSlot, PlacementEntry[]>;
}
