import { describe, expect, it } from "vitest";
import { detectPlatform, preferredDownload } from "./platform";

describe("platform detection", () => {
  it.each([
    ["MacIntel", "macos"],
    ["macOS", "macos"],
    ["Win32", "windows"],
    ["Windows", "windows"],
    ["Linux x86_64", "other"],
    ["", "other"],
    [undefined, "other"],
  ] as const)("maps %s to %s", (value, platform) => {
    expect(detectPlatform(value)).toBe(platform);
  });

  it("returns an honest unavailable release choice", () => {
    expect(preferredDownload("macos")).toEqual({
      label: "macOS",
      available: false,
      reason: "等待首个已验证发布包",
    });
    expect(preferredDownload("windows").label).toBe("Windows");
    expect(preferredDownload("other").label).toBe("选择平台");
  });
});
