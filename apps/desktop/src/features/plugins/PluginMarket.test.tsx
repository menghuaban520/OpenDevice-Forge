import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { App } from "../../App";

describe("Plugin Market", () => {
  it("discloses source and risk, then reversibly disables an installed plugin", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: "插件" }));
    await user.click(screen.getByRole("button", { name: /AI 节点/ }));

    expect(screen.getByText("OpenDevice Forge 官方目录")).toBeInTheDocument();
    expect(screen.getByText("低风险")).toBeInTheDocument();
    const toggle = screen.getByRole("switch", { name: "启用 AI 节点" });
    expect(toggle).toBeChecked();
    await user.click(toggle);
    expect(toggle).not.toBeChecked();
    expect(screen.getByText("已停用")).toBeInTheDocument();
  });

  it("does not invent generic settings for a plugin without configuration", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: "插件" }));
    await user.click(screen.getByRole("button", { name: /设备体检/ }));

    expect(screen.queryByText("配置预设")).not.toBeInTheDocument();
    expect(screen.queryByText("服务端口")).not.toBeInTheDocument();
    expect(screen.queryByText("启用 API 密钥")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "部署到手机" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "自定义插件位置" })).toBeInTheDocument();
  });

  it("keeps the kernel manager protected and installs a catalog plugin disabled", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: "插件" }));

    expect(screen.getByText("核心（不可移除）")).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: "发现" }));
    await user.click(screen.getByRole("button", { name: /Root 实验室/ }));
    expect(screen.getAllByText("仅展示风险与恢复知识，不执行 Root 或解锁。" ).length).toBeGreaterThan(0);
    await user.click(screen.getByRole("button", { name: "安装 Root 实验室" }));
    expect(screen.getByRole("switch", { name: "启用 Root 实验室" })).not.toBeChecked();
  });
});
