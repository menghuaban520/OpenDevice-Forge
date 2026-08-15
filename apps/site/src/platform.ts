export type Platform = "macos" | "windows" | "other";

export interface DownloadChoice {
  label: string;
  available: false;
  reason: string;
}

export function detectPlatform(value?: string): Platform {
  const normalized = (value ?? "").toLowerCase();

  if (normalized.includes("mac")) return "macos";
  if (normalized.includes("win")) return "windows";
  return "other";
}

export function detectBrowserPlatform(): Platform {
  const browser = navigator as Navigator & {
    userAgentData?: { platform?: string };
  };

  return detectPlatform(
    browser.userAgentData?.platform || navigator.platform || navigator.userAgent,
  );
}

export function preferredDownload(platform: Platform): DownloadChoice {
  const labels: Record<Platform, string> = {
    macos: "macOS",
    windows: "Windows",
    other: "选择平台",
  };

  return {
    label: labels[platform],
    available: false,
    reason: "等待首个已验证发布包",
  };
}
