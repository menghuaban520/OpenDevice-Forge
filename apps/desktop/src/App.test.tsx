import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { App } from "./App";
import type { DeviceClient } from "./lib/device-client";

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
      model: "CDL-AN50",
      product: "nova 7 SE",
    },
  ],
  inspectDevice: async () => {
    throw new Error("unauthorized");
  },
};

describe("App", () => {
  it("shows an honest demo device when ADB is missing", async () => {
    render(<App deviceClient={missingAdbClient} />);
    expect(screen.getByText("HUAWEI nova 7 SE 5G 乐活版")).toBeInTheDocument();
    expect(screen.getAllByText(/演示视图 · 未连接真机/).length).toBeGreaterThan(0);
    await waitFor(() => expect(screen.getAllByText("电脑尚未安装 ADB").length).toBeGreaterThan(0));
    expect(screen.getByText(/当前没有对手机执行任何修改/)).toBeInTheDocument();
  });

  it("explains phone authorization instead of pretending inspection succeeded", async () => {
    render(<App deviceClient={unauthorizedClient} />);
    await waitFor(() => expect(screen.getAllByText("手机尚未允许 USB 调试").length).toBeGreaterThan(0));
    expect(screen.getAllByRole("button", { name: "重新检测" })[0]).toBeEnabled();
  });

  it("enters and exits safe mode from the persistent safety strip", async () => {
    const user = userEvent.setup();
    render(<App deviceClient={missingAdbClient} />);
    await user.click(screen.getByRole("button", { name: "进入安全模式" }));
    expect(screen.getByText("安全模式已开启，仅保留内核插件")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "退出安全模式" }));
    expect(screen.queryByText("安全模式已开启，仅保留内核插件")).not.toBeInTheDocument();
  });
});
