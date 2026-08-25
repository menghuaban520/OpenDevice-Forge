import OpenAI from "openai";
import { setTimeout as delay } from "node:timers/promises";

const baseURL = process.env.OPENDEVICE_BASE_URL;
const apiKey = process.env.OPENDEVICE_API_KEY;
if (!baseURL?.endsWith("/v1") || !apiKey) {
  throw new Error("set OPENDEVICE_BASE_URL ending in /v1 and OPENDEVICE_API_KEY");
}

const client = new OpenAI({ baseURL, apiKey, timeout: 120_000, maxRetries: 0 });
const models = await client.models.list();
const model = models.data.at(0)?.id;
if (!model) throw new Error("phone returned no ready model");

const startedAt = Date.now();
const endsAt = startedAt + 1_800_000;
let ordinal = 0;

while (Date.now() < endsAt) {
  ordinal += 1;
  const requestStartedAt = Date.now();
  let outputTokens = 0;
  try {
    const stream = await client.chat.completions.create({
      model,
      messages: [
        {
          role: "user",
          content: "用不重复的短句说明手机本地推理正在工作。",
        },
      ],
      max_tokens: 256,
      stream: true,
    });
    for await (const chunk of stream) {
      if (chunk.choices[0]?.delta.content) outputTokens += 1;
    }
    process.stdout.write(
      `${JSON.stringify({
        request: ordinal,
        elapsedMillis: Date.now() - requestStartedAt,
        outputTokens,
      })}\n`,
    );
  } catch (error) {
    const code = safeErrorCode(error);
    process.stderr.write(
      `${JSON.stringify({
        request: ordinal,
        elapsedMillis: Date.now() - requestStartedAt,
        errorCode: code,
      })}\n`,
    );
    process.exitCode = 1;
    break;
  }
  if (Date.now() < endsAt) await delay(10_000);
}

function safeErrorCode(error) {
  const candidate = error?.code ?? error?.status;
  const normalized = String(candidate ?? "sdk_error");
  return /^[A-Za-z0-9_.-]{1,64}$/.test(normalized) ? normalized : "sdk_error";
}
