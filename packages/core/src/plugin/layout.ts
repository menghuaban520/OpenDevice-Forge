import { PLACEMENT_SLOTS } from "./types";
import type {
  LayoutProfile,
  LayoutScope,
  PlacementEntry,
  PlacementSlot,
} from "./types";

const emptyPlacements = (): LayoutProfile["placements"] => ({
  sidebar: [],
  overview: [],
  shortcut: [],
  service: [],
  report: [],
  context: [],
});

const cloneProfile = (profile: LayoutProfile): LayoutProfile => ({
  ...profile,
  scope:
    profile.scope.kind === "named"
      ? { kind: "named", deviceIds: [...profile.scope.deviceIds] }
      : { ...profile.scope },
  placements: Object.fromEntries(
    PLACEMENT_SLOTS.map((slot) => [
      slot,
      profile.placements[slot].map((entry) => ({ ...entry })),
    ]),
  ) as LayoutProfile["placements"],
});

export const createLayoutProfile = (
  id: string,
  name: string,
  scope: LayoutScope,
): LayoutProfile => ({ id, name, scope, placements: emptyPlacements() });

export const setPlacement = (
  profile: LayoutProfile,
  slot: PlacementSlot,
  pluginId: string,
  options: { visible: boolean; index?: number },
): LayoutProfile => {
  const next = cloneProfile(profile);
  const entries = next.placements[slot];
  const existingIndex = entries.findIndex((entry) => entry.pluginId === pluginId);
  let entry: PlacementEntry = { pluginId, visible: options.visible };
  if (existingIndex >= 0) {
    entry = { ...entries[existingIndex], visible: options.visible } as PlacementEntry;
    entries.splice(existingIndex, 1);
  }
  const requestedIndex = options.index ?? entries.length;
  const targetIndex = Math.max(0, Math.min(requestedIndex, entries.length));
  entries.splice(targetIndex, 0, entry);
  return next;
};

export const movePlacement = (
  profile: LayoutProfile,
  slot: PlacementSlot,
  pluginId: string,
  index: number,
): LayoutProfile => {
  const entry = profile.placements[slot].find((item) => item.pluginId === pluginId);
  if (!entry) return cloneProfile(profile);
  return setPlacement(profile, slot, pluginId, { visible: entry.visible, index });
};

export const visiblePlugins = (
  profile: LayoutProfile,
  slot: PlacementSlot,
): string[] =>
  profile.placements[slot]
    .filter((entry) => entry.visible)
    .map((entry) => entry.pluginId);

export const resolveLayoutProfile = (
  profiles: LayoutProfile[],
  context: { deviceId: string; currentDeviceId?: string },
): LayoutProfile => {
  const named = profiles.find(
    (profile) =>
      profile.scope.kind === "named" && profile.scope.deviceIds.includes(context.deviceId),
  );
  if (named) return named;
  const current = profiles.find(
    (profile) =>
      profile.scope.kind === "current" && context.currentDeviceId === context.deviceId,
  );
  if (current) return current;
  const all = profiles.find((profile) => profile.scope.kind === "all");
  if (!all) throw new Error("layout_profile_missing");
  return all;
};

export const resetLayoutProfile = (
  _profile: LayoutProfile,
  defaults: LayoutProfile,
): LayoutProfile => cloneProfile(defaults);

