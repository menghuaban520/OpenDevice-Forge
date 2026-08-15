import { useState } from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  BUILTIN_PLUGINS,
  type PluginConfiguration,
  type WorkflowRuntimeDefinition,
} from "@opendevice/core";
import {
  ALL_INSPECTION_GROUPS,
  type DeviceClient,
  type DeviceInspection,
  type InspectionSelection,
} from "../../lib/device-client";
import { PluginWorkbench } from "./PluginWorkbench";
import {
  createDeviceInspectionRuntime,
  DEFAULT_INSPECTION_CONFIG,
} from "./runtime/device-inspection";
import type { DesktopPluginContext } from "./runtime/registry";

const inspectionManifest = BUILTIN_PLUGINS.find(
  (manifest) => manifest.id === "dev.opendevice.device-inspection",
)!;

const inspectionFixture: DeviceInspection = {
  manufacturer: "HUAWEI",
  productName: "CDL-AN50",
  model: "CDL-AN50",
  androidVersion: "10",
  abi: "arm64-v8a",
  ramBytes: 7_749_536 * 1024,
  storageAvailableBytes: 84_801_144 * 1024,
  batteryPercent: 100,
  rootSignals: [],
};

const clientWithInspection = (
  inspectDevice: DeviceClient["inspectDevice"],
): DeviceClient => ({
  probeAdb: async () => ({ available: true, source: "path" }),
  listDevices: async () => [],
  probeUsbDevice: async () => null,
  inspectDevice,
});

function InspectionHarness({
  client,
  connected = true,
}: {
  client: DeviceClient;
  connected?: boolean;
}) {
  const [configuration, setConfiguration] = useState(DEFAULT_INSPECTION_CONFIG);
  return (
    <PluginWorkbench
      manifest={inspectionManifest}
      runtime={createDeviceInspectionRuntime(client)}
      context={{ sessionSerial: connected ? "session-only" : null }}
      configuration={configuration}
      onConfigurationChange={setConfiguration}
      canRun={connected}
      unavailableReason="请先连接一台已授权的 Android 手机"
    />
  );
}

describe("PluginWorkbench", () => {
  it("runs a real device plugin with edited configuration and renders only selected results", async () => {
    const user = userEvent.setup();
    const selections: InspectionSelection[] = [];
    const client = clientWithInspection(async (_serial, selection = ALL_INSPECTION_GROUPS) => {
      selections.push(selection);
      return inspectionFixture;
    });
    render(<InspectionHarness client={client} />);

    await user.click(screen.getByRole("tab", { name: "配置" }));
    await user.click(screen.getByRole("checkbox", { name: /电源状态/ }));
    await user.click(screen.getByRole("tab", { name: "运行" }));
    await user.click(screen.getByRole("button", { name: "运行设备体检" }));

    expect(await screen.findByText("HUAWEI")).toBeInTheDocument();
    expect(screen.getByText("设备身份")).toBeInTheDocument();
    expect(screen.queryByText("电源状态")).not.toBeInTheDocument();
    expect(selections).toEqual([{
      identity: true,
      performance: true,
      power: false,
      system: true,
    }]);
  });

  it("disables execution while the device is disconnected", () => {
    render(<InspectionHarness client={clientWithInspection(async () => inspectionFixture)} connected={false} />);

    expect(screen.getByRole("button", { name: "运行设备体检" })).toBeDisabled();
    expect(screen.getByText("请先连接一台已授权的 Android 手机")).toBeInTheDocument();
  });

  it("shows a useful failure state and retries the same workflow", async () => {
    const user = userEvent.setup();
    const inspectDevice = vi.fn()
      .mockRejectedValueOnce(new Error("adb_failed"))
      .mockResolvedValueOnce(inspectionFixture);
    render(<InspectionHarness client={clientWithInspection(inspectDevice)} />);

    await user.click(screen.getByRole("button", { name: "运行设备体检" }));
    expect(await screen.findByText("运行失败，请检查连接后重试")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "重试" }));

    expect(await screen.findByText("HUAWEI")).toBeInTheDocument();
    expect(inspectDevice).toHaveBeenCalledTimes(2);
  });

  it("prevents duplicate runs and aborts the active workflow when unmounted", async () => {
    const user = userEvent.setup();
    let observedSignal: AbortSignal | undefined;
    const execute = vi.fn(({ signal }: { signal: AbortSignal }) => {
      observedSignal = signal;
      return new Promise<never>(() => undefined);
    });
    const runtime = {
      ...createDeviceInspectionRuntime(clientWithInspection(async () => inspectionFixture)),
      execute,
    } as WorkflowRuntimeDefinition<DesktopPluginContext>;
    const { unmount } = render(
      <PluginWorkbench
        manifest={inspectionManifest}
        runtime={runtime}
        context={{ sessionSerial: "session-only" }}
        configuration={DEFAULT_INSPECTION_CONFIG}
        onConfigurationChange={() => undefined}
        canRun
      />,
    );

    const runButton = screen.getByRole("button", { name: "运行设备体检" });
    await user.click(runButton);
    await waitFor(() => expect(execute).toHaveBeenCalledTimes(1));
    expect(runButton).toBeDisabled();
    await user.click(runButton);
    expect(execute).toHaveBeenCalledTimes(1);

    unmount();
    expect(observedSignal?.aborted).toBe(true);
  });

  it("renders every schema field type and updates the exact configuration", async () => {
    const user = userEvent.setup();
    const runtime: WorkflowRuntimeDefinition<DesktopPluginContext> = {
      pluginId: inspectionManifest.id,
      kind: "workflow",
      configuration: {
        version: "1",
        fields: [
          { id: "enabled", type: "toggle", label: "启用缓存" },
          { id: "count", type: "number", label: "并发数", min: 1, max: 8 },
          { id: "label", type: "text", label: "节点名称", placeholder: "我的节点" },
          {
            id: "mode",
            type: "select",
            label: "运行模式",
            options: [
              { value: "safe", label: "稳妥" },
              { value: "fast", label: "快速" },
            ],
          },
        ],
      },
      defaultConfiguration: {
        enabled: false,
        count: 2,
        label: "旧手机",
        mode: "safe",
      },
      validate: () => ({}),
      execute: async () => ({ groups: [] }),
    };
    let latest: PluginConfiguration = runtime.defaultConfiguration;

    function SchemaHarness() {
      const [configuration, setConfiguration] = useState(runtime.defaultConfiguration);
      const update = (next: PluginConfiguration) => {
        latest = next;
        setConfiguration(next);
      };
      return (
        <PluginWorkbench
          manifest={inspectionManifest}
          runtime={runtime}
          context={{ sessionSerial: "session-only" }}
          configuration={configuration}
          onConfigurationChange={update}
          canRun
        />
      );
    }

    render(<SchemaHarness />);
    await user.click(screen.getByRole("tab", { name: "配置" }));
    await user.click(screen.getByRole("switch", { name: "启用缓存" }));
    const count = screen.getByRole("spinbutton", { name: "并发数" });
    await user.clear(count);
    await user.type(count, "5");
    const label = screen.getByRole("textbox", { name: "节点名称" });
    await user.clear(label);
    await user.type(label, "nova7 节点");
    await user.selectOptions(screen.getByRole("combobox", { name: "运行模式" }), "fast");

    expect(latest).toEqual({
      enabled: true,
      count: 5,
      label: "nova7 节点",
      mode: "fast",
    });
  });

  it("surfaces schema validation before starting a workflow", async () => {
    const user = userEvent.setup();
    const client = clientWithInspection(async () => inspectionFixture);
    render(
      <PluginWorkbench
        manifest={inspectionManifest}
        runtime={createDeviceInspectionRuntime(client)}
        context={{ sessionSerial: "session-only" }}
        configuration={{ groups: [] }}
        onConfigurationChange={() => undefined}
        canRun
      />,
    );

    await user.click(screen.getByRole("button", { name: "运行设备体检" }));
    await act(async () => undefined);
    expect(screen.getByText("至少选择一个检测项目")).toBeInTheDocument();
  });
});
