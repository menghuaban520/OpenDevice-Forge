import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { App } from "../../App";

describe("Layout Editor", () => {
  it("edits visibility within a named profile without changing permissions", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: "插件市场" }));
    await user.click(screen.getByRole("button", { name: "编辑全部插件布局" }));

    expect(screen.getByRole("heading", { name: "界面与插件布局" })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("布局方案"), "daily");
    const overviewToggle = screen.getByRole("switch", { name: "AI 节点：设备总览信息区" });
    expect(overviewToggle).not.toBeChecked();
    await user.click(overviewToggle);
    expect(overviewToggle).toBeChecked();
    expect(screen.getByText("布局编辑不会增加插件权限")).toBeInTheDocument();
    expect(screen.getByText(/未保存的更改/)).toBeInTheDocument();
  });

  it("restores the accepted default layout", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: "插件市场" }));
    await user.click(screen.getByRole("button", { name: "编辑全部插件布局" }));
    await user.click(screen.getByRole("button", { name: "恢复默认" }));
    expect(screen.getByText("已恢复默认布局")).toBeInTheDocument();
  });
});
