# Plugin-First Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a plugin-first OpenDevice Forge shell with a real configurable device-inspection workflow, truthful marketplace availability, and a typed future boundary for local single-file services.

**Architecture:** Keep device discovery and plugin lifecycle in the host. Add a typed runtime declaration to plugin manifests, a desktop runtime registry that binds trusted built-in entries to executors, and one generic workbench that renders configuration, execution progress, and structured results. The first executor uses the existing ADB adapter with an explicit probe selection; future local services reuse the same manifest/config/result boundary through a non-executing `service` contract.

**Tech Stack:** TypeScript 5, React 19, Vitest, Testing Library, Playwright, Tauri 2, Rust, serde, pnpm workspaces, Lucide icons.

## Global Constraints

- Android device discovery, selection, connection status, plugin management, permission boundaries, and runtime hosting remain kernel responsibilities.
- Device inspection, reports, AI readiness, routing, models, and local APIs are plugin responsibilities.
- Only plugins with a matching registered runtime may expose install, enable, or run actions.
- Built-in runtimes use fixed code registrations; do not load arbitrary JavaScript, dynamic libraries, shell strings, GitHub repositories, or third-party executables.
- USB serials remain ephemeral session identifiers: never display, export, or persist them in plugin configuration.
- The first workbench must support `运行`, `配置`, and `结果`; omit `配置` when a plugin has no configuration schema.
- The first real plugin is `dev.opendevice.device-inspection` with identity, performance, power, and system probe groups.
- A disabled probe group must neither execute its ADB probes nor appear in the result.
- A future `service` contract must require local-only binding and fixed argument arrays, but this plan must not launch a remote gateway or third-party executable.
- Remove simulated window controls, fake back/forward controls, unimplemented refresh/help controls, lock decoration, `核心（不可移除）`, the rail read-only declaration, and the footer safety strip.
- No Root, bootloader unlock, flashing, phone-side installation, public listening address, or public remote access.
- Preserve the current dirty worktree; never reset or discard existing changes. Stage only the files named by each task.
- Desktop validation widths are exactly 1280×800 and 1024×800.
- Use one blue primary action per screen, Lucide icons only, and `prefers-reduced-motion` for state transitions.

---

### Task 0: Checkpoint the verified generic-device baseline

**Files:**
- Commit existing: `DESIGN.md`
- Commit existing: `apps/desktop/e2e/app.spec.ts`
- Commit existing: `apps/desktop/src-tauri/build.rs`
- Commit existing: `apps/desktop/src-tauri/src/adb.rs`
- Commit existing: `apps/desktop/src-tauri/src/device.rs`
- Commit existing: `apps/desktop/src-tauri/src/lib.rs`
- Commit existing: `apps/desktop/src-tauri/src/main.rs`
- Commit existing: `apps/desktop/src-tauri/src/usb.rs`
- Commit existing: `apps/desktop/src-tauri/tauri.conf.json`
- Commit existing: `apps/desktop/src/App.test.tsx`
- Commit existing: `apps/desktop/src/App.tsx`
- Commit existing: `apps/desktop/src/components/AppShell.tsx`
- Commit existing: `apps/desktop/src/features/overview/DeviceOverview.tsx`
- Commit existing: `apps/desktop/src/features/plugins/LayoutEditor.test.tsx`
- Commit existing: `apps/desktop/src/features/plugins/LayoutEditor.tsx`
- Commit existing: `apps/desktop/src/features/plugins/PluginDetail.tsx`
- Commit existing: `apps/desktop/src/features/plugins/PluginMarket.test.tsx`
- Commit existing: `apps/desktop/src/features/plugins/PluginMarket.tsx`
- Commit existing: `apps/desktop/src/lib/device-client.ts`
- Commit existing: `apps/desktop/src/styles.css`
- Commit existing: `docs/verification/screenshots/desktop-overview.png`
- Commit existing: `docs/verification/screenshots/layout-editor-1280x800.png`
- Commit existing: `docs/verification/screenshots/layout-editor.png`
- Commit existing: `docs/verification/screenshots/plugin-market.png`

**Interfaces:**
- Consumes: the already verified generic Android identification, macOS USB hint, Huawei storage parser, and ad-hoc signing configuration.
- Produces: a clean review checkpoint so later plugin commits contain only plugin-workbench changes.

- [ ] **Step 1: Verify the existing baseline without modifying files**

Run:

```bash
git diff --check
cargo fmt --manifest-path apps/desktop/src-tauri/Cargo.toml -- --check
pnpm check
cargo test --manifest-path apps/desktop/src-tauri/Cargo.toml
pnpm --filter @opendevice/desktop test:e2e
```

Expected: all commands exit 0; workspace tests report 29 core, 8 site, 13 desktop, 9 Rust, and 1 Playwright test unless a newer committed test count is present.

- [ ] **Step 2: Confirm the staged set is empty before checkpointing**

Run:

```bash
git diff --cached --name-only
```

Expected: no output. If output is present, stop and inspect it; do not fold an unknown staged change into this checkpoint.

- [ ] **Step 3: Stage exactly the verified baseline files**

Run:

```bash
git add DESIGN.md \
  apps/desktop/e2e/app.spec.ts \
  apps/desktop/src-tauri/build.rs \
  apps/desktop/src-tauri/src/adb.rs \
  apps/desktop/src-tauri/src/device.rs \
  apps/desktop/src-tauri/src/lib.rs \
  apps/desktop/src-tauri/src/main.rs \
  apps/desktop/src-tauri/src/usb.rs \
  apps/desktop/src-tauri/tauri.conf.json \
  apps/desktop/src/App.test.tsx \
  apps/desktop/src/App.tsx \
  apps/desktop/src/components/AppShell.tsx \
  apps/desktop/src/features/overview/DeviceOverview.tsx \
  apps/desktop/src/features/plugins/LayoutEditor.test.tsx \
  apps/desktop/src/features/plugins/LayoutEditor.tsx \
  apps/desktop/src/features/plugins/PluginDetail.tsx \
  apps/desktop/src/features/plugins/PluginMarket.test.tsx \
  apps/desktop/src/features/plugins/PluginMarket.tsx \
  apps/desktop/src/lib/device-client.ts \
  apps/desktop/src/styles.css \
  docs/verification/screenshots/desktop-overview.png \
  docs/verification/screenshots/layout-editor-1280x800.png \
  docs/verification/screenshots/layout-editor.png \
  docs/verification/screenshots/plugin-market.png
git diff --cached --check
```

Expected: `git diff --cached --check` exits 0.

- [ ] **Step 4: Commit the verified baseline**

Run:

```bash
git commit -m "feat(device): verify generic Android inspection"
```

Expected: one commit containing only the files listed in this task.

---

### Task 1: Define truthful workflow and service runtime contracts

**Files:**
- Create: `packages/core/src/plugin/runtime.ts`
- Create: `packages/core/src/plugin/runtime.test.ts`
- Modify: `packages/core/src/plugin/types.ts`
- Modify: `packages/core/src/plugin/manifest.ts`
- Modify: `packages/core/src/plugin/manifest.test.ts`
- Modify: `packages/core/src/plugin/builtins.ts`
- Modify: `packages/core/src/index.ts`

**Interfaces:**
- Consumes: `PluginManifest` and the existing manifest validator.
- Produces: `PluginRuntimeReference`, `PluginConfiguration`, `PluginConfigurationSchema`, `PluginRunProgress`, `PluginRunResult`, `WorkflowRuntimeDefinition<TContext>`, `ServiceRuntimeDefinition`, `PluginRuntimeDefinition<TContext>`, `validateRuntimeDefinition()`, and `isPluginRunnable()`.

- [ ] **Step 1: Write failing tests for manifest/runtime matching and local-only service contracts**

Add `packages/core/src/plugin/runtime.test.ts` with these observable cases:

```ts
import { describe, expect, it, vi } from "vitest";
import { BUILTIN_PLUGINS } from "./builtins";
import {
  isPluginRunnable,
  validateRuntimeDefinition,
  type ServiceRuntimeDefinition,
  type WorkflowRuntimeDefinition,
} from "./runtime";

describe("plugin runtime contracts", () => {
  const inspection = BUILTIN_PLUGINS.find(
    (manifest) => manifest.id === "dev.opendevice.device-inspection",
  )!;

  it("requires a registered runtime whose plugin id and kind match the manifest", () => {
    const runtime: WorkflowRuntimeDefinition<{ sessionSerial: string }> = {
      pluginId: inspection.id,
      kind: "workflow",
      defaultConfiguration: { groups: ["identity"] },
      validate: () => ({}),
      execute: vi.fn(),
    };
    const runtimes = new Map([["builtin:device-inspection", runtime]]);

    expect(validateRuntimeDefinition(inspection, runtime)).toEqual({ ok: true, errors: [] });
    expect(isPluginRunnable(inspection, runtimes)).toBe(true);
    expect(isPluginRunnable(inspection, new Map())).toBe(false);
  });

  it("rejects a service runtime that is not local-only or fixed-argument", () => {
    const manifest = {
      ...inspection,
      id: "dev.example.local-service",
      runtime: { kind: "service" as const, entry: "builtin:local-service" },
    };
    const runtime = {
      pluginId: manifest.id,
      kind: "service",
      localOnly: false,
      fixedArguments: false,
      defaultHost: "0.0.0.0",
      launch: {
        packageEntry: "../outside/server",
        arguments: ["--port", "11434"],
      },
      lifecycle: ["start", "stop", "restart"],
      defaultConfiguration: {},
      validate: () => ({}),
    } as unknown as ServiceRuntimeDefinition;

    expect(validateRuntimeDefinition(manifest, runtime)).toEqual({
      ok: false,
      errors: [
        "service_not_local_only",
        "service_host_not_local",
        "service_arguments_not_fixed",
        "service_entry_not_package_relative",
      ],
    });

    const validRuntime: ServiceRuntimeDefinition = {
      pluginId: manifest.id,
      kind: "service",
      localOnly: true,
      fixedArguments: true,
      defaultHost: "127.0.0.1",
      launch: {
        packageEntry: "bin/model-server",
        arguments: ["--port", "11434"],
      },
      lifecycle: ["start", "stop", "restart"],
      defaultConfiguration: { port: 11434 },
      validate: () => ({}),
    };
    expect(validateRuntimeDefinition(manifest, validRuntime)).toEqual({
      ok: true,
      errors: [],
    });
  });
});
```

Extend `packages/core/src/plugin/manifest.test.ts` with a manifest containing `runtime: { kind: "service", entry: "builtin:gateway" }` but no `service` contribution; expect `validatePluginManifest()` to include `runtime_contribution_mismatch`.

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
pnpm --filter @opendevice/core test -- runtime.test.ts manifest.test.ts
```

Expected: FAIL because runtime types/functions and runtime manifest validation do not exist.

- [ ] **Step 3: Add the runtime reference to manifests**

Add these exact types to `packages/core/src/plugin/types.ts` and add `runtime?: PluginRuntimeReference` to `PluginManifest`:

```ts
export type PluginRuntimeKind = "workflow" | "service";

export interface PluginRuntimeReference {
  kind: PluginRuntimeKind;
  entry: string;
}
```

Add `runtime_invalid` and `runtime_contribution_mismatch` to `ManifestValidationError["code"]`. In `manifest.ts`, require `runtime.entry` to match `/^[a-z0-9][a-z0-9:._/-]*$/`; require a `service` runtime to have a `service` contribution; require a `workflow` runtime to have at least one of `deviceProbe`, `workflow`, `overviewSection`, or `reportSection`.

Declare only the real first runtime in `builtins.ts`:

```ts
runtime: { kind: "workflow", entry: "builtin:device-inspection" },
```

Do not add runtime declarations to AI readiness, report export, remote gateway, or Root Lab in this task.

- [ ] **Step 4: Create the runtime contract and validator**

Create `packages/core/src/plugin/runtime.ts` with these public structures:

```ts
import type { PluginManifest, PluginRuntimeKind } from "./types";

export type PluginConfigurationValue = string | number | boolean | string[];
export type PluginConfiguration = Record<string, PluginConfigurationValue>;

export interface PluginConfigurationOption {
  value: string;
  label: string;
  description?: string;
}

export interface PluginCheckboxGroupField {
  id: string;
  type: "checkbox-group";
  label: string;
  description?: string;
  options: PluginConfigurationOption[];
}

export interface PluginToggleField {
  id: string;
  type: "toggle";
  label: string;
  description?: string;
}

export interface PluginNumberField {
  id: string;
  type: "number";
  label: string;
  description?: string;
  min?: number;
  max?: number;
  step?: number;
}

export interface PluginTextField {
  id: string;
  type: "text";
  label: string;
  description?: string;
  placeholder?: string;
}

export interface PluginSelectField {
  id: string;
  type: "select";
  label: string;
  description?: string;
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

export interface WorkflowRuntimeDefinition<TContext = unknown> extends RuntimeBase {
  kind: "workflow";
  execute(request: PluginExecutionRequest<TContext>): Promise<PluginRunResult>;
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

export type ServiceRuntimeState = "stopped" | "starting" | "running" | "error";

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
```

Implement `validateRuntimeDefinition()` to return the exact error strings exercised by the tests, including `plugin_id_mismatch`, `runtime_kind_mismatch`, `service_not_local_only`, `service_host_not_local`, `service_arguments_not_fixed`, and `service_entry_not_package_relative`. Update the invalid service fixture so `defaultHost` is `"0.0.0.0"` and `launch.packageEntry` is `"../outside/server"`; expect both corresponding errors. A valid service fixture must use `"127.0.0.1"`, a package-relative entry such as `"bin/model-server"`, and a literal argument array such as `["--port", "11434"]`. Reject absolute entries, entries containing a `..` path segment, empty entries, and arguments containing newline or carriage-return characters. Implement `isPluginRunnable()` by resolving `manifest.runtime.entry` in the supplied map and calling the validator. Export the configuration field union, run state, runtime definition, service launch, service status, endpoint, log, and snapshot types from `packages/core/src/index.ts`.

- [ ] **Step 5: Run focused and full core tests**

Run:

```bash
pnpm --filter @opendevice/core test -- runtime.test.ts manifest.test.ts
pnpm --filter @opendevice/core test
pnpm --filter @opendevice/core typecheck
```

Expected: all runtime/manifest tests and all existing core tests pass; typecheck exits 0.

- [ ] **Step 6: Commit the runtime contract**

Run:

```bash
git add packages/core/src/plugin/runtime.ts \
  packages/core/src/plugin/runtime.test.ts \
  packages/core/src/plugin/types.ts \
  packages/core/src/plugin/manifest.ts \
  packages/core/src/plugin/manifest.test.ts \
  packages/core/src/plugin/builtins.ts \
  packages/core/src/index.ts
git commit -m "feat(core): define plugin runtime contracts"
```

---

### Task 2: Make device inspection honor selected probe groups

**Files:**
- Modify: `apps/desktop/src-tauri/src/device.rs`
- Modify: `apps/desktop/src/lib/device-client.ts`
- Modify: `apps/desktop/src/App.tsx`
- Modify: `apps/desktop/src/App.test.tsx`

**Interfaces:**
- Consumes: existing fixed `AdbCommand` variants and `DeviceInspection`.
- Produces: Rust `InspectionSelection`, TypeScript `InspectionSelection`, `ALL_INSPECTION_GROUPS`, and `DeviceClient.inspectDevice(sessionSerial, selection)`.

- [ ] **Step 1: Write a failing Rust test proving disabled groups do not enter the probe plan**

In `device.rs`, add a test that targets a new pure `inspection_plan()` function:

```rust
#[test]
fn inspection_plan_contains_only_enabled_probe_groups() {
    let plan = inspection_plan(InspectionSelection {
        identity: true,
        performance: false,
        power: true,
        system: false,
    });

    assert_eq!(
        plan,
        vec![
            InspectionProbe::Manufacturer,
            InspectionProbe::ProductName,
            InspectionProbe::Model,
            InspectionProbe::AndroidVersion,
            InspectionProbe::Abi,
            InspectionProbe::Battery,
        ],
    );
}
```

- [ ] **Step 2: Run the Rust test and verify RED**

Run:

```bash
cargo test --manifest-path apps/desktop/src-tauri/Cargo.toml device::tests::inspection_plan_contains_only_enabled_probe_groups -- --exact
```

Expected: FAIL because `InspectionSelection`, `InspectionProbe`, and `inspection_plan()` do not exist.

- [ ] **Step 3: Implement the selective Rust probe plan**

Add `Deserialize` and `Default` support to `InspectionSelection`; `Default` must enable all four groups so existing full-device refresh behavior remains intact:

```rust
#[derive(Debug, Clone, Copy, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InspectionSelection {
    pub identity: bool,
    pub performance: bool,
    pub power: bool,
    pub system: bool,
}

impl Default for InspectionSelection {
    fn default() -> Self {
        Self { identity: true, performance: true, power: true, system: true }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum InspectionProbe {
    Manufacturer,
    ProductName,
    Model,
    AndroidVersion,
    Abi,
    Memory,
    Storage,
    Battery,
    Debuggable,
    Secure,
}
```

Implement `inspection_plan()` in identity → performance → power → system order. Refactor `inspect_device()` so it accepts `selection: InspectionSelection`, starts with an empty `InspectionSnapshot`, iterates only the returned probes, and fills the corresponding field using the existing fixed ADB command/property helpers. Derive `Default` for `InspectionSnapshot`. System probes build `root_signals` only from `Debuggable` and `Secure` outputs.

- [ ] **Step 4: Add the TypeScript selection contract and default**

In `device-client.ts`, add:

```ts
export interface InspectionSelection {
  identity: boolean;
  performance: boolean;
  power: boolean;
  system: boolean;
}

export const ALL_INSPECTION_GROUPS: InspectionSelection = {
  identity: true,
  performance: true,
  power: true,
  system: true,
};
```

Change the interface to:

```ts
inspectDevice(
  sessionSerial: string,
  selection?: InspectionSelection,
): Promise<DeviceInspection>;
```

The Tauri client must invoke:

```ts
return invoke<DeviceInspection>("inspect_device", {
  sessionSerial,
  selection: selection ?? ALL_INSPECTION_GROUPS,
});
```

Keep browser behavior unchanged. In `App.tsx`, pass `ALL_INSPECTION_GROUPS` explicitly for the kernel device refresh so the boundary stays visible.

- [ ] **Step 5: Extend the App test fake and prove kernel refresh still asks for all groups**

In `App.test.tsx`, make the ready client capture the second argument and assert the literal object:

```ts
expect(inspectSelections).toEqual([{
  identity: true,
  performance: true,
  power: true,
  system: true,
}]);
```

This assertion catches accidental narrowing of the kernel overview read.

- [ ] **Step 6: Run Rust and desktop tests**

Run:

```bash
cargo fmt --manifest-path apps/desktop/src-tauri/Cargo.toml -- --check
cargo test --manifest-path apps/desktop/src-tauri/Cargo.toml
pnpm --filter @opendevice/desktop test -- App.test.tsx
pnpm --filter @opendevice/desktop typecheck
```

Expected: all commands exit 0 and the new selective plan test passes.

- [ ] **Step 7: Commit selective inspection**

Run:

```bash
git add apps/desktop/src-tauri/src/device.rs \
  apps/desktop/src/lib/device-client.ts \
  apps/desktop/src/App.tsx \
  apps/desktop/src/App.test.tsx
git commit -m "feat(device): support selective inspection probes"
```

---

### Task 3: Register the real device-inspection workflow and persist configuration

**Files:**
- Create: `apps/desktop/src/features/plugins/runtime/device-inspection.ts`
- Create: `apps/desktop/src/features/plugins/runtime/device-inspection.test.ts`
- Create: `apps/desktop/src/features/plugins/runtime/registry.ts`
- Create: `apps/desktop/src/features/plugins/runtime/storage.ts`
- Create: `apps/desktop/src/features/plugins/runtime/storage.test.ts`

**Interfaces:**
- Consumes: `WorkflowRuntimeDefinition<DesktopPluginContext>`, `DeviceClient`, `InspectionSelection`, and `DeviceInspection`.
- Produces: `DesktopPluginContext`, `DEVICE_INSPECTION_ENTRY`, `DEFAULT_INSPECTION_CONFIG`, `createDeviceInspectionRuntime()`, `createDesktopPluginRuntimes()`, `loadPluginConfigurations()`, and `savePluginConfigurations()`.

- [ ] **Step 1: Write a failing real-runtime test for selection and filtered results**

Create `device-inspection.test.ts` using a complete fake `DeviceClient`. Execute the real runtime with only identity and power enabled:

```ts
it("executes only selected groups and returns only their result groups", async () => {
  const selections: InspectionSelection[] = [];
  const client: DeviceClient = {
    probeAdb: async () => ({ available: true, source: "path" }),
    listDevices: async () => [],
    probeUsbDevice: async () => null,
    inspectDevice: async (_serial, selection = ALL_INSPECTION_GROUPS) => {
      selections.push(selection);
      return inspectionFixture;
    },
  };
  const runtime = createDeviceInspectionRuntime(client);
  const result = await runtime.execute({
    context: { sessionSerial: "session-only" },
    configuration: { groups: ["identity", "power"] },
    signal: new AbortController().signal,
    onProgress: () => undefined,
  });

  expect(selections).toEqual([{
    identity: true,
    performance: false,
    power: true,
    system: false,
  }]);
  expect(result.groups.map((group) => group.id)).toEqual(["identity", "power"]);
  expect(runtime.validate({ groups: [] })).toEqual({
    groups: "至少选择一个检测项目",
  });
});
```

Use a literal `inspectionFixture` containing `HUAWEI`, `CDL-AN50`, Android `10`, `arm64-v8a`, `7_749_536 * 1024` bytes RAM, `84_801_144 * 1024` bytes storage, battery `100`, and no root signals.

- [ ] **Step 2: Write failing storage tests**

Create `storage.test.ts` with two cases:

```ts
it("round-trips plugin configuration by plugin id", () => {
  const storage = new MemoryStorage();
  savePluginConfigurations(storage, {
    "dev.opendevice.device-inspection": { groups: ["identity", "system"] },
  });
  expect(loadPluginConfigurations(storage)).toEqual({
    "dev.opendevice.device-inspection": { groups: ["identity", "system"] },
  });
});

it("falls back to an empty record for malformed data", () => {
  const storage = new MemoryStorage();
  storage.setItem(PLUGIN_CONFIG_STORAGE_KEY, "not-json");
  expect(loadPluginConfigurations(storage)).toEqual({});
});
```

Define `MemoryStorage` in the test file with the complete `Storage` interface so the test exercises real serialization rather than mocking the helper.

- [ ] **Step 3: Run the runtime/storage tests and verify RED**

Run:

```bash
pnpm --filter @opendevice/desktop test -- device-inspection.test.ts storage.test.ts
```

Expected: FAIL because the runtime and storage modules do not exist.

- [ ] **Step 4: Implement the device-inspection schema and runtime**

Export these constants from `device-inspection.ts`:

```ts
export const DEVICE_INSPECTION_ENTRY = "builtin:device-inspection";
export const DEVICE_INSPECTION_ID = "dev.opendevice.device-inspection";
export const DEFAULT_INSPECTION_CONFIG: PluginConfiguration = {
  groups: ["identity", "performance", "power", "system"],
};
```

The configuration schema is one `checkbox-group` field named `groups` with these literal options and Chinese labels:

```ts
[
  { value: "identity", label: "设备身份", description: "厂商、型号、系统与架构" },
  { value: "performance", label: "性能基础", description: "运行内存与可用存储" },
  { value: "power", label: "电源状态", description: "当前电量" },
  { value: "system", label: "系统状态", description: "ADB 与只读 Root 信号" },
]
```

`validate()` returns `{ groups: "至少选择一个检测项目" }` when `groups` is absent, not an array, or empty; otherwise it returns `{}`. `execute()` checks `sessionSerial`, emits `正在读取设备…`, calls `client.inspectDevice()` with the exact boolean selection, checks `signal.aborted` before and after the client call, and returns only selected groups. Format RAM/storage as one decimal GiB and battery as a percent. Null values produce status `unknown` and value `设备未提供`.

- [ ] **Step 5: Implement registry and storage helpers**

In `registry.ts`, define:

```ts
export interface DesktopPluginContext {
  sessionSerial: string | null;
}

export const createDesktopPluginRuntimes = (
  client: DeviceClient,
): ReadonlyMap<string, WorkflowRuntimeDefinition<DesktopPluginContext>> =>
  new Map([[DEVICE_INSPECTION_ENTRY, createDeviceInspectionRuntime(client)]]);
```

In `storage.ts`, use the exact key `opendevice.plugin-config.v1` and export:

```ts
export type PluginConfigurationRecord = Record<string, PluginConfiguration>;
export const PLUGIN_CONFIG_STORAGE_KEY = "opendevice.plugin-config.v1";
export function loadPluginConfigurations(storage: Storage): PluginConfigurationRecord;
export function savePluginConfigurations(
  storage: Storage,
  configurations: PluginConfigurationRecord,
): void;
```

Reject parsed arrays and non-object JSON by returning `{}`.

- [ ] **Step 6: Run desktop runtime tests and typecheck**

Run:

```bash
pnpm --filter @opendevice/desktop test -- device-inspection.test.ts storage.test.ts
pnpm --filter @opendevice/desktop typecheck
```

Expected: all tests pass and typecheck exits 0.

- [ ] **Step 7: Commit the real runtime**

Run:

```bash
git add apps/desktop/src/features/plugins/runtime/device-inspection.ts \
  apps/desktop/src/features/plugins/runtime/device-inspection.test.ts \
  apps/desktop/src/features/plugins/runtime/registry.ts \
  apps/desktop/src/features/plugins/runtime/storage.ts \
  apps/desktop/src/features/plugins/runtime/storage.test.ts
git commit -m "feat(desktop): add device inspection runtime"
```

---

### Task 4: Build the generic plugin workbench

**Files:**
- Create: `apps/desktop/src/features/plugins/PluginWorkbench.tsx`
- Create: `apps/desktop/src/features/plugins/PluginWorkbench.test.tsx`
- Modify: `apps/desktop/src/styles.css`

**Interfaces:**
- Consumes: a runnable `PluginManifest`, `WorkflowRuntimeDefinition<DesktopPluginContext>`, current context, saved configuration, and a configuration change callback.
- Produces: `PluginWorkbench` with configuration, execution progress, error, and structured result UI.

- [ ] **Step 1: Write a failing component test for the full successful workflow**

Use `createDeviceInspectionRuntime()` with a complete fake `DeviceClient`, not a fake workbench component. The test must:

```ts
render(
  <PluginWorkbench
    manifest={inspectionManifest}
    runtime={createDeviceInspectionRuntime(client)}
    context={{ sessionSerial: "session-only" }}
    configuration={DEFAULT_INSPECTION_CONFIG}
    onConfigurationChange={onConfigurationChange}
    onBack={() => undefined}
  />,
);

await user.click(screen.getByRole("tab", { name: "配置" }));
await user.click(screen.getByRole("checkbox", { name: /电源状态/ }));
expect(onConfigurationChange).toHaveBeenLastCalledWith({
  groups: ["identity", "performance", "system"],
});

await user.click(screen.getByRole("tab", { name: "运行" }));
await user.click(screen.getByRole("button", { name: "开始检测" }));
expect(await screen.findByRole("tab", { name: "结果" })).toHaveAttribute(
  "aria-selected",
  "true",
);
expect(screen.getByText("设备身份")).toBeInTheDocument();
expect(screen.queryByText("电源状态")).not.toBeInTheDocument();
```

Rerender with the updated configuration before pressing `开始检测`; this proves the executor receives the configuration that the host saved.

- [ ] **Step 2: Write failing disconnected and failure-state tests**

Add two cases:

```ts
const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};

it("disables execution until a ready device session exists", () => {
  renderWorkbench({ sessionSerial: null });
  expect(screen.getByRole("button", { name: "开始检测" })).toBeDisabled();
  expect(screen.getByText("连接设备后即可运行此插件")).toBeInTheDocument();
});

it("shows the executor error and a retry action", async () => {
  renderWorkbenchWithRejectingClient(new Error("device_offline"));
  await user.click(screen.getByRole("button", { name: "开始检测" }));
  expect(await screen.findByText("检测失败，请确认设备仍然在线")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "再次检测" })).toBeEnabled();
});

it("prevents a duplicate run and aborts the active request on unmount", async () => {
  const execution = deferred<PluginRunResult>();
  const observedSignals: AbortSignal[] = [];
  const runtime = {
    ...createDeviceInspectionRuntime(client),
    execute: async (request: PluginExecutionRequest<DesktopPluginContext>) => {
      observedSignals.push(request.signal);
      return execution.promise;
    },
  };
  const { unmount } = render(
    <PluginWorkbench
      manifest={inspectionManifest}
      runtime={runtime}
      context={{ sessionSerial: "session-only" }}
      configuration={DEFAULT_INSPECTION_CONFIG}
      onConfigurationChange={() => undefined}
      onBack={() => undefined}
    />,
  );

  await user.click(screen.getByRole("button", { name: "开始检测" }));
  expect(screen.getByRole("button", { name: "正在检测" })).toBeDisabled();
  expect(observedSignals).toHaveLength(1);

  unmount();
  expect(observedSignals[0]?.aborted).toBe(true);
});
```

- [ ] **Step 3: Write a failing schema-renderer test for service-ready field types**

Create a synthetic workflow runtime whose schema contains these fields and defaults:

```ts
configuration: {
  version: "1",
  fields: [
    { id: "autoStart", type: "toggle", label: "自动启动" },
    { id: "port", type: "number", label: "本机端口", min: 1024, max: 65535 },
    { id: "route", type: "text", label: "路由前缀", placeholder: "/v1" },
    {
      id: "format",
      type: "select",
      label: "配置格式",
      options: [
        { value: "json", label: "JSON" },
        { value: "yaml", label: "YAML" },
      ],
    },
  ],
},
defaultConfiguration: {
  autoStart: false,
  port: 11434,
  route: "/v1",
  format: "json",
},
```

Open `配置`, toggle `自动启动`, change `本机端口` to `12400`, change `路由前缀` to `/api`, and select `YAML`. Assert the last configuration callback is the exact literal:

```ts
{
  autoStart: true,
  port: 12400,
  route: "/api",
  format: "yaml",
}
```

This test proves the generic host can render configuration needed by future model and route service plugins without loading plugin-authored HTML.

- [ ] **Step 4: Run the component test and verify RED**

Run:

```bash
pnpm --filter @opendevice/desktop test -- PluginWorkbench.test.tsx
```

Expected: FAIL because `PluginWorkbench` and its schema renderer do not exist.

- [ ] **Step 5: Implement the workbench state machine and accessible tabs**

`PluginWorkbench` must use the exported `PluginRunState` values `idle | running | success | error | cancelled`. Render a header with plugin name, summary, a functional `返回插件市场` button, and tabs:

```ts
type WorkbenchTab = "run" | "configuration" | "result";
```

Rules:

- Initial tab is `run`.
- Omit `配置` when `runtime.configuration` is undefined.
- Render `checkbox-group` with native checkboxes, `toggle` with one native checkbox carrying `role="switch"`, `number` with a numeric input and declared min/max/step, `text` with a text input and placeholder, and `select` with a native select and declared options.
- Convert numeric input values with `Number(event.currentTarget.value)` and preserve every sibling configuration key when one field changes.
- Validate before executing; field errors stay beside the group and execution does not start.
- During execution, disable all config controls and the primary button, show the latest progress message, and ignore repeated starts.
- On success, store the result and select `result`.
- On failure, preserve configuration, select `result`, show `检测失败，请确认设备仍然在线`, and expose `再次检测`.
- Result groups render as definition rows; status `unknown` reads `设备未提供`; status `error` uses one `CircleAlert` icon.
- Abort the controller on unmount. Do not expose cancellation UI for this quick workflow.

- [ ] **Step 6: Add focused workbench styling**

In `styles.css`, add `.plugin-workbench`, `.workbench-header`, `.workbench-tabs`, `.workbench-run`, `.configuration-fieldset`, `.configuration-option`, `.run-progress`, `.result-group`, and `.result-row`. Use existing colors and typography; no new dependency, gradient, or card grid. Add `:focus-visible` outlines and a reduced-motion rule:

```css
@media (prefers-reduced-motion: reduce) {
  .run-progress-indicator { animation: none; }
}
```

- [ ] **Step 7: Run component tests, lint, and typecheck**

Run:

```bash
pnpm --filter @opendevice/desktop test -- PluginWorkbench.test.tsx
pnpm --filter @opendevice/desktop lint
pnpm --filter @opendevice/desktop typecheck
```

Expected: all commands exit 0.

- [ ] **Step 8: Commit the workbench**

Run:

```bash
git add apps/desktop/src/features/plugins/PluginWorkbench.tsx \
  apps/desktop/src/features/plugins/PluginWorkbench.test.tsx \
  apps/desktop/src/styles.css
git commit -m "feat(desktop): add plugin workbench"
```

---

### Task 5: Integrate truthful marketplace actions and simplify the shell

**Files:**
- Modify: `apps/desktop/src/App.tsx`
- Modify: `apps/desktop/src/App.test.tsx`
- Modify: `apps/desktop/src/components/AppShell.tsx`
- Modify: `apps/desktop/src/features/overview/DeviceOverview.tsx`
- Modify: `apps/desktop/src/features/plugins/PluginMarket.tsx`
- Modify: `apps/desktop/src/features/plugins/PluginMarket.test.tsx`
- Modify: `apps/desktop/src/features/plugins/PluginDetail.tsx`
- Delete: `apps/desktop/src/features/plugins/LayoutEditor.tsx`
- Delete: `apps/desktop/src/features/plugins/LayoutEditor.test.tsx`
- Modify: `apps/desktop/src/styles.css`

**Interfaces:**
- Consumes: runtime registry, workbench, plugin lifecycle registry, and plugin configuration storage.
- Produces: `AppPage = "overview" | "plugins" | "workbench"`, `activePluginId`, `onOpenPlugin(pluginId)`, truthful marketplace availability, and the final simplified shell.

- [ ] **Step 1: Write a failing App test for install → enable → open → configure → run**

Use a ready fake device client and clear both `opendevice.registry.v2` and `opendevice.plugin-config.v1`. The test flow is:

```ts
await user.click(screen.getByRole("button", { name: "插件市场" }));
await user.click(screen.getByRole("tab", { name: "发现" }));
await user.click(screen.getByRole("button", { name: "设备体检 插件" }));
await user.click(screen.getByRole("button", { name: "安装设备体检" }));
await user.click(screen.getByRole("button", { name: "启用设备体检" }));
await user.click(screen.getByRole("button", { name: "打开设备体检工作台" }));
expect(screen.getByRole("heading", { name: "设备体检" })).toBeInTheDocument();
expect(screen.getByRole("button", { name: "开始检测" })).toBeEnabled();
```

After running, assert `CDL-AN50`, `7.4 GB`, `80.9 GB`, and `100%` are present in the result.

Add a persistence case that disables `电源状态`, unmounts the App, renders a new App against the same `window.localStorage`, reopens the workbench, and asserts the `电源状态` checkbox remains unchecked. This is the component-level proof for configuration surviving an application refresh.

- [ ] **Step 2: Write failing tests for removed UI and unavailable plugins**

Add assertions that these texts/labels are absent from the rendered App:

```ts
for (const removed of [
  "核心（不可移除）",
  "默认只读",
  "只读模式",
  "插件帮助",
  "刷新目录",
  "后退",
  "前进",
]) {
  expect(screen.queryByText(removed)).not.toBeInTheDocument();
}
```

In `PluginMarket.test.tsx`, select `远程网关` and assert `尚未接入运行器` is present while install/open actions are absent. Select `设备体检` with its registered runtime entry and assert the correct lifecycle action is present.

- [ ] **Step 3: Run focused tests and verify RED**

Run:

```bash
pnpm --filter @opendevice/desktop test -- App.test.tsx PluginMarket.test.tsx
```

Expected: FAIL because the workbench route, truthful availability, and shell simplification are not integrated.

- [ ] **Step 4: Integrate runtime/config state in App**

Make these exact state changes:

```ts
const [page, setPage] = useState<AppPage>("overview");
const [activePluginId, setActivePluginId] = useState<string | null>(null);
const [pluginConfigurations, setPluginConfigurations] = useState(
  () => loadPluginConfigurations(window.localStorage),
);
const runtimes = useMemo(() => createDesktopPluginRuntimes(client), [client]);
```

Use `opendevice.registry.v2` and initialize only `kernel.plugin-manager`; the device inspection plugin must be installed through the market. Persist configuration changes with `savePluginConfigurations()` in an effect. `openPlugin()` must refuse unavailable, uninstalled, or disabled plugins and otherwise set `activePluginId` plus page `workbench`.

When `page === "workbench"`, resolve the active manifest, runtime reference, registered workflow runtime, selected session, and saved/default configuration. Render `PluginWorkbench`; if any invariant is missing, return to the market rather than rendering a fake workbench.

- [ ] **Step 5: Make marketplace actions truthful and compact**

Change `PluginMarket` to accept:

```ts
runtimeEntries: ReadonlySet<string>;
onOpenPlugin(pluginId: string): void;
```

Keep only `已安装` and `发现` tabs. Remove breadcrumb back/forward, top recovery, refresh, and help controls. Keep search. Move safe mode to one quiet catalog-footer action labeled `插件出现问题？进入恢复模式` or `退出恢复模式`.

For each manifest:

- Runtime reference missing or entry absent: state `尚未接入`; no install, enable, or open button.
- Runnable and uninstalled: `安装设备体检`.
- Installed and disabled: `启用设备体检`.
- Installed and enabled: one blue `打开设备体检工作台` action.

Remove `自定义插件位置`; delete the LayoutEditor route, files, imports, and related CSS selectors.

- [ ] **Step 6: Simplify device and application chrome**

In `AppShell.tsx`:

- change nav label `插件` to `插件市场`;
- remove `window-controls`, `核心（不可移除）`, both lock icons, `rail-principle`, and the entire `safety-strip` footer;
- remove the `safeMode` prop;
- keep one device connection button and the multiple-device selector.

In `DeviceOverview.tsx`:

- remove the toolbar refresh icon and the entire `quick-actions` row;
- remove report export and AI readiness props/sections;
- retain device connection, measured facts, and one `浏览插件市场` secondary action;
- keep the connection panel's primary detect/read action because device connection is a kernel responsibility.

Delete the now-unused report export/readiness code from `App.tsx`; report and AI remain catalog concepts without runnable actions until their runtime tasks are implemented in a subsequent goal.

- [ ] **Step 7: Run desktop tests and full type/lint checks**

Run:

```bash
pnpm --filter @opendevice/desktop test
pnpm --filter @opendevice/desktop lint
pnpm --filter @opendevice/desktop typecheck
```

Expected: all desktop tests pass, deleted LayoutEditor tests are absent from the count, and lint/typecheck exit 0.

- [ ] **Step 8: Commit the plugin-first integration**

Run:

```bash
git add apps/desktop/src/App.tsx \
  apps/desktop/src/App.test.tsx \
  apps/desktop/src/components/AppShell.tsx \
  apps/desktop/src/features/overview/DeviceOverview.tsx \
  apps/desktop/src/features/plugins/PluginMarket.tsx \
  apps/desktop/src/features/plugins/PluginMarket.test.tsx \
  apps/desktop/src/features/plugins/PluginDetail.tsx \
  apps/desktop/src/features/plugins/LayoutEditor.tsx \
  apps/desktop/src/features/plugins/LayoutEditor.test.tsx \
  apps/desktop/src/styles.css
git commit -m "feat(desktop): integrate plugin-first workbench"
```

---

### Task 6: Lock responsive visual behavior and browser acceptance

**Files:**
- Modify: `DESIGN.md`
- Modify: `apps/desktop/e2e/app.spec.ts`
- Modify: `apps/desktop/src/styles.css`
- Update: `docs/verification/screenshots/desktop-overview.png`
- Update: `docs/verification/screenshots/plugin-market.png`
- Create: `docs/verification/screenshots/plugin-workbench-1280x800.png`
- Create: `docs/verification/screenshots/plugin-workbench-1024x800.png`

**Interfaces:**
- Consumes: final shell, market, and workbench UI.
- Produces: responsive layout proof, keyboard-focus proof, screenshot evidence, and reusable v3 project rules.

- [ ] **Step 1: Write failing Playwright checks for both target widths**

Replace the old accepted-surface test with a loop over exact viewports:

```ts
for (const width of [1280, 1024]) {
  test(`plugin-first shell fits ${width}x800`, async ({ page }) => {
    await page.setViewportSize({ width, height: 800 });
    await page.goto("/");
    await expect(page.getByRole("button", { name: "插件市场" })).toBeVisible();
    await page.getByRole("button", { name: "插件市场" }).click();
    await expect(page.getByRole("heading", { name: "插件市场" })).toBeVisible();

    const overflow = await page.evaluate(() => ({
      body: document.body.scrollWidth - document.body.clientWidth,
      root: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    }));
    expect(overflow).toEqual({ body: 0, root: 0 });
  });
}
```

Add a keyboard case that tabs to `插件市场`, presses Enter, tabs into search, and verifies the focused element has a visible outline width greater than zero using `getComputedStyle(document.activeElement!).outlineWidth`.

- [ ] **Step 2: Run E2E and verify RED at 1024px or on changed labels**

Run:

```bash
pnpm --filter @opendevice/desktop test:e2e
```

Expected: at least one new assertion fails before the responsive/style adjustment.

- [ ] **Step 3: Finish responsive CSS without changing the information architecture**

At widths at or below 1100px:

- device rail width becomes 238px;
- catalog list width becomes 300px;
- plugin metadata switches from five columns to two columns;
- workbench result rows keep labels at 150px and allow values to wrap;
- no fixed minimum width may force horizontal page scrolling.

Keep the catalog list and workbench vertically scrollable inside the application window. Add visible focus outlines with `outline: 2px solid #1677e8` and `outline-offset: 2px`.

- [ ] **Step 4: Capture deterministic browser screenshots**

In Playwright, capture:

```ts
import path from "node:path";

const verificationDir = path.resolve(
  import.meta.dirname,
  "../../../docs/verification/screenshots",
);

await page.screenshot({
  path: path.join(verificationDir, `plugin-workbench-${width}x800.png`),
  fullPage: true,
});
```

Use the browser disconnected state only for responsive structure evidence; do not label it as native device proof. Refresh `desktop-overview.png` and `plugin-market.png` from their corresponding routes.

- [ ] **Step 5: Update DESIGN.md with the accepted plugin-workbench rules**

Add a `v3 plugin workbench` section recording:

- device rail + market + workbench hierarchy;
- truthful runtime availability rule;
- one primary action per screen;
- no persistent safety slogans or fake desktop controls;
- generic schema-rendered configuration and results;
- 1280×800 and 1024×800 responsive behavior;
- browser screenshots as structure evidence only, with native proof required separately.

- [ ] **Step 6: Run browser and workspace verification**

Run:

```bash
git diff --check
pnpm --filter @opendevice/desktop test:e2e
pnpm check
cargo fmt --manifest-path apps/desktop/src-tauri/Cargo.toml -- --check
cargo test --manifest-path apps/desktop/src-tauri/Cargo.toml
```

Expected: all commands exit 0; both viewport tests and the keyboard-focus test pass.

- [ ] **Step 7: Commit responsive evidence and design rules**

Run:

```bash
git add DESIGN.md \
  apps/desktop/e2e/app.spec.ts \
  apps/desktop/src/styles.css \
  docs/verification/screenshots/desktop-overview.png \
  docs/verification/screenshots/plugin-market.png \
  docs/verification/screenshots/plugin-workbench-1280x800.png \
  docs/verification/screenshots/plugin-workbench-1024x800.png
git commit -m "test(ui): verify plugin workbench surfaces"
```

---

### Task 7: Build and verify the native nova 7 plugin loop

**Files:**
- Create: `docs/verification/screenshots/plugin-workbench-native.png`
- Modify: `DESIGN.md`

**Interfaces:**
- Consumes: the signed Tauri bundle, connected HUAWEI CDL-AN50, and the completed plugin workbench.
- Produces: native runtime proof and final requirement-by-requirement Goal audit.

- [ ] **Step 1: Run a privacy-safe preflight**

Run without printing the device serial:

```bash
state="$(/opt/homebrew/bin/adb get-state 2>/dev/null || true)"
ready_count="$(/opt/homebrew/bin/adb devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
printf 'adb_state=%s\nready_devices=%s\n' "$state" "$ready_count"
```

Expected: `adb_state=device` and `ready_devices=1`. If not, keep the goal active and report the current connection state; do not run Root, flash, install, or authorization-bypass commands.

- [ ] **Step 2: Run the complete fresh verification suite**

Run:

```bash
git diff --check
pnpm check
cargo fmt --manifest-path apps/desktop/src-tauri/Cargo.toml -- --check
cargo test --manifest-path apps/desktop/src-tauri/Cargo.toml
pnpm --filter @opendevice/desktop test:e2e
```

Expected: every command exits 0 with zero failed tests.

- [ ] **Step 3: Build and verify distributable artifacts**

Close the old native application, then run:

```bash
pnpm --filter @opendevice/desktop tauri build --bundles app,dmg
codesign --verify --deep --strict --verbose=2 \
  "apps/desktop/src-tauri/target/release/bundle/macos/OpenDevice Forge.app"
hdiutil verify \
  "apps/desktop/src-tauri/target/release/bundle/dmg/OpenDevice Forge_0.1.0_aarch64.dmg"
shasum -a 256 \
  "apps/desktop/src-tauri/target/release/bundle/dmg/OpenDevice Forge_0.1.0_aarch64.dmg"
```

Expected: build exits 0, codesign says valid on disk and satisfies its designated requirement, DMG checksum is valid, and SHA-256 is printed for handoff. Record that this is ad-hoc signed and not notarized.

- [ ] **Step 4: Verify the real plugin loop through the native window**

Using the Computer Use skill and the newly built `.app`:

1. Confirm the device rail says `HUAWEI CDL-AN50` and `真机已连接`.
2. Open `插件市场`; confirm removed controls and safety slogans are absent.
3. Install `设备体检`, enable it, and open its workbench.
4. Open `配置`, disable `电源状态`, return to `运行`, and press `开始检测`.
5. Confirm result groups contain `设备身份`, `性能基础`, and `系统状态`, and do not contain `电源状态` or `100%`.
6. Re-enable all four groups, run again, and confirm `CDL-AN50`, Android `10`, `arm64-v8a`, approximately `7.4 GB` RAM, approximately `80.9 GB` storage, `100%`, and no Root signal.
7. Restart the app and confirm all-four-groups configuration remains selected.

Capture the final native workbench result as `docs/verification/screenshots/plugin-workbench-native.png`. Do not include or expose the ADB serial.

- [ ] **Step 5: Update the verified project rule**

In `DESIGN.md`, mark the v3 workbench rule verified and record:

- component, Playwright, build, signing, DMG, and native nova 7 evidence;
- the exact verified widths;
- the limitation that only the built-in device-inspection workflow has a runtime in this Goal;
- the boundary that the service contract is typed but no local executable or public gateway was started.

- [ ] **Step 6: Run the final completion audit**

Check each requirement against authoritative evidence:

```bash
git status --short
git log --oneline -8
rg -n "核心（不可移除）|默认只读|只读模式|插件帮助|刷新目录" \
  apps/desktop/src apps/desktop/e2e
rg -n "kind: \"service\"|localOnly: true|fixedArguments: true" \
  packages/core/src
```

Expected: removed UI strings have no active source matches; service boundaries are present; the only remaining worktree change is the native verification screenshot and DESIGN evidence from this task.

- [ ] **Step 7: Commit native evidence**

Run:

```bash
git add DESIGN.md docs/verification/screenshots/plugin-workbench-native.png
git commit -m "docs: record native plugin workbench verification"
```

Expected: commit succeeds and `git status --short` is empty.
