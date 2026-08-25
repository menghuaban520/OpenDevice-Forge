import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { basename, delimiter, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { setTimeout as delay } from "node:timers/promises";

const SCRIPT_ROOT = dirname(fileURLToPath(import.meta.url));
const REPOSITORY_ROOT = resolve(SCRIPT_ROOT, "..");
const ARTIFACT_ROOT = join(REPOSITORY_ROOT, "docs", "verification", "artifacts");
const APK_PATH = join(
  REPOSITORY_ROOT,
  "apps",
  "android-node",
  "app",
  "build",
  "outputs",
  "apk",
  "debug",
  "app-debug.apk",
);

export function redactEvidence(value, secrets) {
  let redacted = String(value)
    .replace(
      /Authorization:\s*Bearer\s+[^\s"']+/giu,
      "Authorization: Bearer [REDACTED]",
    )
    .replace(/\bsk-[A-Za-z0-9_-]+\b/gu, "[REDACTED]");
  for (const secret of secrets.filter(Boolean)) {
    redacted = redacted.split(secret).join("[REDACTED]");
  }
  return redacted;
}

export async function collectEvidence(options, dependencies = {}) {
  const run = dependencies.runAdb ?? createAdbRunner(options.serial);
  const now = dependencies.now ?? (() => new Date());
  const pause = dependencies.delay ?? delay;
  const serial = options.serial ?? resolveAuthorizedSerial(createAdbRunner(null));
  const serialHash = sha256(serial);
  const model = run(["shell", "getprop", "ro.product.model"]).trim();
  if (model !== "CDL-AN50" && !options.allowDifferentDevice) {
    throw new Error(
      "connected device is not CDL-AN50; pass --allow-different-device only for a non-gating dry run",
    );
  }

  const startedAt = now();
  const deadline = startedAt.getTime() + options.durationSeconds * 1_000;
  const samples = [];
  let safetyStop = null;
  let continueSampling = true;
  while (continueSampling) {
    const sample = collectSample(run, now, model);
    samples.push(sample);
    safetyStop = sample.safetyStop;
    continueSampling = !safetyStop && now().getTime() < deadline;
    if (continueSampling) await pause(options.intervalSeconds * 1_000);
  }

  const apkSha256 = sha256(readFileSync(APK_PATH));
  const evidence = {
    schemaVersion: 1,
    phase: options.phase,
    collectedAt: startedAt.toISOString(),
    completedAt: now().toISOString(),
    device: {
      serialSha256: serialHash,
      model,
      cdlAn50GateSatisfied: model === "CDL-AN50",
      differentDeviceExplicitlyAllowed: model !== "CDL-AN50" && options.allowDifferentDevice,
    },
    build: {
      apkFile: basename(APK_PATH),
      apkSha256,
    },
    requestedDurationSeconds: options.durationSeconds,
    intervalSeconds: options.intervalSeconds,
    safetyStop,
    samples,
  };
  const secrets = [process.env.OPENDEVICE_API_KEY, serial];
  const serialized = redactEvidence(`${JSON.stringify(evidence, null, 2)}\n`, secrets);
  mkdirSync(ARTIFACT_ROOT, { recursive: true });
  const timestamp = startedAt.toISOString().replaceAll(":", "-");
  const artifactPath = join(ARTIFACT_ROOT, `${timestamp}-${options.phase}.json`);
  writeFileSync(artifactPath, serialized, { encoding: "utf8", flag: "wx", mode: 0o600 });
  return { artifactPath, safetyStop, cdlAn50GateSatisfied: model === "CDL-AN50" };
}

function collectSample(run, now, knownModel) {
  const pidText = run(["shell", "pidof", "dev.opendevice.node"], true).trim();
  const pid = pidText.split(/\s+/u).find((candidate) => /^\d+$/u.test(candidate));
  const battery = run(["shell", "dumpsys", "battery"]);
  const thermal = run(["shell", "dumpsys", "thermalservice"], true);
  const service = run(
    ["shell", "dumpsys", "activity", "service", "dev.opendevice.node/.ai.AiNodeService"],
    true,
  );
  const batteryTemperatureTenths = numberAfterLabel(battery, "temperature");
  const batteryTemperatureC = batteryTemperatureTenths == null
    ? null
    : batteryTemperatureTenths / 10;
  const severeThermal = /\b(SEVERE|CRITICAL|EMERGENCY|SHUTDOWN)\b/iu.test(thermal);
  const safetyStop = batteryTemperatureC != null && batteryTemperatureC >= 45
    ? `battery_temperature_${batteryTemperatureC.toFixed(1)}C`
    : severeThermal
      ? "android_thermal_severe_or_higher"
      : null;
  return {
    collectedAt: now().toISOString(),
    properties: {
      model: knownModel,
      androidRelease: run(["shell", "getprop", "ro.build.version.release"]).trim(),
      androidSdk: run(["shell", "getprop", "ro.build.version.sdk"]).trim(),
      abiList: run(["shell", "getprop", "ro.product.cpu.abilist"]).trim(),
    },
    memory: {
      procMeminfo: run(["shell", "cat", "/proc/meminfo"]),
      processStatus: pid
        ? run(["shell", "cat", `/proc/${pid}/status`], true)
        : "process_not_running",
      processMeminfo: run(
        ["shell", "dumpsys", "meminfo", "dev.opendevice.node"],
        true,
      ),
    },
    storage: run(["shell", "df", "-k", "/data"]),
    battery,
    batteryTemperatureC,
    thermal,
    app: {
      package: extractPackageFacts(
        run(["shell", "dumpsys", "package", "dev.opendevice.node"], true),
      ),
      foregroundService: service,
      diagnostics: extractDiagnostics(service),
      pid: pid ?? null,
    },
    logcat: pid
      ? run(["shell", "logcat", "-d", "-v", "threadtime", "--pid", pid], true)
      : "process_not_running",
    safetyStop,
  };
}

function createAdbRunner(serial) {
  const adb = resolveAdbExecutable();
  return (args, allowFailure = false) => {
    const commandArgs = serial ? ["-s", serial, ...args] : args;
    try {
      return execFileSync(adb, commandArgs, {
        encoding: "utf8",
        maxBuffer: 16 * 1_024 * 1_024,
        stdio: ["ignore", "pipe", "pipe"],
      });
    } catch (error) {
      if (allowFailure) return "command_unavailable";
      const command = args.slice(0, 3).join(" ");
      throw new Error(`adb command failed: ${command}`, { cause: error });
    }
  };
}

function resolveAuthorizedSerial(runWithoutSerial) {
  const output = runWithoutSerial(["devices"]);
  const devices = output
    .split(/\r?\n/u)
    .slice(1)
    .map((line) => line.trim().split(/\s+/u))
    .filter((parts) => parts.length >= 2 && parts[1] === "device")
    .map((parts) => parts[0]);
  if (devices.length !== 1) {
    throw new Error("connect exactly one authorized Android device or set ANDROID_SERIAL");
  }
  return devices[0];
}

function resolveAdbExecutable() {
  const sdkRoot = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
  if (sdkRoot) return join(sdkRoot, "platform-tools", "adb");
  const pathEntries = (process.env.PATH ?? "").split(delimiter);
  const candidate = pathEntries.find((entry) => basename(entry) === "platform-tools");
  return candidate ? join(candidate, "adb") : "adb";
}

function extractPackageFacts(value) {
  return {
    versionName: firstMatch(value, /^\s*versionName=(.+)$/mu),
    versionCode: firstMatch(value, /^\s*versionCode=(\d+)/mu),
    firstInstallTime: firstMatch(value, /^\s*firstInstallTime=(.+)$/mu),
    lastUpdateTime: firstMatch(value, /^\s*lastUpdateTime=(.+)$/mu),
  };
}

function extractDiagnostics(value) {
  const diagnostics = {};
  for (const match of value.matchAll(/^\s*opendevice\.([A-Za-z0-9_.-]+)=(.*)$/gmu)) {
    diagnostics[match[1]] = match[2];
  }
  return diagnostics;
}

function numberAfterLabel(value, label) {
  const match = value.match(new RegExp(`^\\s*${label}:\\s*(-?\\d+)`, "mu"));
  return match ? Number.parseInt(match[1], 10) : null;
}

function firstMatch(value, expression) {
  return value.match(expression)?.[1]?.trim() ?? null;
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function parseArguments(argv) {
  const options = {
    phase: null,
    durationSeconds: 0,
    intervalSeconds: 30,
    allowDifferentDevice: false,
    serial: process.env.ANDROID_SERIAL || null,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === "--allow-different-device") {
      options.allowDifferentDevice = true;
    } else if (argument === "--phase") {
      options.phase = argv[++index];
    } else if (argument === "--duration-seconds") {
      options.durationSeconds = Number.parseInt(argv[++index], 10);
    } else if (argument === "--interval-seconds") {
      options.intervalSeconds = Number.parseInt(argv[++index], 10);
    } else {
      throw new Error(`unknown argument: ${argument}`);
    }
  }
  if (!/^[a-z0-9][a-z0-9-]{0,63}$/u.test(options.phase ?? "")) {
    throw new Error("set --phase to a lowercase name");
  }
  if (!Number.isInteger(options.durationSeconds) || options.durationSeconds < 0) {
    throw new Error("duration must be a non-negative integer");
  }
  if (!Number.isInteger(options.intervalSeconds) || options.intervalSeconds < 5) {
    throw new Error("interval must be at least 5 seconds");
  }
  return options;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = await collectEvidence(parseArguments(process.argv.slice(2)));
  process.stdout.write(
    `${JSON.stringify({
      artifact: result.artifactPath.replace(`${REPOSITORY_ROOT}/`, ""),
      cdlAn50GateSatisfied: result.cdlAn50GateSatisfied,
      safetyStop: result.safetyStop,
    })}\n`,
  );
  if (result.safetyStop) process.exitCode = 2;
}
