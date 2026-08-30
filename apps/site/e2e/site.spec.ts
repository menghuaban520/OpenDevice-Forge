import { expect, test } from "@playwright/test";
import { resolve } from "node:path";

const screenshotDir = resolve(process.cwd(), "../../docs/verification/screenshots");

test("desktop site presents the product without claiming a public release", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/");

  await expect(page.getByRole("heading", { name: /让旧安卓手机/ })).toBeVisible();
  await expect(page.getByText("等待首个已验证发布包")).toBeVisible();
  await expect(page.getByRole("button", { name: /版本/ })).toBeDisabled();
  await expect(page.getByText("当前没有公开发行包")).toBeVisible();
  await expect(page.locator("img[alt='OpenDevice Forge 模块中心实装截图']")).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

  await page.screenshot({ path: resolve(screenshotDir, "site-desktop.png"), fullPage: true });
});

test("platform choice remains honest and mobile layout does not overflow", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");

  await page.getByRole("button", { name: "选择其他平台" }).click();
  await page.getByRole("button", { name: "Windows" }).click();
  await expect(page.getByText("Windows 版本")).toBeVisible();
  await expect(page.getByText("等待首个已验证发布包")).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

  await page.screenshot({ path: resolve(screenshotDir, "site-mobile.png"), fullPage: true });
});
