import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { App } from "./App";
import {
  ALL_INSPECTION_GROUPS,
  type DeviceClient,
  type InspectionSelection,
} from "./lib/device-client";

const missingAdbClient: DeviceClient = {
  probeAdb: async () => ({ available: false, source: null }),
  listDevices: async () => [],
  inspectDevice: async () => {
    throw new Error("not_connected");
  },
};

const unauthorizedClient: DeviceClient = {
  probeAdb: async () => ({ available: true, source: "path" }),
  listDevices: async () => [
    {
      sessionSerial: "session-only",
      transport: "unauthorized",
      model: "Xiaomi 15",
      product: "dada",
    },
  ],
  inspectDevice: async () => {
    throw new Error("unauthorized");
  },
};

const pixelClient: DeviceClient = {
  probeAdb: async () => ({ available: true, source: "path" }),
  listDevices: async () => [{
    sessionSerial: "pixel-session",
    transport: "ready",
    model: "Pixel 9 Pro",
    product: "komodo",
  }],
  inspectDevice: async () => ({
    manufacturer: "Google",
    productName: "komodo",
    model: "Pixel 9 Pro",
    androidVersion: "16",
    abi: "arm64-v8a",
    ramBytes: 16 * 1024 ** 3,
    storageAvailableBytes: 120 * 1024 ** 3,
    batteryPercent: 82,
    rootSignals: [],
  }),
};

const partialSamsungClient: DeviceClient = {
  probeAdb: async () => ({ available: true, source: "path" }),
  listDevices: async () => [{
    sessionSerial: "samsung-session",
    transport: "ready",
    model: "SM-S918B",
    product: null,
  }],
  inspectDevice: async () => ({
    manufacturer: "samsung",
    productName: null,
    model: "SM-S918B",
    androidVersion: "15",
    abi: "arm64-v8a",
    ramBytes: null,
    storageAvailableBytes: null,
    batteryPercent: 71,
    rootSignals: [],
  }),
};

const usbVisibleHuaweiClient = {
  probeAdb: async () => ({ available: true, source: "path" as const }),
  listDevices: async () => [],
  probeUsbDevice: async () => ({ manufacturer: "HUAWEI", product: "CDL-AN50" }),
  inspectDevice: async () => {
    throw new Error("not_authorized");
  },
} as DeviceClient & {
  probeUsbDevice(): Promise<{ manufacturer: string | null; product: string | null } | null>;
};

describe("App", () => {
  it("shows a neutral Android state when ADB is missing", async () => {
    render(<App deviceClient={missingAdbClient} />);
    expect(screen.getByRole("heading", { name: "尚未连接设备" })).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText("电脑尚未安装 ADB").length).toBeGreaterThan(0));
    expect(screen.queryByText(/nova 7|CDL-AN50|HUAWEI/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/当前没有对手机执行任何修改/)).not.toBeInTheDocument();
  });

  it("uses measured identity for a Google phone everywhere", async () => {
    render(<App deviceClient={pixelClient} />);
    await waitFor(() => expect(screen.getAllByText("Google Pixel 9 Pro").length).toBeGreaterThan(0));
    expect(screen.getAllByText("komodo").length).toBeGreaterThan(0);
    expect(screen.queryByText(/nova 7|CDL-AN50|HUAWEI/i)).not.toBeInTheDocument();
  });

  it("asks the kernel device overview to read every inspection group", async () => {
    const selections: InspectionSelection[] = [];
    const client: DeviceClient = {
      ...pixelClient,
      inspectDevice: async (_sessionSerial, selection = ALL_INSPECTION_GROUPS) => {
        selections.push(selection);
        return pixelClient.inspectDevice("pixel-session");
      },
    };

    render(<App deviceClient={client} />);
    await waitFor(() => expect(screen.getAllByText("Google Pixel 9 Pro").length).toBeGreaterThan(0));
    expect(selections).toEqual([{
      identity: true,
      performance: true,
      power: true,
      system: true,
    }]);
  });

  it("does not fill missing Samsung fields from the Huawei reference phone", async () => {
    render(<App deviceClient={partialSamsungClient} />);
    await waitFor(() => expect(screen.getAllByText("samsung SM-S918B").length).toBeGreaterThan(0));
    expect(screen.queryByText(/nova 7|CDL-AN50|HUAWEI/i)).not.toBeInTheDocument();
    expect(screen.getAllByText("待读取").length).toBeGreaterThan(0);
  });

  it("lets the user select among multiple ready phones", async () => {
    const user = userEvent.setup();
    const inspectDevice = vi.fn(async (sessionSerial: string) => ({
      manufacturer: sessionSerial === "second-session" ? "OPPO" : "Google",
      productName: sessionSerial === "second-session" ? "PKJ110" : "komodo",
      model: sessionSerial === "second-session" ? "Find X8" : "Pixel 9 Pro",
      androidVersion: "15",
      abi: "arm64-v8a",
      ramBytes: 12 * 1024 ** 3,
      storageAvailableBytes: 64 * 1024 ** 3,
      batteryPercent: 80,
      rootSignals: [],
    }));
    const multiDeviceClient: DeviceClient = {
      probeAdb: async () => ({ available: true, source: "path" }),
      listDevices: async () => [
        { sessionSerial: "first-session", transport: "ready", model: "Pixel 9 Pro", product: "komodo" },
        { sessionSerial: "second-session", transport: "ready", model: "Find X8", product: "PKJ110" },
      ],
      inspectDevice,
    };

    render(<App deviceClient={multiDeviceClient} />);
    await waitFor(() => expect(screen.getAllByText("Google Pixel 9 Pro").length).toBeGreaterThan(0));
    await user.selectOptions(screen.getByRole("combobox", { name: "选择设备" }), "second-session");
    await waitFor(() => expect(screen.getAllByText("OPPO Find X8").length).toBeGreaterThan(0));
    expect(inspectDevice).toHaveBeenLastCalledWith("second-session", {
      identity: true,
      performance: true,
      power: true,
      system: true,
    });
  });

  it("keeps the latest phone selected when an earlier inspection finishes late", async () => {
    const user = userEvent.setup();
    let finishFirst!: (inspection: Awaited<ReturnType<DeviceClient["inspectDevice"]>>) => void;
    const firstInspection = new Promise<Awaited<ReturnType<DeviceClient["inspectDevice"]>>>((resolve) => {
      finishFirst = resolve;
    });
    const raceClient: DeviceClient = {
      probeAdb: async () => ({ available: true, source: "path" }),
      listDevices: async () => [
        { sessionSerial: "slow-session", transport: "ready", model: "Pixel 9 Pro", product: "komodo" },
        { sessionSerial: "fast-session", transport: "ready", model: "Find X8", product: "PKJ110" },
      ],
      inspectDevice: async (sessionSerial) => sessionSerial === "slow-session" ? firstInspection : {
        manufacturer: "OPPO",
        productName: "PKJ110",
        model: "Find X8",
        androidVersion: "15",
        abi: "arm64-v8a",
        ramBytes: 12 * 1024 ** 3,
        storageAvailableBytes: 64 * 1024 ** 3,
        batteryPercent: 80,
        rootSignals: [],
      },
    };

    render(<App deviceClient={raceClient} />);
    await user.selectOptions(await screen.findByRole("combobox", { name: "选择设备" }), "fast-session");
    await waitFor(() => expect(screen.getAllByText("OPPO Find X8").length).toBeGreaterThan(0));
    await act(async () => finishFirst({
      manufacturer: "Google",
      productName: "komodo",
      model: "Pixel 9 Pro",
      androidVersion: "16",
      abi: "arm64-v8a",
      ramBytes: 16 * 1024 ** 3,
      storageAvailableBytes: 120 * 1024 ** 3,
      batteryPercent: 82,
      rootSignals: [],
    }));

    expect(screen.getAllByText("OPPO Find X8").length).toBeGreaterThan(0);
    expect(screen.queryByText("Google Pixel 9 Pro")).not.toBeInTheDocument();
  });

  it("explains phone authorization instead of pretending inspection succeeded", async () => {
    render(<App deviceClient={unauthorizedClient} />);
    await waitFor(() => expect(screen.getAllByText("手机尚未允许 USB 调试").length).toBeGreaterThan(0));
    expect(screen.getAllByRole("button", { name: "重新检测" })[0]).toBeEnabled();
  });

  it("shows a USB-visible Huawei phone instead of saying no phone is connected", async () => {
    render(<App deviceClient={usbVisibleHuaweiClient} />);
    await waitFor(() => expect(screen.getAllByText("HUAWEI CDL-AN50").length).toBeGreaterThan(0));
    expect(screen.getAllByText("USB 已连接，等待调试授权").length).toBeGreaterThan(0);
    expect(screen.queryByText("未发现已连接的手机")).not.toBeInTheDocument();
  });

  it("keeps daily navigation focused on devices and plugins", async () => {
    render(<App deviceClient={missingAdbClient} />);
    expect(screen.getByRole("button", { name: "设备" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "模块" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "任务记录" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "设置" })).not.toBeInTheDocument();
    expect(screen.queryByText("核心（不可移除）")).not.toBeInTheDocument();
    expect(screen.queryByText("默认只读")).not.toBeInTheDocument();
  });
});
