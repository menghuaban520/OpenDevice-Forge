import { expect, test } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

const screenshotDirectory = resolve(import.meta.dirname, "../../../docs/verification/screenshots");

test.beforeAll(async () => {
  await mkdir(screenshotDirectory, { recursive: true });
});

test("device-first desktop workflow and accepted surfaces", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "尚未连接设备" })).toBeVisible();
  await expect(page.getByText("缺少 Android 设备桥接")).toBeVisible();
  await expect(page.getByText("当前没有对手机执行任何修改")).toBeVisible();
  await page.screenshot({ path: resolve(screenshotDirectory, "desktop-overview.png") });

  await page.getByRole("button", { name: "插件", exact: true }).click();
  await expect(page.getByText("OpenDevice Forge 官方目录")).toBeVisible();
  await page.screenshot({ path: resolve(screenshotDirectory, "plugin-market.png") });

  const aiToggle = page.getByRole("switch", { name: "启用 AI 节点" });
  await expect(aiToggle).toBeChecked();
  await aiToggle.click();
  await expect(aiToggle).not.toBeChecked();
  await aiToggle.click();

  await page.getByRole("button", { name: "自定义插件位置" }).click();
  await expect(page.getByRole("heading", { name: "界面与插件布局" })).toBeVisible();
  await page.screenshot({ path: resolve(screenshotDirectory, "layout-editor.png") });

  await page.setViewportSize({ width: 1280, height: 800 });
  await expect(page.locator("body")).not.toHaveCSS("overflow-x", "auto");
  await page.screenshot({ path: resolve(screenshotDirectory, "layout-editor-1280x800.png") });
});
