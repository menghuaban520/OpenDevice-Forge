package dev.opendevice.node.device

enum class DeviceSupportLevel(
    val label: String,
) {
    DETECTING("检测中"),
    READY("完全兼容"),
    LIMITED("受限兼容"),
    UNSUPPORTED("暂不支持"),
}

enum class WorkloadTier(
    val label: String,
) {
    DETECTING("等待检测"),
    LITE("轻量级"),
    BALANCED("均衡级"),
    PERFORMANCE("性能级"),
}

data class DeviceCapabilityProfile(
    val support: DeviceSupportLevel,
    val tier: WorkloadTier,
    val summary: String,
    val recommendedThreads: Int,
    val signals: List<String>,
)

fun DeviceFacts.capabilityProfile(): DeviceCapabilityProfile {
    if (sdkInt <= 0 || supportedAbis.isEmpty()) {
        return DeviceCapabilityProfile(
            support = DeviceSupportLevel.DETECTING,
            tier = WorkloadTier.DETECTING,
            summary = "正在读取这台设备的实际能力",
            recommendedThreads = 2,
            signals = listOf("等待 Android 版本与处理器架构"),
        )
    }

    val androidReady = sdkInt >= MIN_ANDROID_SDK
    val nativeReady = "arm64-v8a" in supportedAbis
    if (!androidReady || !nativeReady) {
        val missing = buildList {
            if (!androidReady) add("需要 Android 9 / API 28 或更高版本")
            if (!nativeReady) add("当前 AI 运行时需要 arm64-v8a")
        }
        return DeviceCapabilityProfile(
            support = DeviceSupportLevel.UNSUPPORTED,
            tier = WorkloadTier.LITE,
            summary = missing.joinToString("；"),
            recommendedThreads = 2,
            signals = missing,
        )
    }

    val totalMemory = totalMemoryBytes
    val storage = allocatableStorageBytes
    val processors = logicalProcessorCount
    val measurementsComplete = totalMemory != null && storage != null && processors != null
    val limited = when {
        totalMemory == null || storage == null || processors == null -> true
        else -> totalMemory < LITE_MEMORY_BYTES ||
            storage < MIN_WORKSPACE_BYTES || processors < MIN_PROCESSORS
    }
    val performance = when {
        totalMemory == null || storage == null || processors == null -> false
        else -> totalMemory >= PERFORMANCE_MEMORY_BYTES &&
            storage >= PERFORMANCE_WORKSPACE_BYTES && processors >= PERFORMANCE_PROCESSORS
    }
    val tier = when {
        limited -> WorkloadTier.LITE
        performance -> WorkloadTier.PERFORMANCE
        else -> WorkloadTier.BALANCED
    }
    val support = if (limited) DeviceSupportLevel.LIMITED else DeviceSupportLevel.READY
    val summary = when (tier) {
        WorkloadTier.PERFORMANCE -> "适合本地节点与较积极的短时工作负载"
        WorkloadTier.BALANCED -> "适合日常模块与保守的本地 AI 工作负载"
        WorkloadTier.LITE -> if (measurementsComplete) {
            "基础模块可用；本地 AI 将采用轻量设置"
        } else {
            "基础模块兼容；部分资源读数缺失，AI 采用保守设置"
        }
        WorkloadTier.DETECTING -> "正在读取这台设备的实际能力"
    }
    return DeviceCapabilityProfile(
        support = support,
        tier = tier,
        summary = summary,
        recommendedThreads = when (tier) {
            WorkloadTier.PERFORMANCE -> 3
            WorkloadTier.BALANCED, WorkloadTier.LITE, WorkloadTier.DETECTING -> 2
        },
        signals = buildList {
            add("Android API $sdkInt")
            add("arm64 原生运行时")
            totalMemoryBytes?.let { add("${it.toCompactGiB()} 总内存") }
            allocatableStorageBytes?.let { add("${it.toCompactGiB()} 可用空间") }
            logicalProcessorCount?.let { add("$it 个逻辑核心") }
            if (totalMemoryBytes == null) add("总内存未读取")
            if (allocatableStorageBytes == null) add("可用空间未读取")
            if (logicalProcessorCount == null) add("逻辑核心未读取")
        },
    )
}

private fun Long.toCompactGiB(): String = String.format(
    java.util.Locale.US,
    "%.1f GiB",
    toDouble() / GIB,
)

private const val MIN_ANDROID_SDK = 28
private const val MIN_PROCESSORS = 4
// Six available logical processors are enough for the current four-thread ceiling.
private const val PERFORMANCE_PROCESSORS = 6
private const val GIB = 1_073_741_824.0
private const val LITE_MEMORY_BYTES = 4L * 1_073_741_824L
// Android reports physical RAM, which is normally lower than the marketed capacity.
private const val PERFORMANCE_MEMORY_BYTES = 6L * 1_073_741_824L
private const val MIN_WORKSPACE_BYTES = 1L * 1_073_741_824L
private const val PERFORMANCE_WORKSPACE_BYTES = 2L * 1_073_741_824L
