import { describe, expect, it } from "vitest";
import {
  loadPluginConfigurations,
  PLUGIN_CONFIG_STORAGE_KEY,
  savePluginConfigurations,
} from "./storage";

class MemoryStorage implements Storage {
  readonly #values = new Map<string, string>();

  get length() {
    return this.#values.size;
  }

  clear() {
    this.#values.clear();
  }

  getItem(key: string) {
    return this.#values.get(key) ?? null;
  }

  key(index: number) {
    return [...this.#values.keys()][index] ?? null;
  }

  removeItem(key: string) {
    this.#values.delete(key);
  }

  setItem(key: string, value: string) {
    this.#values.set(key, value);
  }
}

describe("plugin configuration storage", () => {
  it("round-trips plugin configuration by plugin id", () => {
    const storage = new MemoryStorage();
    savePluginConfigurations(storage, {
      "dev.opendevice.device-inspection": {
        groups: ["identity", "system"],
      },
    });

    expect(loadPluginConfigurations(storage)).toEqual({
      "dev.opendevice.device-inspection": {
        groups: ["identity", "system"],
      },
    });
  });

  it("falls back to an empty record for malformed data", () => {
    const storage = new MemoryStorage();
    storage.setItem(PLUGIN_CONFIG_STORAGE_KEY, "not-json");

    expect(loadPluginConfigurations(storage)).toEqual({});
  });
});
