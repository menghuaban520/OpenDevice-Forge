import assert from "node:assert/strict";
import test from "node:test";

import { redactEvidence } from "./collect-android-node-evidence.mjs";
import { findSecretLeaks } from "./verify-android-node-logs.mjs";
import { verifyClient } from "./verify-openai-client.mjs";

const fakeOpenAiClient = (calls) => ({
  models: {
    list: async () => {
      calls.push("models.list");
      return { data: [{ id: "qwen3-0.6b-q8_0" }] };
    },
  },
  chat: {
    completions: {
      create: async (request) => {
        if (!request.stream) {
          calls.push("chat.plain");
          return { choices: [{ message: { content: "手机节点正常" } }] };
        }
        calls.push("chat.stream");
        return (async function* stream() {
          yield { choices: [{ delta: { content: "一二" } }] };
          yield { choices: [{ delta: { content: "三" } }] };
        })();
      },
    },
  },
});

test("standard verifier exercises models, plain chat, and stream", async () => {
  const calls = [];
  const client = fakeOpenAiClient(calls);
  const result = await verifyClient(client);
  assert.equal(result.ok, true);
  assert.deepEqual(calls, ["models.list", "chat.plain", "chat.stream"]);
});

test("evidence redaction removes token and full adb serial", () => {
  const redacted = redactEvidence(
    "Authorization: Bearer raw-secret SERIAL-123 sk-test-token",
    ["raw-secret", "SERIAL-123"],
  );
  assert.equal(redacted.includes("raw-secret"), false);
  assert.equal(redacted.includes("SERIAL-123"), false);
  assert.equal(redacted.includes("sk-test-token"), false);
});

test("log scanner identifies prompt, token, and serial leaks", () => {
  assert.deepEqual(
    findSecretLeaks("你好 raw-secret SERIAL-123", ["你好", "raw-secret", "SERIAL-123"]),
    ["你好", "raw-secret", "SERIAL-123"],
  );
});
