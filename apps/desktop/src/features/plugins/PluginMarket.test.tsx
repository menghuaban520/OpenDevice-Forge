import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { App } from "../../App";
import {
  ALL_INSPECTION_GROUPS,
  type DeviceClient,
  type InspectionSelection,
} from "../../lib/device-client";

const createConnectedClient = () => {
  const selections: InspectionSelection[] = [];
  const inspectDevice = vi.fn(async (_serial: string, selection = ALL_INSPECTION_GROUPS) => {
    selections.push(selection);
    return {
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
  });
  const client: DeviceClient = {
    probeAdb: async () => ({ available: true, source: "path" }),
    listDevices: async () => [{
      sessionSerial: "session-only",
      transport: "ready",
      model: "CDL-AN50",
      product: "CDL-AN50",
    }],
    inspectDevice,
  };
  return { client, inspectDevice, selections };
};

const installEnableAndOpenInspection = async (
  user: ReturnType<typeof userEvent.setup>,
) => {
  await user.click(screen.getByRole("button", { name: "模块" }));
  await user.click(screen.getByRole("button", { name: "设备体检 模块" }));
  await user.click(screen.getByRole("button", { name: "安装设备体检" }));
  const toggle = screen.getByRole("switch", { name: "启用设备体检" });
  expect(toggle).not.toBeChecked();
  expect(screen.getByRole("button", { name: "打开设备体检" })).toBeDisabled();
  await user.click(toggle);
  await user.click(screen.getByRole("button", { name: "打开设备体检" }));
};

describe("Plugin Market", () => {
  it("installs, enables, opens, configures and runs the real inspection plugin", async () => {
    const user = userEvent.setup();
    const { client, selections } = createConnectedClient();
    render(<App deviceClient={client} />);
    await waitFor(() => expect(screen.getAllByText("HUAWEI CDL-AN50").length).toBeGreaterThan(0));

    await installEnableAndOpenInspection(user);
    expect(screen.getByRole("heading", { name: "设备体检" })).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: "配置" }));
    await user.click(screen.getByRole("checkbox", { name: /电源状态/ }));
    await user.click(screen.getByRole("tab", { name: "运行" }));
    await user.click(screen.getByRole("button", { name: "运行设备体检" }));

    expect(await screen.findByText("HUAWEI")).toBeInTheDocument();
    expect(screen.queryByText("电源状态")).not.toBeInTheDocument();
    expect(selections.at(-1)).toEqual({
      identity: true,
      performance: true,
      power: false,
      system: true,
    });
  });

  it("persists plugin lifecycle and configuration across an app restart", async () => {
    const user = userEvent.setup();
    const { client } = createConnectedClient();
    const first = render(<App deviceClient={client} />);
    await waitFor(() => expect(screen.getAllByText("HUAWEI CDL-AN50").length).toBeGreaterThan(0));
    await installEnableAndOpenInspection(user);
    await user.click(screen.getByRole("tab", { name: "配置" }));
    await user.click(screen.getByRole("checkbox", { name: /电源状态/ }));
    await waitFor(() => expect(window.localStorage.getItem("opendevice.plugin-config.v1")).toContain("identity"));
    first.unmount();

    render(<App deviceClient={client} />);
    await user.click(screen.getByRole("button", { name: "模块" }));
    await user.click(screen.getByRole("tab", { name: "已安装" }));
    await user.click(screen.getByRole("button", { name: "设备体检 模块" }));
    expect(screen.getByRole("switch", { name: "启用设备体检" })).toBeChecked();
    await user.click(screen.getByRole("button", { name: "打开设备体检" }));
    await user.click(screen.getByRole("tab", { name: "配置" }));
    expect(screen.getByRole("checkbox", { name: /电源状态/ })).not.toBeChecked();
  });

  it("hides catalog ideas until they have a working runtime", async () => {
    const user = userEvent.setup();
    const { client } = createConnectedClient();
    render(<App deviceClient={client} />);
    await user.click(screen.getByRole("button", { name: "模块" }));
    expect(screen.queryByRole("button", { name: "AI 节点 模块" })).not.toBeInTheDocument();
    expect(screen.queryByText("尚未接入运行器")).not.toBeInTheDocument();
  });

  it("removes decorative market and layout controls with no implemented behavior", async () => {
    const user = userEvent.setup();
    const { client } = createConnectedClient();
    render(<App deviceClient={client} />);
    await user.click(screen.getByRole("button", { name: "模块" }));

    expect(screen.queryByRole("button", { name: "后退" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "前进" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "刷新目录" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "插件帮助" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "自定义插件位置" })).not.toBeInTheDocument();
  });
});
