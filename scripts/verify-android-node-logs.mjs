import { readdirSync, readFileSync, statSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

export function findSecretLeaks(text, secrets) {
  return secrets.filter(Boolean).filter((secret) => text.includes(secret));
}

export function scanEvidence(text, environment = process.env) {
  const categories = new Set();
  const groups = {
    prompt: [
      "只回复：手机节点正常",
      "从一数到三",
      "用不重复的短句说明手机本地推理正在工作。",
      "你好",
    ],
    response: ["手机节点正常", "手机回复", "一二三"],
    api_key: [environment.OPENDEVICE_API_KEY],
    adb_serial: [environment.ANDROID_SERIAL],
  };
  for (const [category, values] of Object.entries(groups)) {
    if (findSecretLeaks(text, values).length > 0) categories.add(category);
  }
  if (/Authorization:\s*Bearer\s+(?!\[REDACTED\])[^\s"']+/iu.test(text)) {
    categories.add("api_key");
  }
  if (/\bsk-[A-Za-z0-9_-]+\b/u.test(text)) categories.add("api_key");
  return [...categories].sort();
}

function evidenceFiles(target) {
  const metadata = statSync(target);
  if (metadata.isFile()) return [target];
  if (!metadata.isDirectory()) return [];
  return readdirSync(target, { withFileTypes: true }).flatMap((entry) => {
    const child = resolve(target, entry.name);
    return entry.isDirectory() ? evidenceFiles(child) : [child];
  });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const target = process.argv[2];
  if (!target) throw new Error("provide an evidence file or directory");
  const files = evidenceFiles(resolve(target));
  const categories = new Set();
  for (const file of files) {
    const text = readFileSync(file, "utf8");
    for (const category of scanEvidence(text)) categories.add(category);
  }
  if (categories.size > 0) {
    process.stderr.write(
      `${JSON.stringify({ ok: false, leakCategories: [...categories].sort() })}\n`,
    );
    process.exitCode = 1;
  } else {
    process.stdout.write(`${JSON.stringify({ ok: true, filesScanned: files.length })}\n`);
  }
}
