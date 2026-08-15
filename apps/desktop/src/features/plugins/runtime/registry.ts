import type { WorkflowRuntimeDefinition } from "@opendevice/core";
import type { DeviceClient } from "../../../lib/device-client";
import {
  createDeviceInspectionRuntime,
  DEVICE_INSPECTION_ENTRY,
} from "./device-inspection";

export interface DesktopPluginContext {
  sessionSerial: string | null;
}

export const createDesktopPluginRuntimes = (
  client: DeviceClient,
): ReadonlyMap<string, WorkflowRuntimeDefinition<DesktopPluginContext>> =>
  new Map([
    [DEVICE_INSPECTION_ENTRY, createDeviceInspectionRuntime(client)],
  ]);
