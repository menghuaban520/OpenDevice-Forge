import type {
  DeviceConnection,
  DeviceFact,
  DeviceSnapshot,
  FactSource,
  ReadinessAssessment,
  ReadinessCheck,
  ReadinessStatus,
} from "./types";

const GIB = 1024 ** 3;

const unknownCheck = (
  id: ReadinessCheck["id"],
  label: string,
  source: FactSource,
  detail: string,
): ReadinessCheck => ({ id, label, source, detail, status: "unknown" });

const classifyNumber = (
  id: ReadinessCheck["id"],
  label: string,
  fact: DeviceFact<number>,
  readyAt: number,
  limitedAt: number,
): ReadinessCheck => {
  if (fact.value === null) {
    return unknownCheck(id, label, fact.source, "尚未读取到这个字段。");
  }
  const status: ReadinessStatus =
    fact.value >= readyAt ? "ready" : fact.value >= limitedAt ? "limited" : "not_ready";
  return {
    id,
    label,
    status,
    source: fact.source,
    detail: `${(fact.value / GIB).toFixed(1)} GiB`,
  };
};

const architectureCheck = (fact: DeviceFact<string>): ReadinessCheck => {
  if (fact.value === null) {
    return unknownCheck("architecture", "处理器架构", fact.source, "尚未读取架构。");
  }
  const normalized = fact.value.toLowerCase();
  const status: ReadinessStatus = normalized.includes("arm64")
    ? "ready"
    : normalized.includes("armeabi") || normalized.includes("armv7")
      ? "limited"
      : "not_ready";
  return {
    id: "architecture",
    label: "处理器架构",
    status,
    source: fact.source,
    detail: fact.value,
  };
};

const androidCheck = (fact: DeviceFact<string>): ReadinessCheck => {
  if (fact.value === null) {
    return unknownCheck("android", "Android 版本", fact.source, "尚未读取系统版本。");
  }
  const major = Number.parseInt(fact.value, 10);
  const status: ReadinessStatus = Number.isNaN(major)
    ? "unknown"
    : major >= 10
      ? "ready"
      : major >= 8
        ? "limited"
        : "not_ready";
  return {
    id: "android",
    label: "Android 版本",
    status,
    source: fact.source,
    detail: fact.value,
  };
};

const blockerFor = (
  connection: DeviceConnection,
): ReadinessAssessment["blocker"] | undefined => {
  if (connection === "adb_missing") return "adb_missing";
  if (connection === "unauthorized") return "adb_unauthorized";
  if (connection === "offline") return "device_offline";
  if (connection === "not_connected") return "device_not_connected";
  return undefined;
};

const evidenceQuality = (
  snapshot: DeviceSnapshot,
  checks: ReadinessCheck[],
): ReadinessAssessment["evidenceQuality"] => {
  if (snapshot.mode === "demo") return "demo";
  const sources = new Set(checks.map((check) => check.source));
  if (sources.size === 1 && sources.has("measured")) return "measured";
  if (sources.size === 1 && sources.has("public_reference")) return "reference_only";
  if (sources.size === 1 && sources.has("unknown")) return "insufficient";
  return "mixed";
};

export const assessReadiness = (snapshot: DeviceSnapshot): ReadinessAssessment => {
  const checks: ReadinessCheck[] = [
    architectureCheck(snapshot.abi),
    classifyNumber("memory", "可用内存档位", snapshot.ramBytes, 6 * GIB, 4 * GIB),
    classifyNumber(
      "storage",
      "可用存储空间",
      snapshot.storageAvailableBytes,
      6 * GIB,
      3 * GIB,
    ),
    androidCheck(snapshot.androidVersion),
  ];
  const blocker = blockerFor(snapshot.connection);
  const statuses = checks.map((check) => check.status);
  const verdict: ReadinessStatus = blocker || statuses.includes("unknown")
    ? "unknown"
    : statuses.includes("not_ready")
      ? "not_ready"
      : statuses.includes("limited")
        ? "limited"
        : "ready";
  const quality = evidenceQuality(snapshot, checks);
  const summary = snapshot.mode === "demo"
    ? "这是演示评估，用来预览流程；连接真机后会由实测值覆盖。"
    : verdict === "unknown"
      ? "部分关键字段仍需真机读取，暂不判断模型兼容性。"
      : verdict === "ready"
        ? "基础条件符合轻量模型试验档位，仍需用具体模型实测。"
        : verdict === "limited"
          ? "可以从更小的量化模型开始，实际速度仍需本机测试。"
          : "当前基础条件不适合本地模型节点，报告不会建议强行安装。";

  return {
    verdict,
    evidenceQuality: quality,
    demo: snapshot.mode === "demo",
    summary,
    ...(blocker ? { blocker } : {}),
    checks,
  };
};

export const mergeDeviceSnapshots = (
  reference: DeviceSnapshot,
  measured: Partial<DeviceSnapshot>,
): DeviceSnapshot => ({ ...reference, ...measured });

