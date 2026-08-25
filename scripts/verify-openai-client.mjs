import OpenAI from "openai";
import { pathToFileURL } from "node:url";

export async function verifyClient(client) {
  const models = await client.models.list();
  const model = models.data.at(0)?.id;
  if (!model) throw new Error("phone returned no ready model");

  const plain = await client.chat.completions.create({
    model,
    messages: [{ role: "user", content: "只回复：手机节点正常" }],
    max_tokens: 32,
  });
  const nonStreamContent = plain.choices[0]?.message.content;
  if (!nonStreamContent) throw new Error("empty non-stream response");

  const stream = await client.chat.completions.create({
    model,
    messages: [{ role: "user", content: "从一数到三" }],
    max_tokens: 32,
    stream: true,
  });
  let streamed = "";
  for await (const chunk of stream) {
    streamed += chunk.choices[0]?.delta.content ?? "";
  }
  if (!streamed) throw new Error("empty stream response");
  return {
    ok: true,
    model,
    nonStreamChars: nonStreamContent.length,
    streamChars: streamed.length,
  };
}

function requiredConfiguration(environment = process.env) {
  const baseURL = environment.OPENDEVICE_BASE_URL;
  const apiKey = environment.OPENDEVICE_API_KEY;
  if (!baseURL?.endsWith("/v1") || !apiKey) {
    throw new Error(
      "set OPENDEVICE_BASE_URL ending in /v1 and OPENDEVICE_API_KEY",
    );
  }
  return { baseURL, apiKey };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const { baseURL, apiKey } = requiredConfiguration();
  const result = await verifyClient(
    new OpenAI({ baseURL, apiKey, timeout: 120_000, maxRetries: 0 }),
  );
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
