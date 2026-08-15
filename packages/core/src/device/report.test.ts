import { describe, expect, it } from "vitest";
import { DEMO_NOVA7 } from "./demo";
import { assessReadiness } from "./readiness";
import { createDeviceReport, toJsonReport, toMarkdownReport } from "./report";

describe("device reports", () => {
  it("exports stable JSON with demo, source, unknown, and safety markers", () => {
    const snapshot = {
      ...DEMO_NOVA7,
      batteryPercent: { value: null, source: "unknown" as const },
    };
    const report = createDeviceReport(snapshot, assessReadiness(snapshot));
    const json = toJsonReport(report);

    expect(Object.keys(JSON.parse(json))).toEqual([
      "schemaVersion",
      "generatedAt",
      "mode",
      "safety",
      "device",
      "readiness",
    ]);
    expect(json).toContain('"mode": "demo"');
    expect(json).toContain('"source": "public_reference"');
    expect(json).toContain('"source": "unknown"');
    expect(json).toContain("只读");
    expect(json).not.toContain("serial");
  });

  it("exports readable Markdown without claiming a real Huawei inspection", () => {
    const report = createDeviceReport(DEMO_NOVA7, assessReadiness(DEMO_NOVA7));
    const markdown = toMarkdownReport(report);

    expect(markdown).toContain("# OpenDevice Forge 设备报告");
    expect(markdown).toContain("演示数据，不代表已连接真机");
    expect(markdown).toContain("公开参考");
    expect(markdown).toContain("不执行 Root、解锁、安装或文件提取");
    expect(markdown).not.toContain("IMEI");
  });
});
