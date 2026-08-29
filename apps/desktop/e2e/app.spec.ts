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
  await expect(page.getByText("电脑尚未安装 ADB").first()).toBeVisible();
  await page.screenshot({ path: resolve(screenshotDirectory, "desktop-overview.png") });

  await page.getByRole("button", { name: "模块", exact: true }).click();
  await expect(page.getByText("随应用提供 · 本地清单")).toBeVisible();
  await page.screenshot({ path: resolve(screenshotDirectory, "module-center.png") });

  await page.getByRole("button", { name: "安装设备体检" }).click();
  const inspectionToggle = page.getByRole("switch", { name: "启用设备体检" });
  await expect(inspectionToggle).not.toBeChecked();
  await expect(page.getByRole("button", { name: "打开设备体检" })).toBeDisabled();
  await inspectionToggle.click();
  await page.getByRole("button", { name: "打开设备体检" }).click();

  await expect(page.getByRole("heading", { name: "设备体检" })).toBeVisible();
  await expect(page.getByText("请先连接一台已授权的 Android 手机")).toBeVisible();
  await expect(page.getByRole("button", { name: "运行设备体检" })).toBeDisabled();
  await page.screenshot({ path: resolve(screenshotDirectory, "device-inspection-workbench.png") });

  await page.getByRole("button", { name: "返回模块中心" }).click();
  await expect(page.getByRole("button", { name: "AI 节点 模块" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "安装 AI 节点" })).toHaveCount(0);

  await page.setViewportSize({ width: 1280, height: 800 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: resolve(screenshotDirectory, "module-center-1280x800.png") });
});
