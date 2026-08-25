package dev.opendevice.node.kernel

import dev.opendevice.node.contract.ABI
import dev.opendevice.node.contract.Audience
import dev.opendevice.node.contract.ContributeType
import dev.opendevice.node.contract.DefaultPlacement
import dev.opendevice.node.contract.ModuleManifest
import dev.opendevice.node.contract.Risk
import dev.opendevice.node.contract.RuntimeKind

data class ModuleHost(
    val kernelVersion: String,
    val androidSdk: Int,
    val abis: Set<ABI>,
    val availableRuntimes: Set<RuntimeKind>,
)

data class ModuleCompatibilityError(
    val code: String,
    val path: String,
    val message: String,
)

object ModuleCompatibility {
    private val semanticVersion =
        Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?$")

    private val placementRules = mapOf(
        ContributeType.Navigation to setOf(DefaultPlacement.Sidebar),
        ContributeType.OverviewSection to setOf(DefaultPlacement.Overview),
        ContributeType.OverviewAction to
            setOf(DefaultPlacement.Overview, DefaultPlacement.Shortcut),
        ContributeType.Configuration to setOf(DefaultPlacement.Context),
        ContributeType.Workflow to
            setOf(DefaultPlacement.Shortcut, DefaultPlacement.Context),
        ContributeType.Service to setOf(DefaultPlacement.Service),
        ContributeType.Screen to
            setOf(DefaultPlacement.Sidebar, DefaultPlacement.Fullscreen),
        ContributeType.DeviceControl to
            setOf(DefaultPlacement.Context, DefaultPlacement.Fullscreen),
        ContributeType.CompanionApp to setOf(DefaultPlacement.Service),
        ContributeType.ReportSection to setOf(DefaultPlacement.Report),
    )

    fun check(manifest: ModuleManifest, host: ModuleHost): List<ModuleCompatibilityError> {
        val errors = mutableListOf<ModuleCompatibilityError>()
        val hostVsMinimum = compareVersions(host.kernelVersion, manifest.kernel.min)
        val hostVsMaximum = compareVersions(host.kernelVersion, manifest.kernel.maxExclusive)
        if (hostVsMinimum == null || hostVsMaximum == null ||
            hostVsMinimum < 0 || hostVsMaximum >= 0
        ) {
            errors += error(
                "kernel_incompatible",
                "/kernel",
                "Module kernel range is incompatible.",
            )
        }

        if (host.androidSdk.toLong() < manifest.platform.android.minSDK) {
            errors += error(
                "android_sdk_incompatible",
                "/platform/android/minSdk",
                "Host Android SDK is below the module minimum.",
            )
        }

        if (!host.abis.containsAll(manifest.platform.android.abis)) {
            errors += error(
                "abi_incompatible",
                "/platform/android/abis",
                "Host does not provide every required ABI.",
            )
        }

        if (manifest.runtime.kind !in host.availableRuntimes) {
            errors += error(
                "runtime_unavailable",
                "/runtime/kind",
                "The requested runtime is unavailable on this host.",
            )
        }

        manifest.contributes.forEachIndexed { index, contribution ->
            if (contribution.defaultPlacement !in placementRules.getValue(contribution.type)) {
                errors += error(
                    "placement_mismatch",
                    "/contributes/$index/defaultPlacement",
                    "Contribution placement is incompatible with its type.",
                )
            }
        }

        if (manifest.risk == Risk.High && manifest.audience != Audience.Advanced) {
            errors += error(
                "risk_audience_mismatch",
                "/audience",
                "High-risk modules must target the advanced audience.",
            )
        }

        return errors
    }

    private fun compareVersions(left: String, right: String): Int? {
        val leftParts = parseVersion(left) ?: return null
        val rightParts = parseVersion(right) ?: return null
        return leftParts.indices
            .asSequence()
            .map { leftParts[it].compareTo(rightParts[it]) }
            .firstOrNull { it != 0 } ?: 0
    }

    private fun parseVersion(value: String): List<Int>? =
        semanticVersion.matchEntire(value)
            ?.groupValues
            ?.drop(1)
            ?.take(3)
            ?.map(String::toInt)

    private fun error(code: String, path: String, message: String) =
        ModuleCompatibilityError(code = code, path = path, message = message)
}
