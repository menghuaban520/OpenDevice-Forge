import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import {
  FetchingJSONSchemaStore,
  InputData,
  JSONSchemaInput,
  quicktype,
} from "quicktype-core";

const mode = process.argv.includes("--check") ? "check" : "write";
const schema = await readFile(
  new URL("../../core/schema/opendevice.module.v1.schema.json", import.meta.url),
  "utf8",
);
const targets = [
  {
    lang: "typescript",
    out: new URL("../src/generated/module-manifest.ts", import.meta.url),
    rendererOptions: { "just-types": "true" },
  },
  {
    lang: "kotlin",
    out: new URL(
      "../../../apps/android-node/app/src/main/java/dev/opendevice/node/contract/ModuleManifest.kt",
      import.meta.url,
    ),
    rendererOptions: {
      framework: "kotlinx",
      package: "dev.opendevice.node.contract",
    },
  },
];

const normalizeKotlin = (source) => {
  const jsonDefault = source
    .replace("val default: Default,", "val default: JsonElement,")
    .replace(
      /\n@Serializable\nsealed class Default \{[\s\S]*?\n\}\n\n@Serializable\nenum class FieldType/,
      "\n@Serializable\nenum class FieldType",
    );
  return `${jsonDefault.trimEnd()}\n\ntypealias ModuleManifest = ModuleManifestV1\n`;
};

for (const target of targets) {
  const schemaInput = new JSONSchemaInput(new FetchingJSONSchemaStore());
  await schemaInput.addSource({ name: "ModuleManifestV1", schema });
  const inputData = new InputData();
  inputData.addInput(schemaInput);
  const { lines } = await quicktype({
    inputData,
    lang: target.lang,
    rendererOptions: target.rendererOptions,
  });
  const rendered = `${lines.join("\n")}\n`;
  const generated = target.lang === "kotlin" ? normalizeKotlin(rendered) : rendered;
  const path = fileURLToPath(target.out);
  if (mode === "check") {
    const current = await readFile(path, "utf8").catch(() => "");
    if (current !== generated) {
      throw new Error(`generated file is stale: ${path}`);
    }
  } else {
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, generated, "utf8");
  }
}
