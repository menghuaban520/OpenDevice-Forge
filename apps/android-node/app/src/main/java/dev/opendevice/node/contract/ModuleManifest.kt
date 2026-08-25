// To parse the JSON, install kotlin's serialization plugin and do:
//
// val json             = Json { allowStructuredMapKeys = true }
// val moduleManifestV1 = json.parse(ModuleManifestV1.serializer(), jsonString)

package dev.opendevice.node.contract

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable
data class ModuleManifestV1 (
    val audience: Audience,
    val capabilities: List<String>,
    val configuration: Configuration,
    val contributes: List<Contribute>,
    val id: String,
    val integrity: Integrity,
    val kernel: Kernel,
    val license: String,
    val manifestVersion: ManifestVersion,
    val name: String,
    val permissions: List<Permission>,
    val platform: Platform,
    val protected: Boolean,
    val publisher: String,
    val risk: Risk,
    val runtime: Runtime,
    val service: Service? = null,
    val source: Source,
    val summary: String,
    val version: String
)

@Serializable
enum class Audience(val value: String) {
    @SerialName("advanced") Advanced("advanced"),
    @SerialName("general") General("general");
}

@Serializable
data class Configuration (
    val fields: List<Field>
)

@Serializable
data class Field (
    val default: JsonElement,
    val key: String,
    val label: String,
    val maximum: Double? = null,
    val minimum: Double? = null,
    val options: List<String>,
    val pattern: String? = null,
    val required: Boolean,
    val type: FieldType
)

@Serializable
enum class FieldType(val value: String) {
    @SerialName("integer") Integer("integer"),
    @SerialName("number") Number("number"),
    @SerialName("select") Select("select"),
    @SerialName("boolean") TypeBoolean("boolean"),
    @SerialName("string") TypeString("string");
}

@Serializable
data class Contribute (
    val defaultPlacement: DefaultPlacement,
    val id: String,
    val label: String,
    val type: ContributeType
)

@Serializable
enum class DefaultPlacement(val value: String) {
    @SerialName("context") Context("context"),
    @SerialName("fullscreen") Fullscreen("fullscreen"),
    @SerialName("overview") Overview("overview"),
    @SerialName("report") Report("report"),
    @SerialName("service") Service("service"),
    @SerialName("shortcut") Shortcut("shortcut"),
    @SerialName("sidebar") Sidebar("sidebar");
}

@Serializable
enum class ContributeType(val value: String) {
    @SerialName("companionApp") CompanionApp("companionApp"),
    @SerialName("configuration") Configuration("configuration"),
    @SerialName("deviceControl") DeviceControl("deviceControl"),
    @SerialName("navigation") Navigation("navigation"),
    @SerialName("overviewAction") OverviewAction("overviewAction"),
    @SerialName("overviewSection") OverviewSection("overviewSection"),
    @SerialName("reportSection") ReportSection("reportSection"),
    @SerialName("screen") Screen("screen"),
    @SerialName("service") Service("service"),
    @SerialName("workflow") Workflow("workflow");
}

@Serializable
data class Integrity (
    val kind: IntegrityKind,
    val publisherKeySha256: String? = null,
    val publisherSignature: String? = null,
    val rollbackVersion: String? = null,
    val sha256: String? = null
)

@Serializable
enum class IntegrityKind(val value: String) {
    @SerialName("host-apk") HostApk("host-apk"),
    @SerialName("package") Package("package");
}

@Serializable
data class Kernel (
    val maxExclusive: String,
    val min: String
)

@Serializable
enum class ManifestVersion(val value: String) {
    @SerialName("1") The1("1");
}

@Serializable
enum class Permission(val value: String) {
    @SerialName("device.bluetooth") DeviceBluetooth("device.bluetooth"),
    @SerialName("device.read") DeviceRead("device.read"),
    @SerialName("device.usb") DeviceUSB("device.usb"),
    @SerialName("display.fullscreen") DisplayFullscreen("display.fullscreen"),
    @SerialName("location.read") LocationRead("location.read"),
    @SerialName("media.camera") MediaCamera("media.camera"),
    @SerialName("media.microphone") MediaMicrophone("media.microphone"),
    @SerialName("model.read") ModelRead("model.read"),
    @SerialName("network.outbound") NetworkOutbound("network.outbound"),
    @SerialName("root.broker") RootBroker("root.broker"),
    @SerialName("service.local") ServiceLocal("service.local");
}

@Serializable
data class Platform (
    val android: Android,
    val hardware: Hardware
)

@Serializable
data class Android (
    val abis: List<ABI>,

    @SerialName("minSdk")
    val minSDK: Long
)

@Serializable
enum class ABI(val value: String) {
    @SerialName("arm64-v8a") Arm64V8A("arm64-v8a"),
    @SerialName("armeabi-v7a") ArmeabiV7A("armeabi-v7a"),
    @SerialName("x86") X86("x86"),
    @SerialName("x86_64") X8664("x86_64");
}

@Serializable
data class Hardware (
    val minMemoryBytes: Long? = null,
    val minStorageBytes: Long? = null
)

@Serializable
enum class Risk(val value: String) {
    @SerialName("high") High("high"),
    @SerialName("low") Low("low"),
    @SerialName("medium") Medium("medium");
}

@Serializable
data class Runtime (
    val companionPackage: String? = null,
    val entry: String,
    val foregroundService: Boolean,
    val isolatedProcess: Boolean,
    val kind: RuntimeKind,
    val requiresRootBroker: Boolean
)

@Serializable
enum class RuntimeKind(val value: String) {
    @SerialName("builtin") Builtin("builtin"),
    @SerialName("companion") Companion("companion"),
    @SerialName("declarative") Declarative("declarative"),
    @SerialName("sandbox") Sandbox("sandbox");
}

@Serializable
data class Service (
    val healthPath: String,
    val logFields: List<LogField>,
    val result: Result,
    val start: Start,
    val stop: Start
)

@Serializable
enum class LogField(val value: String) {
    @SerialName("batteryTemperatureC") BatteryTemperatureC("batteryTemperatureC"),
    @SerialName("durationMillis") DurationMillis("durationMillis"),
    @SerialName("inputTokens") InputTokens("inputTokens"),
    @SerialName("model") Model("model"),
    @SerialName("outputTokens") OutputTokens("outputTokens"),
    @SerialName("peakRssBytes") PeakRSSBytes("peakRssBytes"),
    @SerialName("requestId") RequestID("requestId"),
    @SerialName("statusCode") StatusCode("statusCode"),
    @SerialName("timestamp") Timestamp("timestamp");
}

@Serializable
data class Result (
    val contentTypes: List<String>,
    val kind: ResultKind,

    @SerialName("schemaId")
    val schemaID: String
)

@Serializable
enum class ResultKind(val value: String) {
    @SerialName("stream") Stream("stream"),
    @SerialName("structured") Structured("structured");
}

@Serializable
enum class Start(val value: String) {
    @SerialName("explicit") Explicit("explicit");
}

@Serializable
data class Source (
    val kind: SourceKind,
    val repository: String? = null,
    val revision: String? = null
)

@Serializable
enum class SourceKind(val value: String) {
    @SerialName("builtin") Builtin("builtin"),
    @SerialName("community") Community("community"),
    @SerialName("github") Github("github"),
    @SerialName("local") Local("local"),
    @SerialName("official") Official("official");
}

typealias ModuleManifest = ModuleManifestV1
