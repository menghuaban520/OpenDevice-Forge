import { describe, expect, it } from "vitest";
import type { PluginResultGroup } from "@opendevice/core";
import {
  ALL_INSPECTION_GROUPS,
  type DeviceClient,
  type DeviceInspection,
  type InspectionSelection,
} from "../../../lib/device-client";
import { createDeviceInspectionRuntime } from "./device-inspection";

const inspectionFixture: DeviceInspection = {
  manufacturer: "HUAWEI",
  productName: "CDL-AN50",
  model: "CDL-AN50",
  androidVersion: "10",
  abi: "arm64-v8a",
  ramBytes: 7_749_536 * 1024,
  storageAvailableBytes: 84_801_144 * 1024,
  batteryPercent: 100,
  rootSignals: [],
};

const resultIds = (groups: PluginResultGroup[]) => groups.map((group) => group.id);

describe("device inspection runtime", () => {
  it("executes only selected groups and returns only their result groups", async () => {
    const selections: InspectionSelection[] = [];
    const client: DeviceClient = {
      probeAdb: async () => ({ available: true, source: "path" }),
      listDevices: async () => [],
      probeUsbDevice: async () => null,
      inspectDevice: async (_serial, selection = ALL_INSPECTION_GROUPS) => {
        selections.push(selection);
        return inspectionFixture;
      },
    };
    const runtime = createDeviceInspectionRuntime(client);
    const result = await runtime.execute({
      context: { sessionSerial: "session-only" },
      configuration: { groups: ["identity", "power"] },
      signal: new AbortController().signal,
      onProgress: () => undefined,
    });

    expect(selections).toEqual([{
      identity: true,
      performance: false,
      power: true,
      system: false,
    }]);
    expect(resultIds(result.groups)).toEqual(["identity", "power"]);
    expect(result.groups[0]?.items.map((item) => item.value)).toEqual([
      "HUAWEI",
      "CDL-AN50",
      "CDL-AN50",
      "10",
      "arm64-v8a",
    ]);
    expect(result.groups[1]?.items[0]?.value).toBe("100%");
    expect(runtime.validate({ groups: [] })).toEqual({
      groups: "至少选择一个检测项目",
    });
  });
});
