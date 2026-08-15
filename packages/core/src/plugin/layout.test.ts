import { describe, expect, it } from "vitest";
import {
  createLayoutProfile,
  movePlacement,
  resetLayoutProfile,
  resolveLayoutProfile,
  setPlacement,
  visiblePlugins,
} from "./layout";
import type { LayoutProfile, PlacementSlot } from "./types";

describe("plugin layout profiles", () => {
  it("places and orders plugins independently across every supported slot", () => {
    const slots: PlacementSlot[] = [
      "sidebar",
      "overview",
      "shortcut",
      "service",
      "report",
      "context",
    ];
    let profile = createLayoutProfile("default", "默认布局", { kind: "all" });

    for (const slot of slots) {
      profile = setPlacement(profile, slot, `${slot}.first`, { visible: true });
      profile = setPlacement(profile, slot, `${slot}.second`, { visible: true });
      profile = movePlacement(profile, slot, `${slot}.second`, 0);
      expect(visiblePlugins(profile, slot)).toEqual([
        `${slot}.second`,
        `${slot}.first`,
      ]);
    }
  });

  it("can hide a plugin without losing its configured position", () => {
    let profile = createLayoutProfile("default", "默认布局", { kind: "all" });
    profile = setPlacement(profile, "sidebar", "device.inspect", { visible: true });
    profile = setPlacement(profile, "sidebar", "device.inspect", { visible: false });

    expect(visiblePlugins(profile, "sidebar")).toEqual([]);
    expect(profile.placements.sidebar[0]).toEqual({
      pluginId: "device.inspect",
      visible: false,
    });
  });

  it("prefers a named-device profile, then current-device, then all-device", () => {
    const profiles: LayoutProfile[] = [
      createLayoutProfile("all", "所有设备", { kind: "all" }),
      createLayoutProfile("current", "当前设备", { kind: "current" }),
      createLayoutProfile("lab", "实验机", {
        kind: "named",
        deviceIds: ["demo-nova7"],
      }),
    ];

    expect(
      resolveLayoutProfile(profiles, {
        deviceId: "demo-nova7",
        currentDeviceId: "demo-nova7",
      }).id,
    ).toBe("lab");
    expect(
      resolveLayoutProfile(profiles, {
        deviceId: "another",
        currentDeviceId: "another",
      }).id,
    ).toBe("current");
    expect(resolveLayoutProfile(profiles, { deviceId: "offline" }).id).toBe("all");
  });

  it("resets a changed profile to a separate default snapshot", () => {
    let defaults = createLayoutProfile("default", "默认布局", { kind: "all" });
    defaults = setPlacement(defaults, "sidebar", "device.inspect", { visible: true });
    const changed = setPlacement(defaults, "sidebar", "ai.readiness", { visible: true });

    expect(resetLayoutProfile(changed, defaults)).toEqual(defaults);
    expect(changed.placements.sidebar).toHaveLength(2);
  });
});
