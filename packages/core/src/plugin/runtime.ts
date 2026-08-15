import type { PluginManifest, PluginRuntimeKind } from "./types";

export type PluginConfigurationValue = string | number | boolean | string[];
export type PluginConfiguration = Record<string, PluginConfigurationValue>;

export interface PluginConfigurationOption {
  value: string;
  label: string;
  description?: string;
}

interface PluginConfigurationFieldBase {
  id: string;
  label: string;
  description?: string;
}

export interface PluginCheckboxGroupField extends PluginConfigurationFieldBase {
  type: "checkbox-group";
  options: PluginConfigurationOption[];
}

export interface PluginToggleField extends PluginConfigurationFieldBase {
  type: "toggle";
}

export interface PluginNumberField extends PluginConfigurationFieldBase {
  type: "number";
  min?: number;
  max?: number;
  step?: number;
}

export interface PluginTextField extends PluginConfigurationFieldBase {
  type: "text";
  placeholder?: string;
}

export interface PluginSelectField extends PluginConfigurationFieldBase {
  type: "select";
  options: PluginConfigurationOption[];
}

export type PluginConfigurationField =
  | PluginCheckboxGroupField
  | PluginToggleField
  | PluginNumberField
  | PluginTextField
  | PluginSelectField;

export interface PluginConfigurationSchema {
  version: "1";
  fields: PluginConfigurationField[];
}

export interface PluginRunProgress {
  phase: string;
  message: string;
}

export type PluginRunState =
  | "idle"
  | "running"
  | "success"
  | "error"
  | "cancelled";

export interface PluginResultItem {
  id: string;
  label: string;
  value: string;
  status: "ok" | "unknown" | "error";
}

export interface PluginResultGroup {
  id: string;
  label: string;
  items: PluginResultItem[];
}

export interface PluginRunResult {
  groups: PluginResultGroup[];
}

export interface PluginExecutionRequest<TContext> {
  context: TContext;
  configuration: PluginConfiguration;
  signal: AbortSignal;
  onProgress(progress: PluginRunProgress): void;
}

interface RuntimeBase {
  pluginId: string;
  kind: PluginRuntimeKind;
  configuration?: PluginConfigurationSchema;
  defaultConfiguration: PluginConfiguration;
  validate(configuration: PluginConfiguration): Record<string, string>;
}

export interface WorkflowRuntimeDefinition<TContext = unknown>
  extends RuntimeBase {
  kind: "workflow";
  execute(
    request: PluginExecutionRequest<TContext>,
  ): Promise<PluginRunResult>;
}

export interface ServiceLaunchDefinition {
  packageEntry: string;
  arguments: readonly string[];
}

export interface ServiceRuntimeDefinition extends RuntimeBase {
  kind: "service";
  localOnly: true;
  fixedArguments: true;
  defaultHost: "127.0.0.1";
  launch: ServiceLaunchDefinition;
  lifecycle: readonly ["start", "stop", "restart"];
}

export type ServiceRuntimeState =
  | "stopped"
  | "starting"
  | "running"
  | "error";

export interface ServiceEndpoint {
  host: "127.0.0.1";
  port: number;
  baseUrl: string;
  healthy: boolean;
}

export interface ServiceLogEntry {
  sequence: number;
  stream: "stdout" | "stderr" | "host";
  message: string;
}

export interface ServiceRuntimeSnapshot {
  state: ServiceRuntimeState;
  endpoint: ServiceEndpoint | null;
  logs: ServiceLogEntry[];
}

export type PluginRuntimeDefinition<TContext = unknown> =
  | WorkflowRuntimeDefinition<TContext>
  | ServiceRuntimeDefinition;

export type RuntimeValidationError =
  | "plugin_id_mismatch"
  | "runtime_kind_mismatch"
  | "service_not_local_only"
  | "service_host_not_local"
  | "service_arguments_not_fixed"
  | "service_entry_not_package_relative";

export interface RuntimeValidationResult {
  ok: boolean;
  errors: RuntimeValidationError[];
}

const packageEntryIsSafe = (entry: string): boolean => {
  const normalized = entry.trim();
  if (!normalized || normalized.startsWith("/") || /^[a-zA-Z]:[\\/]/.test(normalized)) {
    return false;
  }
  return !normalized.split(/[\\/]/).includes("..");
};

export const validateRuntimeDefinition = <TContext>(
  manifest: PluginManifest,
  runtime: PluginRuntimeDefinition<TContext>,
): RuntimeValidationResult => {
  const errors: RuntimeValidationError[] = [];
  const add = (error: RuntimeValidationError) => {
    if (!errors.includes(error)) errors.push(error);
  };

  if (manifest.id !== runtime.pluginId) add("plugin_id_mismatch");
  if (manifest.runtime?.kind !== runtime.kind) add("runtime_kind_mismatch");

  if (runtime.kind === "service") {
    if (runtime.localOnly !== true) add("service_not_local_only");
    if (runtime.defaultHost !== "127.0.0.1") add("service_host_not_local");
    if (
      runtime.fixedArguments !== true ||
      runtime.launch.arguments.some((argument) => /[\r\n]/.test(argument))
    ) {
      add("service_arguments_not_fixed");
    }
    if (!packageEntryIsSafe(runtime.launch.packageEntry)) {
      add("service_entry_not_package_relative");
    }
  }

  return { ok: errors.length === 0, errors };
};

export const isPluginRunnable = <TContext>(
  manifest: PluginManifest,
  runtimes: ReadonlyMap<string, PluginRuntimeDefinition<TContext>>,
): boolean => {
  const entry = manifest.runtime?.entry;
  if (!entry) return false;
  const runtime = runtimes.get(entry);
  return Boolean(runtime && validateRuntimeDefinition(manifest, runtime).ok);
};
