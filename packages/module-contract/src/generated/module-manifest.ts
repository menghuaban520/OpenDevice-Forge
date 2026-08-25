export interface ModuleManifestV1 {
    audience:        Audience;
    capabilities:    [string, ...string[]];
    configuration:   Configuration;
    contributes:     [Contribute, ...Contribute[]];
    id:              string;
    integrity:       Integrity;
    kernel:          Kernel;
    license:         string;
    manifestVersion: ManifestVersion;
    name:            string;
    permissions:     Permission[];
    platform:        Platform;
    protected:       boolean;
    publisher:       string;
    risk:            Risk;
    runtime:         Runtime;
    service:         Service | null;
    source:          Source;
    summary:         string;
    version:         string;
}

export type Audience = "general" | "advanced";

export interface Configuration {
    fields: Field[];
}

export interface Field {
    default:  boolean | number | number | null | string;
    key:      string;
    label:    string;
    maximum:  number | null;
    minimum:  number | null;
    options:  string[];
    pattern:  null | string;
    required: boolean;
    type:     FieldType;
}

export type FieldType = "boolean" | "integer" | "number" | "string" | "select";

export interface Contribute {
    defaultPlacement: DefaultPlacement;
    id:               string;
    label:            string;
    type:             ContributeType;
}

export type DefaultPlacement = "sidebar" | "overview" | "shortcut" | "service" | "report" | "context" | "fullscreen";

export type ContributeType = "navigation" | "overviewSection" | "overviewAction" | "configuration" | "workflow" | "service" | "screen" | "deviceControl" | "companionApp" | "reportSection";

export interface Integrity {
    kind:               IntegrityKind;
    publisherKeySha256: null | string;
    publisherSignature: null | string;
    rollbackVersion:    null | string;
    sha256:             null | string;
}

export type IntegrityKind = "host-apk" | "package";

export interface Kernel {
    maxExclusive: string;
    min:          string;
}

export type ManifestVersion = "1";

export type Permission = "device.read" | "model.read" | "network.outbound" | "service.local" | "display.fullscreen" | "device.bluetooth" | "device.usb" | "media.camera" | "media.microphone" | "location.read" | "root.broker";

export interface Platform {
    android:  Android;
    hardware: Hardware;
}

export interface Android {
    abis:   [ABI, ...ABI[]];
    minSdk: number;
}

export type ABI = "arm64-v8a" | "armeabi-v7a" | "x86_64" | "x86";

export interface Hardware {
    minMemoryBytes:  number | null;
    minStorageBytes: number | null;
}

export type Risk = "low" | "medium" | "high";

export interface Runtime {
    companionPackage:   null | string;
    entry:              string;
    foregroundService:  boolean;
    isolatedProcess:    boolean;
    kind:               RuntimeKind;
    requiresRootBroker: boolean;
}

export type RuntimeKind = "builtin" | "declarative" | "sandbox" | "companion";

export interface Service {
    healthPath: string;
    logFields:  LogField[];
    result:     Result;
    start:      Start;
    stop:       Start;
}

export type LogField = "requestId" | "timestamp" | "model" | "inputTokens" | "outputTokens" | "durationMillis" | "statusCode" | "peakRssBytes" | "batteryTemperatureC";

export interface Result {
    contentTypes: [string, ...string[]];
    kind:         ResultKind;
    schemaId:     string;
}

export type ResultKind = "structured" | "stream";

export type Start = "explicit";

export interface Source {
    kind:        SourceKind;
    repository?: string;
    revision?:   string;
}

export type SourceKind = "builtin" | "official" | "community" | "github" | "local";

