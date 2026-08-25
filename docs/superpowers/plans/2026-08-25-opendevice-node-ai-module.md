# OpenDevice Node Android AI Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a real-device Android node on the HUAWEI CDL-AN50 that downloads and runs one pinned local GGUF model and exposes it to a computer through an authenticated OpenAI-compatible local API.

**Architecture:** Add a Kotlin/Compose Android application whose module kernel owns lifecycle, persistence, crash recovery, and resource policy. A built-in AI module composes a resumable model store, an API-28-compatible `llama.cpp` JNI adapter, a single-flight controller, and a deliberately small HTTP/1.1 server; the existing TypeScript workspace remains the source of the shared module manifest contract. Public VPS relay, marketplace discovery, visual module creation, scripting, Root Broker, and non-AI modules remain separate follow-up deliverables.

**Tech Stack:** Node.js 24+, pnpm 11.5.2, TypeScript 7.0.2, Vitest 4.1.10, JSON Schema Draft 2020-12, Ajv 8.20.0, quicktype-core 26.0.0, JDK 17, Gradle 8.14.3, Android Gradle Plugin 8.13.2, Kotlin 2.3.0, Jetpack Compose BOM 2026.08.00, minSdk 28, target/compileSdk 36, NDK 29.0.13113456, CMake 3.31.6, WorkManager 2.11.2, DataStore 1.2.1, Coroutines/Serialization 1.11.0, `llama.cpp` commit `3737e41370da1830a44c663f9929a0f27591ffa6`.

**Spec:** `docs/superpowers/specs/2026-08-25-opendevice-node-module-console-design.md`

## Global Constraints

- The first release supports Android 9/API 28 and later, and builds only `arm64-v8a`.
- Every Android command runs with `ANDROID_HOME=/Users/huahua/Library/Android/sdk`, `JAVA_HOME=/opt/homebrew/opt/openjdk@17`, and `/Users/huahua/Library/Android/sdk/platform-tools` on `PATH`.
- The target device must be measured again at execution time; Android version, RAM, storage, battery, thermal state, and Root status from prior evidence are not treated as current facts.
- Root is not required and no Root operation is attempted in this plan.
- The built-in AI module begins disabled and can start only after an explicit phone-side action.
- LAN access begins disabled; enabling it requires an explicit confirmation on the phone.
- Every non-health API request requires `Authorization: Bearer <token>`; tokens are random 32-byte values, shown once, stored only as protected verifier material, and individually revocable.
- The HTTP listener binds to `127.0.0.1:8080` by default. LAN mode binds to the active private interface only; it never claims public Internet reachability.
- The server implements only `GET /health`, `GET /v1/models`, and `POST /v1/chat/completions`, including SSE streaming terminated by `data: [DONE]`.
- HTTP bounds are fixed at a 2 KiB request line, 16 KiB total headers, 64 headers, 1 MiB body, and 15-second read idle timeout; every response closes the connection.
- A generation has a 120-second wall-clock timeout; timeout cancels native generation and returns `generation_timeout` when response headers have not been sent, otherwise it closes the SSE stream.
- Only one model generation may run at a time; a concurrent generation receives HTTP 429.
- The first model is `Qwen/Qwen3-0.6B-GGUF` revision `23749fefcc72300e3a2ad315e1317431b06b590a`, file `Qwen3-0.6B-Q8_0.gguf`, size `639446688`, SHA-256 `9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031`.
- Model downloads use a `.part` file, HTTP Range resume, exact size and SHA-256 verification, then atomic rename.
- Inference uses context 2048, at most 512 output tokens, 2-4 CPU threads, and clears request state between requests.
- Loading requires available memory of at least model size plus 512 MiB. Severe thermal state or battery temperature at least 45 C pauses work; automatic resume requires thermal status at most moderate and battery temperature at most 40 C.
- The foreground service is visible while serving. The app never silently restarts the service after the user stops it.
- Three unclean module startup terminations inside ten minutes activate safe mode; safe mode disables third-party modules and leaves the protected recovery surface available.
- The release is not complete until a real CDL-AN50 passes USB forwarding, OpenAI JavaScript SDK, lifecycle, authentication, concurrency, integrity-failure, and 30-minute thermal-soak checks.

## File Structure

| Path | Responsibility |
| --- | --- |
| `packages/core/schema/opendevice.module.v1.schema.json` | Canonical, versioned module manifest contract shared by TypeScript and Android. |
| `packages/module-contract/` | Schema validation, generated TypeScript types, fixtures, and generation-drift test. |
| `packages/core/src/plugin/types.ts` | Compatibility adapter from the existing plugin vocabulary to the canonical module manifest. |
| `apps/android-node/` | Android application and all node runtime code. |
| `apps/android-node/kernel/` | Module registry, crash window, safe mode, and DataStore persistence. |
| `apps/android-node/device/` | Truthful device, memory, storage, power, thermal, and network observations. |
| `apps/android-node/model/` | Pinned catalog, resumable download, hash verification, and model files. |
| `apps/android-node/inference/` | Kotlin inference interface, JNI bridge, native wrapper, and the pinned `llama.cpp` submodule. |
| `apps/android-node/ai/` | AI node state machine, resource guard, foreground-service lifecycle, and local chat. |
| `apps/android-node/api/` | Bounded HTTP parser, authentication, OpenAI request mapping, SSE writer, and socket server. |
| `apps/android-node/ui/` | Four-screen Compose shell: Node, Modules, Connections, and Status. |
| `scripts/verify-openai-client.mjs` | Computer-side verification through the standard OpenAI JavaScript SDK. |
| `docs/verification/android-node-cdl-an50.md` | Exact commands and captured real-device evidence; no claims are pre-filled. |

## AI Module Capability Contract

- Trigger: a visible phone-side user enables the built-in module, downloads the pinned model, and presses Start; there is no boot or remote-management trigger.
- Provider: host-signed built-in runtime `dev.opendevice.module.ai-node` backed by the pinned `llama.cpp` submodule and the exact verified Qwen GGUF file.
- Side effects: writes model bytes only under app-private storage, opens a local or explicitly confirmed private-LAN socket, runs CPU inference, creates a persistent notification, and records redacted lifecycle/API metrics.
- Validator: strict module Schema plus semantic compatibility, model length/SHA-256, Android Keystore token verification, bounded HTTP parser, resource guard, source/build tests, and real CDL-AN50/standard-client checks.
- Rollback: Stop closes sockets and unloads the model; Disable prevents restart; revoke removes a client verifier; cancel preserves only a `.part`; uninstalling the app removes private state; no Root, bootloader, VPS, or external account state is changed.

---

### Task 1: Reproducible Android Application Baseline

**Files:**
- Create: `apps/android-node/settings.gradle.kts`
- Create: `apps/android-node/build.gradle.kts`
- Create: `apps/android-node/gradle.properties`
- Create: `apps/android-node/app/build.gradle.kts`
- Create: `apps/android-node/app/src/main/AndroidManifest.xml`
- Create: `apps/android-node/app/src/main/res/xml/backup_rules.xml`
- Create: `apps/android-node/app/src/main/res/xml/data_extraction_rules.xml`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/MainActivity.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/OpenDeviceNodeApp.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/BuildContractTest.kt`
- Create: `apps/android-node/gradle/wrapper/gradle-wrapper.properties`
- Create: `apps/android-node/gradlew`
- Create: `apps/android-node/gradlew.bat`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: repository root and JDK 17 at `/opt/homebrew/opt/openjdk@17`.
- Produces: application ID `dev.opendevice.node`, minSdk 28, target/compileSdk 36, one Android `app` module, and the Gradle commands used by every later task.

- [ ] **Step 1: Install the pinned Android SDK packages after license review**

The command-line tools already exist at `/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest`, but `/Users/huahua/Library/Android/sdk` currently contains no SDK packages. First show the Android SDK terms to the user and let the user respond to the interactive prompts; never pipe automatic acceptance into the license command.

```bash
export ANDROID_HOME=/Users/huahua/Library/Android/sdk
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" --licenses
```

After acceptance, install only the exact packages used by this plan:

```bash
/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-36" "build-tools;36.0.0" "ndk;29.0.13113456" "cmake;3.31.6"
/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" --list_installed
```

Expected installed list: all five requested packages at the pinned versions. If license acceptance or Google repository access fails, stop this task with that exact evidence; do not substitute an unpinned SDK package.

- [ ] **Step 2: Write the failing build-contract test**

```kotlin
package dev.opendevice.node

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildContractTest {
    @Test fun packageNameIsStable() {
        assertEquals("dev.opendevice.node", OpenDeviceNodeApp.PACKAGE_NAME)
    }
}
```

- [ ] **Step 3: Pin the Gradle wrapper and Android build**

Create `settings.gradle.kts` and root `build.gradle.kts` with exact plugin versions:

```kotlin
// settings.gradle.kts
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "OpenDeviceNode"
include(":app")
```

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0" apply false
}
```

Use this deterministic `gradle.properties` baseline:

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.parallel=false
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

Generate the wrapper from the already present Gradle 8.11.1 bootstrap, then verify the generated distribution URL; do not hand-edit the generated scripts:

```bash
/Users/huahua/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle -p apps/android-node wrapper --gradle-version 8.14.3 --distribution-type bin
rg '^distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip$' apps/android-node/gradle/wrapper/gradle-wrapper.properties
```

Expected: the `rg` command prints the one exact pinned line.

Append only these Android-local build products to `.gitignore`:

```gitignore
apps/android-node/.gradle/
apps/android-node/local.properties
apps/android-node/**/build/
apps/android-node/**/.cxx/
```

- [ ] **Step 4: Create the API-28 app module**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.opendevice.node"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.opendevice.node"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { jniLibs.useLegacyPackaging = false }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

The manifest declares `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` for the explicit model-download worker, and `FOREGROUND_SERVICE_SPECIAL_USE` for the long-running local AI node. Declare `.ai.AiNodeService` as non-exported with `android:foregroundServiceType="specialUse"` and this property:

```xml
<property
    android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
    android:value="User-started on-device AI inference and authenticated local API server" />
```

It must not request storage, VPN, boot, accessibility, or Root-related permissions.
Set the application attributes `android:allowBackup="false"`, `android:fullBackupContent="@xml/backup_rules"`, `android:dataExtractionRules="@xml/data_extraction_rules"`, and `android:usesCleartextTraffic="false"`. Both XML files explicitly exclude root, file, database, shared-preference, external, and device-protected domains from cloud and device-transfer backup. Token records and models live under `noBackupFilesDir`; a restored verifier would still be unusable because its non-exportable Keystore HMAC key does not migrate. The model downloader accepts HTTPS only.

```xml
<!-- res/xml/backup_rules.xml -->
<full-backup-content>
    <exclude domain="root" path="." />
    <exclude domain="file" path="." />
    <exclude domain="database" path="." />
    <exclude domain="sharedpref" path="." />
    <exclude domain="external" path="." />
    <exclude domain="device_root" path="." />
    <exclude domain="device_file" path="." />
    <exclude domain="device_database" path="." />
    <exclude domain="device_sharedpref" path="." />
</full-backup-content>
```

```xml
<!-- res/xml/data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup disableIfNoEncryptionCapabilities="true">
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
        <exclude domain="device_root" path="." />
        <exclude domain="device_file" path="." />
        <exclude domain="device_database" path="." />
        <exclude domain="device_sharedpref" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
        <exclude domain="device_root" path="." />
        <exclude domain="device_file" path="." />
        <exclude domain="device_database" path="." />
        <exclude domain="device_sharedpref" path="." />
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Step 5: Add the minimal application entry point**

```kotlin
class OpenDeviceNodeApp : Application() {
    companion object { const val PACKAGE_NAME = "dev.opendevice.node" }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("OpenDevice Node") } }
    }
}
```

- [ ] **Step 6: Run the first red/green build cycle**

Run before creating `OpenDeviceNodeApp.kt`:

```bash
ANDROID_HOME=/Users/huahua/Library/Android/sdk JAVA_HOME=/opt/homebrew/opt/openjdk@17 apps/android-node/gradlew -p apps/android-node :app:testDebugUnitTest
```

Expected before implementation: compilation fails because `OpenDeviceNodeApp` is unresolved. Run again after implementation; expected: `BUILD SUCCESSFUL` and `BuildContractTest` passes.

- [ ] **Step 7: Build the debug APK and inspect its SDK/ABI contract**

```bash
ANDROID_HOME=/Users/huahua/Library/Android/sdk JAVA_HOME=/opt/homebrew/opt/openjdk@17 apps/android-node/gradlew -p apps/android-node :app:assembleDebug
/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest/bin/apkanalyzer manifest min-sdk apps/android-node/app/build/outputs/apk/debug/app-debug.apk
/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest/bin/apkanalyzer manifest target-sdk apps/android-node/app/build/outputs/apk/debug/app-debug.apk
/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest/bin/apkanalyzer files list apps/android-node/app/build/outputs/apk/debug/app-debug.apk | rg 'lib/arm64-v8a|lib/(x86|armeabi)'
```

Expected: min SDK `28`, target SDK `36`, and no native architecture other than `arm64-v8a` once native libraries are added.

- [ ] **Step 8: Commit the baseline**

```bash
git add .gitignore apps/android-node
git commit -m "build(android): add reproducible node app baseline"
```

### Task 2: Canonical Cross-Platform Module Contract

**Files:**
- Create: `packages/core/schema/opendevice.module.v1.schema.json`
- Create: `packages/module-contract/package.json`
- Create: `packages/module-contract/tsconfig.json`
- Create: `packages/module-contract/tsconfig.build.json`
- Create: `packages/module-contract/src/generated/module-manifest.ts`
- Create: `packages/module-contract/src/index.ts`
- Create: `packages/module-contract/src/validate.ts`
- Create: `packages/module-contract/src/validate.test.ts`
- Create: `packages/module-contract/src/integrity.ts`
- Create: `packages/module-contract/src/integrity.test.ts`
- Create: `packages/module-contract/scripts/generate.mjs`
- Create: `packages/module-contract/fixtures/ai-node.json`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/contract/ModuleManifest.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/contract/ModuleManifestFixtureTest.kt`
- Modify: `apps/android-node/app/build.gradle.kts`
- Modify: `packages/core/src/plugin/types.ts`
- Modify: `packages/core/src/plugin/manifest.ts`
- Modify: `packages/core/src/plugin/manifest.test.ts`
- Modify: `packages/core/package.json`
- Modify: `pnpm-lock.yaml`

**Interfaces:**
- Consumes: existing `PluginManifest`, validator semantics, and Android serialization runtime.
- Produces: `ModuleManifestV1`, `validateModuleManifest(value: unknown): ModuleValidationResult`, Kotlin `ModuleManifest`, and one shared `ai-node.json` fixture.

- [ ] **Step 1: Write failing schema-validation tests**

```ts
import fixture from "../fixtures/ai-node.json" with { type: "json" };
import { describe, expect, it } from "vitest";
import { validateModuleManifest } from "./validate";

describe("validateModuleManifest", () => {
  it("accepts the built-in AI node fixture", () => {
    expect(validateModuleManifest(fixture, host)).toEqual({ ok: true, errors: [] });
  });

  it("rejects a missing immutable module id", () => {
    const { id: _id, ...invalid } = fixture;
    const result = validateModuleManifest(invalid, host);
    expect(result.ok).toBe(false);
    expect(result.errors.some((error) => error.path === "/id")).toBe(true);
  });
});
```

Add explicit negative cases for an unknown permission, a GitHub source without `revision`, a package integrity record without both SHA-256 and publisher signature, kernel version outside `[min, maxExclusive)`, Android SDK below `minSdk`, missing required ABI, runtime kind absent from `availableRuntimes`, invalid contribution placement, and high-risk/general-audience combination. Each test asserts a stable error code and JSON Pointer path.

Add the shared fixture directory to Android unit-test resources without copying the JSON:

```kotlin
android.sourceSets["test"].resources.srcDir("../../../packages/module-contract/fixtures")
```

The Android fixture test reads `/ai-node.json` from the classpath with `Json { ignoreUnknownKeys = false }` and asserts `id == "dev.opendevice.module.ai-node"`, `runtime.kind == RuntimeKind.BUILTIN`, and `permissions == listOf("device.read", "model.read", "network.outbound", "service.local")`.

- [ ] **Step 2: Define the complete V1 schema and pinned AI fixture**

The schema must set `additionalProperties: false` at every object level. This is the complete top-level shape; factor repeated field objects into `$defs` only if the accepted instances and conditional requirements remain byte-for-byte equivalent:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://opendevice.dev/contracts/module-manifest-v1.schema.json",
  "title": "ModuleManifestV1",
  "type": "object",
  "additionalProperties": false,
  "required": ["manifestVersion", "id", "name", "summary", "version", "publisher", "license", "kernel", "platform", "source", "integrity", "runtime", "audience", "risk", "permissions", "capabilities", "contributes", "configuration", "service", "protected"],
  "properties": {
    "manifestVersion": { "const": "1" },
    "id": { "type": "string", "pattern": "^(?:[a-z0-9]+[.-])+[a-z0-9-]+$" },
    "name": { "type": "string", "minLength": 1, "maxLength": 80 },
    "summary": { "type": "string", "minLength": 1, "maxLength": 240 },
    "version": { "type": "string", "pattern": "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?$" },
    "publisher": { "type": "string", "minLength": 1, "maxLength": 80 },
    "license": { "type": "string", "minLength": 1, "maxLength": 80 },
    "kernel": {
      "type": "object", "additionalProperties": false,
      "required": ["min", "maxExclusive"],
      "properties": { "min": { "type": "string" }, "maxExclusive": { "type": "string" } }
    },
    "platform": {
      "type": "object", "additionalProperties": false,
      "required": ["android", "hardware"],
      "properties": {
        "android": {
          "type": "object", "additionalProperties": false,
          "required": ["minSdk", "abis"],
          "properties": {
            "minSdk": { "type": "integer", "minimum": 21 },
            "abis": { "type": "array", "minItems": 1, "uniqueItems": true, "items": { "enum": ["arm64-v8a", "armeabi-v7a", "x86_64", "x86"] } }
          }
        },
        "hardware": {
          "type": "object", "additionalProperties": false,
          "required": ["minMemoryBytes", "minStorageBytes"],
          "properties": {
            "minMemoryBytes": { "type": ["integer", "null"], "minimum": 0 },
            "minStorageBytes": { "type": ["integer", "null"], "minimum": 0 }
          }
        }
      }
    },
    "source": {
      "type": "object", "additionalProperties": false,
      "required": ["kind"],
      "properties": {
        "kind": { "enum": ["builtin", "official", "community", "github", "local"] },
        "repository": { "type": "string", "pattern": "^https://" },
        "revision": { "type": "string", "minLength": 7, "maxLength": 128 }
      },
      "allOf": [
        {
          "if": { "properties": { "kind": { "enum": ["official", "community", "github"] } }, "required": ["kind"] },
          "then": { "required": ["repository", "revision"] }
        }
      ]
    },
    "integrity": {
      "type": "object", "additionalProperties": false,
      "required": ["kind", "sha256", "publisherKeySha256", "publisherSignature", "rollbackVersion"],
      "properties": {
        "kind": { "enum": ["host-apk", "package"] },
        "sha256": { "type": ["string", "null"], "pattern": "^[a-f0-9]{64}$" },
        "publisherKeySha256": { "type": ["string", "null"], "pattern": "^[a-f0-9]{64}$" },
        "publisherSignature": { "type": ["string", "null"], "minLength": 16 },
        "rollbackVersion": { "type": ["string", "null"] }
      },
      "allOf": [
        {
          "if": { "properties": { "kind": { "const": "package" } }, "required": ["kind"] },
          "then": {
            "properties": {
              "sha256": { "type": "string", "pattern": "^[a-f0-9]{64}$" },
              "publisherKeySha256": { "type": "string", "pattern": "^[a-f0-9]{64}$" },
              "publisherSignature": { "type": "string", "minLength": 16 }
            }
          }
        }
      ]
    },
    "runtime": {
      "type": "object", "additionalProperties": false,
      "required": ["kind", "entry", "foregroundService", "isolatedProcess", "requiresRootBroker", "companionPackage"],
      "properties": {
        "kind": { "enum": ["builtin", "declarative", "sandbox", "companion"] },
        "entry": { "type": "string", "pattern": "^[a-z0-9][a-z0-9:._/-]*$" },
        "foregroundService": { "type": "boolean" },
        "isolatedProcess": { "type": "boolean" },
        "requiresRootBroker": { "type": "boolean" },
        "companionPackage": { "type": ["string", "null"], "pattern": "^(?:[a-z0-9]+\\.)+[a-z0-9_]+$" }
      }
    },
    "audience": { "enum": ["general", "advanced"] },
    "risk": { "enum": ["low", "medium", "high"] },
    "permissions": {
      "type": "array", "uniqueItems": true,
      "items": { "enum": ["device.read", "model.read", "network.outbound", "service.local", "display.fullscreen", "device.bluetooth", "device.usb", "media.camera", "media.microphone", "location.read", "root.broker"] }
    },
    "capabilities": { "type": "array", "minItems": 1, "uniqueItems": true, "items": { "type": "string", "pattern": "^[a-z][a-z0-9.-]+$" } },
    "contributes": {
      "type": "array", "minItems": 1,
      "items": {
        "type": "object", "additionalProperties": false,
        "required": ["id", "type", "label", "defaultPlacement"],
        "properties": {
          "id": { "type": "string", "pattern": "^[a-z][a-z0-9.-]+$" },
          "type": { "enum": ["navigation", "overviewSection", "overviewAction", "configuration", "workflow", "service", "screen", "deviceControl", "companionApp", "reportSection"] },
          "label": { "type": "string", "minLength": 1, "maxLength": 80 },
          "defaultPlacement": { "enum": ["sidebar", "overview", "shortcut", "service", "report", "context", "fullscreen"] }
        }
      }
    },
    "configuration": {
      "type": "object", "additionalProperties": false,
      "required": ["fields"],
      "properties": {
        "fields": {
          "type": "array",
          "items": {
            "type": "object", "additionalProperties": false,
            "required": ["key", "label", "type", "required", "default", "minimum", "maximum", "pattern", "options"],
            "properties": {
              "key": { "type": "string", "pattern": "^[a-z][A-Za-z0-9]*$" },
              "label": { "type": "string", "minLength": 1, "maxLength": 80 },
              "type": { "enum": ["boolean", "integer", "number", "string", "select"] },
              "required": { "type": "boolean" },
              "default": { "type": ["boolean", "integer", "number", "string", "null"] },
              "minimum": { "type": ["number", "null"] },
              "maximum": { "type": ["number", "null"] },
              "pattern": { "type": ["string", "null"] },
              "options": { "type": "array", "items": { "type": "string" } }
            }
          }
        }
      }
    },
    "service": {
      "oneOf": [
        { "type": "null" },
        {
          "type": "object", "additionalProperties": false,
          "required": ["start", "stop", "healthPath", "logFields", "result"],
          "properties": {
            "start": { "const": "explicit" },
            "stop": { "const": "explicit" },
            "healthPath": { "type": "string", "pattern": "^/" },
            "logFields": { "type": "array", "uniqueItems": true, "items": { "enum": ["requestId", "timestamp", "model", "inputTokens", "outputTokens", "durationMillis", "statusCode", "peakRssBytes", "batteryTemperatureC"] } },
            "result": {
              "type": "object", "additionalProperties": false,
              "required": ["kind", "schemaId", "contentTypes"],
              "properties": {
                "kind": { "enum": ["structured", "stream"] },
                "schemaId": { "type": "string", "pattern": "^[a-z][a-z0-9.-]+$" },
                "contentTypes": { "type": "array", "minItems": 1, "uniqueItems": true, "items": { "type": "string" } }
              }
            }
          }
        }
      ]
    },
    "protected": { "type": "boolean" }
  }
}
```

Use this fixture content:

```json
{
  "manifestVersion": "1",
  "id": "dev.opendevice.module.ai-node",
  "name": "本地 AI 节点",
  "summary": "在手机本地运行模型，并向电脑提供兼容接口。",
  "version": "0.1.0",
  "publisher": "OpenDevice Forge",
  "license": "MIT",
  "kernel": { "min": "0.1.0", "maxExclusive": "1.0.0" },
  "platform": {
    "android": { "minSdk": 28, "abis": ["arm64-v8a"] },
    "hardware": { "minMemoryBytes": 1176317600, "minStorageBytes": 907882144 }
  },
  "source": { "kind": "builtin" },
  "integrity": { "kind": "host-apk", "sha256": null, "publisherKeySha256": null, "publisherSignature": null, "rollbackVersion": null },
  "runtime": { "kind": "builtin", "entry": "ai-node", "foregroundService": true, "isolatedProcess": false, "requiresRootBroker": false, "companionPackage": null },
  "audience": "general",
  "risk": "medium",
  "permissions": ["device.read", "model.read", "network.outbound", "service.local"],
  "capabilities": ["ai.chat", "api.openai-compatible", "ui.local-chat"],
  "contributes": [
    { "id": "ai-node.service", "type": "service", "label": "本地 AI 节点", "defaultPlacement": "service" },
    { "id": "ai-node.chat", "type": "navigation", "label": "本机聊天", "defaultPlacement": "sidebar" },
    { "id": "ai-node.download", "type": "workflow", "label": "下载并校验模型", "defaultPlacement": "shortcut" },
    { "id": "ai-node.settings", "type": "configuration", "label": "节点设置", "defaultPlacement": "context" },
    { "id": "ai-node.status", "type": "overviewSection", "label": "节点状态", "defaultPlacement": "overview" }
  ],
  "configuration": {
    "fields": [
      { "key": "port", "label": "端口", "type": "integer", "required": true, "default": 8080, "minimum": 1024, "maximum": 65535, "pattern": null, "options": [] },
      { "key": "lanEnabled", "label": "局域网连接", "type": "boolean", "required": true, "default": false, "minimum": null, "maximum": null, "pattern": null, "options": [] },
      { "key": "contextSize", "label": "上下文长度", "type": "integer", "required": true, "default": 2048, "minimum": 2048, "maximum": 2048, "pattern": null, "options": [] },
      { "key": "maxOutputTokens", "label": "最大输出", "type": "integer", "required": true, "default": 256, "minimum": 1, "maximum": 512, "pattern": null, "options": [] },
      { "key": "threads", "label": "推理线程", "type": "integer", "required": true, "default": 2, "minimum": 2, "maximum": 4, "pattern": null, "options": [] }
    ]
  },
  "service": {
    "start": "explicit",
    "stop": "explicit",
    "healthPath": "/health",
    "logFields": ["requestId", "timestamp", "model", "inputTokens", "outputTokens", "durationMillis", "statusCode", "peakRssBytes", "batteryTemperatureC"],
    "result": {
      "kind": "stream",
      "schemaId": "openai.chat.completion.v1",
      "contentTypes": ["application/json", "text/event-stream"]
    }
  },
  "protected": true
}
```

- [ ] **Step 3: Generate TypeScript and Kotlin types from the schema**

Add `quicktype-core@26.0.0` and make `pnpm --filter @opendevice/module-contract generate` read the schema, emit TypeScript to `src/generated/module-manifest.ts`, and Kotlin serializable data classes to `apps/android-node/app/src/main/java/dev/opendevice/node/contract/ModuleManifest.kt`. The generator must write deterministic UTF-8 with one trailing newline and expose a `--check` mode that compares generated bytes without changing files.

Use this package contract; `tsconfig.json` extends `../../tsconfig.base.json`, includes `src`, and enables `resolveJsonModule`. `tsconfig.build.json` mirrors `packages/core/tsconfig.build.json` with `rootDir: "src"` and excludes tests.

```json
{
  "name": "@opendevice/module-contract",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "exports": { ".": "./src/index.ts" },
  "scripts": {
    "generate": "node scripts/generate.mjs",
    "lint": "oxlint src scripts",
    "typecheck": "tsc -p tsconfig.json",
    "test": "vitest run",
    "build": "pnpm generate -- --check && tsc -p tsconfig.build.json"
  },
  "dependencies": { "ajv": "8.20.0" },
  "devDependencies": {
    "@types/node": "24.13.3",
    "oxlint": "1.78.0",
    "quicktype-core": "26.0.0",
    "typescript": "7.0.2",
    "vitest": "4.1.10"
  }
}
```

```js
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { FetchingJSONSchemaStore, InputData, JSONSchemaInput, quicktype } from "quicktype-core";

const mode = process.argv.includes("--check") ? "check" : "write";
const schema = await readFile(new URL("../../core/schema/opendevice.module.v1.schema.json", import.meta.url), "utf8");
const targets = [
  {
    lang: "typescript",
    out: new URL("../src/generated/module-manifest.ts", import.meta.url),
    rendererOptions: { "just-types": "true" },
  },
  {
    lang: "kotlin",
    out: new URL("../../../apps/android-node/app/src/main/java/dev/opendevice/node/contract/ModuleManifest.kt", import.meta.url),
    rendererOptions: { framework: "kotlinx", package: "dev.opendevice.node.contract" },
  },
];

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
  const generated = `${lines.join("\n")}\n`;
  const path = fileURLToPath(target.out);
  if (mode === "check") {
    const current = await readFile(path, "utf8").catch(() => "");
    if (current !== generated) throw new Error(`generated file is stale: ${path}`);
  } else {
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, generated, "utf8");
  }
}
```

- [ ] **Step 4: Implement Ajv validation and the legacy compatibility adapter**

```ts
export interface ModuleHostContext {
  kernelVersion: string;
  androidSdk: number;
  abis: string[];
  availableRuntimes: Array<"builtin" | "declarative" | "sandbox" | "companion">;
}
export interface ModuleValidationError { code: string; path: string; message: string }
export type ModuleValidationResult =
  | { ok: true; errors: [] }
  | { ok: false; errors: ModuleValidationError[] };

export const validateModuleManifest = (value: unknown, host: ModuleHostContext): ModuleValidationResult => {
  const ok = validate(value);
  if (!ok) return {
    ok: false,
    errors: (validate.errors ?? []).map((error) => ({
      code: `schema.${error.keyword}`,
      path: error.instancePath || `/${String(error.params.missingProperty ?? "")}`,
      message: error.message ?? "invalid module manifest",
    })),
  };
  return validateSemanticCompatibility(value as ModuleManifestV1, host);
};
```

`validateSemanticCompatibility` uses the existing tested semver comparator; it emits stable codes `kernel_incompatible`, `android_sdk_incompatible`, `abi_incompatible`, `runtime_unavailable`, `placement_mismatch`, and `risk_audience_mismatch`. Keep the existing `PluginManifest` API source-compatible. Add `moduleManifestToLegacyPlugin(manifest: ModuleManifestV1): PluginManifest` in `packages/core/src/plugin/manifest.ts`; map built-in source to `official`, built-in runtime to native service, `api.openai-compatible` to a service contribution, and only permissions already recognized by the legacy registry. A focused test must prove the AI fixture validates in the new contract and adapts without weakening the legacy validator.

`packages/module-contract/src/index.ts` contains only stable exports:

```ts
export type * from "./generated/module-manifest";
export { validateModuleManifest } from "./validate";
export type { ModuleHostContext, ModuleValidationError, ModuleValidationResult } from "./validate";
export { verifyPackageIntegrity } from "./integrity";
export type { PackageIntegrityResult } from "./integrity";
```

Implement `verifyPackageIntegrity(manifest, packageBytes, publisherPublicKeySpki)` with Node `crypto`: SHA-256 the package bytes, compare it to `integrity.sha256`, SHA-256 the DER SPKI bytes and compare it to `publisherKeySha256`, then verify the Ed25519 signature over UTF-8 `${manifest.id}\n${manifest.version}\n${integrity.sha256}\n`. Return one of `verified`, `not_package`, `hash_mismatch`, `publisher_key_mismatch`, or `signature_mismatch`; never throw for attacker-controlled bytes. Tests generate an ephemeral Ed25519 key pair and independently corrupt package bytes, key bytes, and signature bytes to exercise all three mismatch results.

- [ ] **Step 5: Run contract and generation-drift tests**

```bash
pnpm --filter @opendevice/module-contract generate
pnpm --filter @opendevice/module-contract generate -- --check
pnpm --filter @opendevice/module-contract test
pnpm --filter @opendevice/core test
JAVA_HOME=/opt/homebrew/opt/openjdk@17 apps/android-node/gradlew -p apps/android-node :app:testDebugUnitTest --tests '*ModuleManifestFixtureTest'
```

Expected: all commands succeed; changing either generated file makes `generate -- --check` exit non-zero.

- [ ] **Step 6: Commit the contract**

```bash
git add packages/module-contract packages/core apps/android-node/app/build.gradle.kts apps/android-node/app/src/main/java/dev/opendevice/node/contract apps/android-node/app/src/test pnpm-lock.yaml
git commit -m "feat(contract): add shared module manifest v1"
```

### Task 3: Persistent Phone Module Registry and Safe Mode

**Files:**
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/kernel/ModuleRecord.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/kernel/ModuleRegistry.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/kernel/DataStoreModuleRegistry.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/kernel/ModuleCompatibility.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/kernel/CrashWindow.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/kernel/ModuleStartupGuard.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/kernel/ModuleRegistryTest.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/kernel/CrashWindowTest.kt`
- Modify: `apps/android-node/app/src/main/java/dev/opendevice/node/OpenDeviceNodeApp.kt`

**Interfaces:**
- Consumes: Kotlin `ModuleManifest` and the built-in AI fixture fields from Task 2.
- Produces: `ModuleRegistry.snapshot: StateFlow<ModuleRegistrySnapshot>`, `enable(id)`, `disable(id)`, `recordCrash(id, atMillis)`, `exitSafeMode()`, and `ModuleStartupGuard` for detecting repeated startup failures across process restarts.

- [ ] **Step 1: Write failing registry and crash-window tests**

```kotlin
@Test fun builtinAiModuleStartsInstalledButDisabled() = runTest {
    val registry = InMemoryModuleRegistry(clock = { 0L })
    registry.installBuiltin(aiManifest)
    val module = registry.snapshot.value.modules.single()
    assertTrue(module.installed)
    assertFalse(module.enabled)
}

@Test fun thirdCrashInsideTenMinutesEntersSafeMode() = runTest {
    val registry = InMemoryModuleRegistry(clock = { 0L })
    registry.recordCrash("community.demo", 0L)
    registry.recordCrash("community.demo", 60_000L)
    registry.recordCrash("community.demo", 9 * 60_000L)
    assertTrue(registry.snapshot.value.safeMode)
}

@Test fun crashAtTenMinuteBoundaryDoesNotCount() {
    val window = CrashWindow(limit = 3, durationMillis = 600_000L)
    assertFalse(window.add(0L))
    assertFalse(window.add(1L))
    assertFalse(window.add(600_000L))
}
```

- [ ] **Step 2: Define registry state and commands**

```kotlin
@Serializable data class ModuleRecord(
    val manifest: ModuleManifest,
    val installed: Boolean,
    val enabled: Boolean,
    val crashTimestamps: List<Long> = emptyList(),
)

@Serializable data class ModuleAuditEvent(
    val atMillis: Long,
    val moduleId: String,
    val action: ModuleAuditAction,
    val result: String,
)

enum class ModuleAuditAction { INSTALL, ENABLE, DISABLE, SAFE_MODE_ENTER, SAFE_MODE_EXIT }

data class ModuleRegistrySnapshot(
    val modules: List<ModuleRecord>,
    val safeMode: Boolean,
    val audit: List<ModuleAuditEvent> = emptyList(),
)

interface ModuleRegistry {
    val snapshot: StateFlow<ModuleRegistrySnapshot>
    suspend fun installBuiltin(manifest: ModuleManifest)
    suspend fun enable(id: String): RegistryResult
    suspend fun disable(id: String): RegistryResult
    suspend fun recordCrash(id: String, atMillis: Long)
    suspend fun exitSafeMode()
}

sealed interface RegistryResult {
    data object Changed : RegistryResult
    data class Rejected(val reason: String) : RegistryResult
}
```

`enable` rejects unknown IDs, manifests incompatible with kernel `0.1.0`/current Android SDK/current ABI/built-in runtime, and safe-mode third-party modules. `disable` is idempotent. The protected AI module may be disabled but not removed. `exitSafeMode` clears the safe-mode flag but does not automatically enable any module. Every accepted or rejected mutation appends a redacted audit event; retain the newest 200 records.

- [ ] **Step 3: Implement the exact crash-window rule**

```kotlin
class CrashWindow(private val limit: Int, private val durationMillis: Long) {
    private val events = ArrayDeque<Long>()
    fun add(atMillis: Long): Boolean {
        while (events.isNotEmpty() && atMillis - events.first() >= durationMillis) {
            events.removeFirst()
        }
        events.addLast(atMillis)
        return events.size >= limit
    }
}
```

`ModuleCompatibility` mirrors the TypeScript semantic checks using exact error codes and the generated manifest fields. Add Kotlin tests for unsupported kernel, SDK, ABI, runtime, placement, and high-risk/general manifests so Android cannot enable a manifest merely because TypeScript validated a different copy.

The production registry stores one JSON document in Preferences DataStore key `module_registry_v1`. Mutations run under a `Mutex`, persist before publishing the new snapshot, and recover malformed storage by preserving the bad payload in `module_registry_v1_corrupt` and re-creating only the disabled protected built-in module. The recovery and its reason are recorded as an audit event without storing the malformed payload in logs.

`ModuleStartupGuard` uses a private SharedPreferences file because its marker must be synchronously committed before native loading begins:

```kotlin
data class StartupMarker(val moduleId: String, val startedAtMillis: Long)

interface ModuleStartupGuard {
    fun markStarting(moduleId: String, atMillis: Long): Boolean
    fun markStable(moduleId: String): Boolean
    fun markCleanStop(moduleId: String): Boolean
    fun consumeUnstablePreviousStart(nowMillis: Long): StartupMarker?
}
```

`markStarting` commits module ID and time. `markStable` clears only the matching marker after 60 seconds of continuous Serving. `markCleanStop` clears on explicit or policy stop. At application start, `consumeUnstablePreviousStart` atomically reads and clears a marker; it returns it only when its age is within ten minutes, otherwise it discards it. `OpenDeviceNodeApp` forwards a returned marker to `ModuleRegistry.recordCrash`. Add tests for process recreation inside the window, stale marker discard, wrong-module clear rejection, and third unstable start entering safe mode. This mechanism is described in the UI as “连续启动失败保护”, not as proof of a Java or native crash cause.

- [ ] **Step 4: Wire application-scoped registry creation**

```kotlin
class OpenDeviceNodeApp : Application() {
    lateinit var moduleRegistry: ModuleRegistry
        private set

    override fun onCreate() {
        super.onCreate()
        moduleRegistry = DataStoreModuleRegistry.create(
            context = this,
            builtins = listOf(BuiltinModules.aiNode),
            clock = System::currentTimeMillis,
        )
    }

    companion object { const val PACKAGE_NAME = "dev.opendevice.node" }
}
```

- [ ] **Step 5: Run the focused tests**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 apps/android-node/gradlew -p apps/android-node :app:testDebugUnitTest --tests 'dev.opendevice.node.kernel.*'
```

Expected: the installed/disabled default, ten-minute boundary, third-crash safe mode, persistence round trip, malformed-data recovery, and explicit safe-mode exit tests pass.

- [ ] **Step 6: Commit the module kernel**

```bash
git add apps/android-node/app/src/main/java/dev/opendevice/node/kernel apps/android-node/app/src/main/java/dev/opendevice/node/OpenDeviceNodeApp.kt apps/android-node/app/src/test/java/dev/opendevice/node/kernel
git commit -m "feat(android): add persistent module kernel"
```

### Task 4: Truthful Device Facts and Four-Screen Shell

**Files:**
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/device/DeviceFacts.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/device/DeviceFactsSource.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/device/AndroidDeviceFactsSource.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/device/DeviceFactsTest.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/NodeApp.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/NodeAppState.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/NodeScreen.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/ModulesScreen.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/ConnectionsScreen.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/StatusScreen.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/ui/NodeAppStateTest.kt`
- Modify: `apps/android-node/app/src/main/java/dev/opendevice/node/MainActivity.kt`

**Interfaces:**
- Consumes: `ModuleRegistry.snapshot: StateFlow<ModuleRegistrySnapshot>`.
- Produces: `DeviceFactsSource.observe(): Flow<DeviceFacts>`, `NodeAppState`, and four reachable top-level destinations.

- [ ] **Step 1: Write failing device-normalization and navigation tests**

```kotlin
@Test fun bytesAreDisplayedWithoutInventingCapacity() {
    val facts = DeviceFacts(totalMemoryBytes = 7_962_624_000L, availableMemoryBytes = 3_221_225_472L)
    assertEquals("7.4 GiB", facts.totalMemoryLabel)
    assertEquals("3.0 GiB", facts.availableMemoryLabel)
}

@Test fun allFourDestinationsAreStable() {
    assertEquals(
        listOf("node", "modules", "connections", "status"),
        NodeDestination.entries.map(NodeDestination::route),
    )
}
```

- [ ] **Step 2: Define a nullable, source-labelled facts model**

```kotlin
data class DeviceFacts(
    val manufacturer: String = "",
    val model: String = "",
    val sdkInt: Int = 0,
    val supportedAbis: List<String> = emptyList(),
    val totalMemoryBytes: Long? = null,
    val availableMemoryBytes: Long? = null,
    val allocatableStorageBytes: Long? = null,
    val batteryPercent: Int? = null,
    val batteryTemperatureC: Float? = null,
    val thermalStatus: ThermalLevel = ThermalLevel.UNKNOWN,
    val rootSignals: List<String> = emptyList(),
) {
    val isRootConfirmed: Boolean get() = false
    val totalMemoryLabel: String get() = totalMemoryBytes.toGibLabel()
    val availableMemoryLabel: String get() = availableMemoryBytes.toGibLabel()
}

private fun Long?.toGibLabel(): String = this?.let {
    String.format(Locale.US, "%.1f GiB", it.toDouble() / 1_073_741_824.0)
} ?: "未读取"

enum class ThermalLevel { UNKNOWN, NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN }

interface DeviceFactsSource { fun observe(): Flow<DeviceFacts> }
```

`isRootConfirmed` must remain false in this release. `rootSignals` may report read-only observations such as a `su` path, but the UI labels them “线索，未经授权验证” and never executes `su`.

- [ ] **Step 3: Read every value from Android APIs**

`AndroidDeviceFactsSource` samples every five seconds while collected. It uses `ActivityManager.MemoryInfo`, `StorageManager.getAllocatableBytes(StorageManager.UUID_DEFAULT)`, `ACTION_BATTERY_CHANGED` with `EXTRA_TEMPERATURE / 10f` and `EXTRA_LEVEL * 100 / EXTRA_SCALE`, `PowerManager.currentThermalStatus` on API 29+, and maps pre-29 thermal status to `UNKNOWN`. It catches `IOException` and `SecurityException` per measurement and returns null for the affected field rather than substituting a guessed number.

```kotlin
override fun observe(): Flow<DeviceFacts> = flow {
    while (currentCoroutineContext().isActive) {
        emit(readOnce())
        delay(5_000L)
    }
}.distinctUntilChanged()
```

- [ ] **Step 4: Build the fixed four-destination Compose shell**

```kotlin
enum class NodeDestination(val route: String, val label: String) {
    NODE("node", "节点"),
    MODULES("modules", "模块"),
    CONNECTIONS("connections", "连接"),
    STATUS("status", "状态"),
}

data class NodeAppState(
    val destination: NodeDestination = NodeDestination.NODE,
    val modules: ModuleRegistrySnapshot = ModuleRegistrySnapshot(emptyList(), false),
    val facts: DeviceFacts = DeviceFacts(),
)
```

Use `NavigationBar` with all four labels always visible. The Node screen shows “尚未启动” and no fake throughput. Modules shows the installed AI module and disabled state. Connections shows localhost as available and LAN as off. Status shows only collected values, with `未读取` for nulls and a persistent warning when safe mode is active.

- [ ] **Step 5: Run unit and Compose compilation checks**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 apps/android-node/gradlew -p apps/android-node :app:testDebugUnitTest --tests 'dev.opendevice.node.device.*' --tests 'dev.opendevice.node.ui.*' :app:assembleDebug
```

Expected: normalization and destination tests pass, and every screen compiles on minSdk 28 without calling an API-29 method outside a version guard.

- [ ] **Step 6: Commit facts and navigation**

```bash
git add apps/android-node/app/src/main/java/dev/opendevice/node/device apps/android-node/app/src/main/java/dev/opendevice/node/ui apps/android-node/app/src/main/java/dev/opendevice/node/MainActivity.kt apps/android-node/app/src/test/java/dev/opendevice/node/device apps/android-node/app/src/test/java/dev/opendevice/node/ui
git commit -m "feat(android): show measured node status"
```

### Task 5: Pinned Model Catalog and Verified Resumable Download

**Files:**
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/model/ModelDescriptor.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/model/BuiltinModelCatalog.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/model/ModelDownloadState.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/model/ModelDownloadRepository.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/model/HttpModelDownloadRepository.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/model/ModelByteSource.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/model/ModelDownloadWorker.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/model/ModelDownloadRepositoryTest.kt`
- Modify: `apps/android-node/app/src/main/AndroidManifest.xml`
- Modify: `apps/android-node/app/src/main/java/dev/opendevice/node/OpenDeviceNodeApp.kt`

**Interfaces:**
- Consumes: application-private files, WorkManager, and the exact model identity in Global Constraints.
- Produces: `BuiltinModelCatalog.qwen3_0_6b`, `ModelDownloadRepository.state: StateFlow<ModelDownloadState>`, `enqueue()`, `cancel()`, `downloadNow()`, and `verifiedModelFile(): File?`.

- [ ] **Step 1: Write failing integrity and resume tests against a local fake HTTP source**

```kotlin
@Test fun resumesPartFileThenAtomicallyPublishesVerifiedModel() = runTest {
    val bytes = ByteArray(1024) { (it % 251).toByte() }
    val source = FakeRangeSource(bytes)
    partFile.writeBytes(bytes.copyOfRange(0, 256))
    val repo = repository(source, descriptor(size = 1024, sha256 = sha256(bytes)))

    repo.downloadNow()

    assertEquals("bytes=256-", source.lastRange)
    assertContentEquals(bytes, finalFile.readBytes())
    assertFalse(partFile.exists())
    assertIs<ModelDownloadState.Ready>(repo.state.value)
}

@Test fun hashMismatchNeverPublishesModel() = runTest {
    val repo = repository(FakeRangeSource(ByteArray(16)), descriptor(size = 16, sha256 = "0".repeat(64)))
    repo.downloadNow()
    assertFalse(finalFile.exists())
    assertIs<ModelDownloadState.FailedIntegrity>(repo.state.value)
}
```

Also test: server ignores Range and returns 200, server returns 416 for a complete `.part`, insufficient storage, cancellation keeps the `.part`, and an exact valid final file skips network access.

- [ ] **Step 2: Encode the immutable first-model descriptor**

```kotlin
@Serializable data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val revision: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val license: String,
)

object BuiltinModelCatalog {
    val qwen3_0_6b = ModelDescriptor(
        id = "qwen3-0.6b-q8_0",
        displayName = "Qwen3 0.6B Q8_0",
        revision = "23749fefcc72300e3a2ad315e1317431b06b590a",
        fileName = "Qwen3-0.6B-Q8_0.gguf",
        url = "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/23749fefcc72300e3a2ad315e1317431b06b590a/Qwen3-0.6B-Q8_0.gguf?download=true",
        sizeBytes = 639_446_688L,
        sha256 = "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031",
        license = "Apache-2.0",
    )
}
```

- [ ] **Step 3: Define observable download state and storage layout**

```kotlin
sealed interface ModelDownloadState {
    data object Missing : ModelDownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadState
    data class Verifying(val totalBytes: Long) : ModelDownloadState
    data class Ready(val file: File) : ModelDownloadState
    data class BlockedStorage(val requiredBytes: Long, val allocatableBytes: Long) : ModelDownloadState
    data class FailedNetwork(val message: String) : ModelDownloadState
    data class FailedIntegrity(val expectedSha256: String, val actualSha256: String) : ModelDownloadState
}

interface ModelDownloadRepository {
    val state: StateFlow<ModelDownloadState>
    fun enqueue()
    fun cancel()
    suspend fun downloadNow()
    suspend fun verifiedModelFile(): File?
}

data class ModelRangeResponse(
    val statusCode: Int,
    val contentRange: String?,
    val location: String?,
    val body: InputStream,
)

fun interface ModelByteSource {
    suspend fun open(url: URL, startByte: Long?): ModelRangeResponse
}
```

Store under `noBackupFilesDir/models/qwen3-0.6b-q8_0/`: final file at the descriptor filename and partial file with an additional `.part` suffix. Require allocatable storage of `remaining download bytes + 256 MiB` before opening the network connection.

- [ ] **Step 4: Implement exact HTTP resume behavior**

For partial length `n > 0`, send `Range: bytes=n-`. Accept 206 only when `Content-Range` starts with `bytes n-`. If the server returns 200, truncate the partial file and restart from byte zero using that same response. On 416, verify the partial file only when its length equals the descriptor size; otherwise delete the partial and retry once from zero. Follow at most five HTTPS redirects and reject a redirect to non-HTTPS.

Use a 15-second connect timeout, 30-second read timeout, an 8 MiB buffer, `FileDescriptor.sync()` after the last write, exact length check, streaming SHA-256, then `Files.move(part, final, ATOMIC_MOVE)` with same-directory `renameTo` fallback. Never rename before both checks pass.

- [ ] **Step 5: Run downloads only as explicit WorkManager jobs**

```kotlin
val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setInputData(workDataOf("modelId" to descriptor.id))
    .build()
workManager.enqueueUniqueWork("model:${descriptor.id}", ExistingWorkPolicy.KEEP, request)
```

The phone UI starts this job only from a Download button. Cancellation calls `cancelUniqueWork`; no boot receiver and no automatic retry are added. The worker creates a data-sync foreground notification showing real byte progress.

- [ ] **Step 6: Run the focused download suite**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 apps/android-node/gradlew -p apps/android-node :app:testDebugUnitTest --tests 'dev.opendevice.node.model.*'
```

Expected: resume, ignored Range, 416, storage guard, cancellation, exact-size validation, SHA mismatch, and atomic publication tests pass without Internet access.

- [ ] **Step 7: Commit the verified model store**

```bash
git add apps/android-node/app/src/main/java/dev/opendevice/node/model apps/android-node/app/src/test/java/dev/opendevice/node/model apps/android-node/app/src/main/AndroidManifest.xml apps/android-node/app/src/main/java/dev/opendevice/node/OpenDeviceNodeApp.kt
git commit -m "feat(android): add verified resumable model download"
```

### Task 6: API-28-Compatible Pinned llama.cpp Inference Engine

**Files:**
- Create: `.gitmodules`
- Create: `apps/android-node/inference/third_party/llama.cpp` as a Git submodule pinned to `3737e41370da1830a44c663f9929a0f27591ffa6`
- Create: `apps/android-node/inference/CMakeLists.txt`
- Create: `apps/android-node/inference/src/main/cpp/opendevice_llama_jni.cpp`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/inference/InferenceEngine.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/inference/LlamaCppInferenceEngine.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/inference/InferenceEngineContractTest.kt`
- Create: `apps/android-node/app/src/androidTest/java/dev/opendevice/node/inference/LlamaCppSmokeTest.kt`
- Create: `apps/android-node/inference/LICENSE.llama.cpp`
- Create: `apps/android-node/inference/SOURCE.md`
- Modify: `apps/android-node/app/build.gradle.kts`

**Interfaces:**
- Consumes: one verified GGUF `File` from Task 5.
- Produces: `InferenceEngine.load`, `generate`, `unload`, `state`, and JNI symbols scoped to `dev.opendevice.node.inference.LlamaCppInferenceEngine`.

- [ ] **Step 1: Write failing engine-contract tests with a deterministic fake**

```kotlin
data class ChatMessage(val role: String, val content: String)
data class GenerationOptions(
    val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
    val contextSize: Int = 2048,
    val threads: Int = 2,
)
data class GenerationChunk(
    val text: String,
    val tokenCount: Int,
    val promptTokenCount: Int? = null,
    val finished: Boolean,
)

sealed interface InferenceState {
    data object Unloaded : InferenceState
    data object Loading : InferenceState
    data class Ready(val modelPath: String) : InferenceState
    data object Generating : InferenceState
    data class Failed(val message: String) : InferenceState
}

interface InferenceEngine {
    val state: StateFlow<InferenceState>
    suspend fun load(model: File, contextSize: Int = 2048, threads: Int = 2)
    fun generate(messages: List<ChatMessage>, options: GenerationOptions): Flow<GenerationChunk>
    suspend fun unload()
}

@Test fun generationIsStatelessAcrossRequests() = runTest {
    val engine = FakeInferenceEngine()
    engine.load(fakeModel)
    engine.generate(listOf(ChatMessage("user", "first")), GenerationOptions()).toList()
    engine.generate(listOf(ChatMessage("user", "second")), GenerationOptions()).toList()
    assertEquals(listOf(listOf("first"), listOf("second")), engine.recordedPrompts)
}
```

Test option validation rejects context other than 2048 in release configuration, output above 512, threads outside 2..4, empty messages, and roles outside `system`, `user`, `assistant`.

- [ ] **Step 2: Add and verify the immutable upstream source**

```bash
git submodule add https://github.com/ggml-org/llama.cpp.git apps/android-node/inference/third_party/llama.cpp
git -C apps/android-node/inference/third_party/llama.cpp checkout 3737e41370da1830a44c663f9929a0f27591ffa6
git -C apps/android-node/inference/third_party/llama.cpp rev-parse HEAD
```

Expected final output: `3737e41370da1830a44c663f9929a0f27591ffa6`. Copy the upstream MIT license verbatim to `LICENSE.llama.cpp`. Record repository URL, commit, Android API 28 fork boundary, NDK, CMake, and native build flags in `SOURCE.md`.

- [ ] **Step 3: Build a narrow native library for API 28 arm64**

```cmake
cmake_minimum_required(VERSION 3.31.6)
project(opendevice_llama LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
add_subdirectory(third_party/llama.cpp EXCLUDE_FROM_ALL)
add_library(opendevice_llama SHARED src/main/cpp/opendevice_llama_jni.cpp)
target_link_libraries(opendevice_llama PRIVATE llama android log)
```

Configure the Android module with NDK `29.0.13113456`, CMake `3.31.6`, `-DANDROID_PLATFORM=android-28`, and only `arm64-v8a`. Do not include or depend on the upstream Android example module because its minSdk is 33.

- [ ] **Step 4: Implement explicit native ownership and cancellation**

The JNI wrapper owns one `llama_model*`, one `llama_context*`, and one `llama_sampler*` behind a native handle. `nativeLoad` returns zero on failure and sends an error string back through a Kotlin exception. `nativeGenerate` accepts the fully rendered prompt, max tokens, temperature, and a callback object with `onToken(String, Int)`; it checks an atomic cancellation flag between decoded tokens and returns the exact prompt-token count or a negative internal error code. `nativeClose` frees sampler, context, model, and backend in that order and is idempotent.

Kotlin exports these exact calls:

```kotlin
private fun interface TokenCallback { fun onToken(text: String, tokenCount: Int) }
private external fun nativeLoad(path: String, contextSize: Int, threads: Int): Long
private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, temperature: Float, callback: TokenCallback): Int
private external fun nativeCancel(handle: Long)
private external fun nativeClose(handle: Long)
```

Render a complete chat prompt from only the current request messages using the model chat template API. Emit UTF-8 only after complete code points, set `tokenCount` to the number of newly decoded model tokens in that text chunk, stop at EOS or max tokens, and send one final empty `GenerationChunk(tokenCount = 0, promptTokenCount = exactPromptTokens, finished = true)`. `unload()` calls cancel, waits for the generation dispatcher, closes the handle, and clears it.

- [ ] **Step 5: Make the device smoke test use a pushed tiny fixture**

`LlamaCppSmokeTest` reads instrumentation argument `modelPath`, loads that file, asks for two tokens from `user: hi`, asserts at least one non-empty chunk, then unloads. The test skips with an explicit assumption failure when the argument is absent; the final real-device task supplies the verified Qwen model path, so release verification cannot pass on a skip.

- [ ] **Step 6: Run host contract tests and native configuration checks**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 apps/android-node/gradlew -p apps/android-node :app:testDebugUnitTest --tests 'dev.opendevice.node.inference.*' :app:assembleDebug
git -C apps/android-node/inference/third_party/llama.cpp rev-parse HEAD
unzip -l apps/android-node/app/build/outputs/apk/debug/app-debug.apk | rg 'lib/arm64-v8a/libopendevice_llama.so|lib/(x86|armeabi)'
```

Expected: contract tests pass, the exact upstream commit prints, `libopendevice_llama.so` exists under `arm64-v8a`, and no second ABI is present.

- [ ] **Step 7: Commit the inference engine**

```bash
git add .gitmodules apps/android-node/inference apps/android-node/app/build.gradle.kts apps/android-node/app/src/main/java/dev/opendevice/node/inference apps/android-node/app/src/test/java/dev/opendevice/node/inference apps/android-node/app/src/androidTest/java/dev/opendevice/node/inference
git commit -m "feat(android): add pinned local llama inference"
```

### Task 7: Single-Flight AI Controller, Resource Guard, and Foreground Service

**Files:**
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ai/AiNodeState.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ai/NodeMetrics.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ai/ResourceGuard.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ai/AiNodeController.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ai/DefaultAiNodeController.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ai/AiNodeService.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ai/AiNodeNotifications.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/ai/ResourceGuardTest.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/ai/AiNodeControllerTest.kt`
- Create: `apps/android-node/app/src/androidTest/java/dev/opendevice/node/ai/ResourcePolicyInstrumentedTest.kt`
- Modify: `apps/android-node/app/src/main/AndroidManifest.xml`
- Modify: `apps/android-node/app/src/main/java/dev/opendevice/node/OpenDeviceNodeApp.kt`

**Interfaces:**
- Consumes: `ModuleRegistry`, `ModuleStartupGuard`, `DeviceFactsSource`, `ModelDownloadRepository`, and `InferenceEngine`.
- Produces: `AiNodeController.state: StateFlow<AiNodeState>`, `AiNodeController.metrics: StateFlow<NodeMetrics>`, `start()`, `stop()`, `chat(requestId, messages, options)`, and `AiNodeService` actions `START` and `STOP`.

- [ ] **Step 1: Write failing resource-threshold tests**

```kotlin
@Test fun memoryRequiresModelPlus512MiB() {
    val model = 639_446_688L
    assertEquals(ResourceDecision.Allow, guard.evaluate(facts(available = model + 512.mebibytes)))
    assertIs<ResourceDecision.BlockMemory>(guard.evaluate(facts(available = model + 512.mebibytes - 1)))
}

@Test fun heatPausesAt45AndResumesOnlyAt40() {
    assertIs<ResourceDecision.PauseHeat>(guard.evaluate(facts(tempC = 45f)))
    guard.markPausedForHeat()
    assertIs<ResourceDecision.StayPaused>(guard.evaluate(facts(tempC = 40.1f)))
    assertEquals(ResourceDecision.Allow, guard.evaluate(facts(tempC = 40f, thermal = ThermalLevel.MODERATE)))
}

@Test fun severeThermalStatusAlwaysPauses() {
    assertIs<ResourceDecision.PauseHeat>(guard.evaluate(facts(tempC = 35f, thermal = ThermalLevel.SEVERE)))
}
```

The test fixture defines `val Int.mebibytes get() = toLong() * 1024L * 1024L`. Implement these exact guard results:

```kotlin
sealed interface ResourceDecision {
    data object Allow : ResourceDecision
    data class BlockMemory(val requiredBytes: Long, val availableBytes: Long?) : ResourceDecision
    data class PauseHeat(val temperatureC: Float?, val thermal: ThermalLevel) : ResourceDecision
    data class StayPaused(val temperatureC: Float?, val thermal: ThermalLevel) : ResourceDecision
}
```

- [ ] **Step 2: Write failing controller lifecycle and concurrency tests**

```kotlin
@Test fun startRequiresEnabledModuleAndVerifiedModel() = runTest {
    assertEquals(StartResult.ModuleDisabled, controller.start())
    registry.enable(AI_MODULE_ID)
    assertEquals(StartResult.ModelMissing, controller.start())
}

@Test fun secondGenerationIsRejectedWithoutQueueing() = runTest {
    startReadyController()
    val first = backgroundScope.async { controller.chat("a", messages, options).toList() }
    engine.awaitGenerationStarted()
    assertFailsWith<NodeBusyException> { controller.chat("b", messages, options).toList() }
    engine.finishGeneration()
    first.await()
}

@Test fun explicitStopDoesNotScheduleRestart() = runTest {
    startReadyController()
    controller.stop(StopReason.User)
    assertFalse(fakeScheduler.hasPendingRestart)
    assertIs<AiNodeState.Stopped>(controller.state.value)
}
```

- [ ] **Step 3: Define the state machine and guard result types**

```kotlin
sealed interface AiNodeState {
    data object Stopped : AiNodeState
    data object Starting : AiNodeState
    data class Serving(val bindAddress: String, val port: Int, val modelId: String) : AiNodeState
    data class Busy(val requestId: String) : AiNodeState
    data class PausedHeat(val temperatureC: Float?, val thermal: ThermalLevel) : AiNodeState
    data class BlockedMemory(val requiredBytes: Long, val availableBytes: Long?) : AiNodeState
    data class Failed(val message: String) : AiNodeState
}

interface AiNodeController {
    val state: StateFlow<AiNodeState>
    val metrics: StateFlow<NodeMetrics>
    suspend fun start(): StartResult
    suspend fun stop(reason: StopReason = StopReason.User)
    fun chat(requestId: String, messages: List<ChatMessage>, options: GenerationOptions): Flow<GenerationChunk>
}

data class NodeMetrics(
    val modelLoadMillis: Long? = null,
    val firstTokenMillis: Long? = null,
    val outputTokensPerSecond: Double? = null,
    val peakRssBytes: Long? = null,
    val batteryPercent: Int? = null,
    val batteryTemperatureC: Float? = null,
    val thermal: ThermalLevel = ThermalLevel.UNKNOWN,
)

sealed interface StartResult {
    data object Started : StartResult
    data object AlreadyRunning : StartResult
    data object ModuleDisabled : StartResult
    data object ModelMissing : StartResult
    data class BlockedMemory(val requiredBytes: Long, val availableBytes: Long?) : StartResult
    data class BlockedHeat(val temperatureC: Float?, val thermal: ThermalLevel) : StartResult
    data class Failed(val message: String) : StartResult
}

enum class StopReason { User, ThermalPolicy, MemoryPressure, ModuleDisabled, ServiceDestroyed }
class NodeBusyException : IllegalStateException("node_busy")
```

`ResourceDecision.Allow` is inclusive at model size plus 512 MiB, 40 C, and moderate. `PauseHeat` begins at severe or 45 C. `StayPaused` covers the hysteresis band. Unknown temperature does not block when thermal is below severe; both readings are shown in Status.

- [ ] **Step 4: Implement lifecycle serialization and single-flight generation**

Use one lifecycle `Mutex` for load/unload and one generation `Mutex` acquired with `tryLock`. `start()` checks module enabled, verified file, and resource decision, calls `ModuleStartupGuard.markStarting`, then loads once. After 60 continuous seconds in Serving, call `markStable`. Every controlled stop calls `markCleanStop`. `chat()` accepts only Serving, atomically changes to Busy, delegates to the engine, and restores Serving in `finally`. If generation cannot acquire the mutex immediately, throw `NodeBusyException` without adding a queue.

Measure load and generation with `SystemClock.elapsedRealtimeNanos()`. Set first-token latency once on the first non-empty chunk, derive tokens/s from emitted token counts and elapsed generation time, and sample RSS with `ActivityManager.getProcessMemoryInfo(intArrayOf(Process.myPid())).first().totalPss * 1024L`; preserve the maximum for the active service session. Copy battery and thermal measurements from `DeviceFactsSource`. Tests inject monotonic time and RSS samplers and assert exact arithmetic; metrics never contain prompt or response text.

Collect `DeviceFactsSource.observe()` while loaded. On `PauseHeat`, cancel the current generation, unload, and enter `PausedHeat`. Resume only after a later `Allow`, and only if the module remains enabled and the previous stop reason was thermal policy. A user stop sets `explicitlyStopped = true` before cancellation, preventing the collector from reloading.

- [ ] **Step 5: Add the visible foreground-service boundary**

`AiNodeService` accepts only explicit intents:

```kotlin
companion object {
    const val ACTION_START = "dev.opendevice.node.action.START_AI_NODE"
    const val ACTION_STOP = "dev.opendevice.node.action.STOP_AI_NODE"
    const val NOTIFICATION_ID = 1001
}
```

For `ACTION_START`, call `startForeground` immediately with channel `ai_node_service`, then call `controller.start()`. For `ACTION_STOP`, call `controller.stop(User)`, `stopForeground(STOP_FOREGROUND_REMOVE)`, and `stopSelf()`. `onTaskRemoved` does not restart. `onStartCommand` returns `START_NOT_STICKY`. The notification shows current model, localhost/LAN mode, and a Stop action.

On API 34+, pass `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` to the three-argument `startForeground`; on older releases use the two-argument call. Catch `ForegroundServiceStartNotAllowedException` on API 31+ and surface `系统不允许从后台启动，请回到应用后重试` without retrying from the background.

- [ ] **Step 6: Run controller and service tests**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 apps/android-node/gradlew -p apps/android-node :app:testDebugUnitTest --tests 'dev.opendevice.node.ai.*' :app:assembleDebug
```

Expected: all threshold boundaries, single-flight rejection, thermal cancellation/resume hysteresis, disabled-module start, missing-model start, and no-restart-on-user-stop tests pass.

- [ ] **Step 7: Commit the controlled service lifecycle**

```bash
git add apps/android-node/app/src/main/java/dev/opendevice/node/ai apps/android-node/app/src/test/java/dev/opendevice/node/ai apps/android-node/app/src/androidTest/java/dev/opendevice/node/ai apps/android-node/app/src/main/AndroidManifest.xml apps/android-node/app/src/main/java/dev/opendevice/node/OpenDeviceNodeApp.kt
git commit -m "feat(android): control AI node lifecycle and resources"
```

### Task 8: Bounded Authenticated OpenAI-Compatible API

**Files:**
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/api/ApiKeyStore.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/api/KeystoreApiKeyStore.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/api/HttpLimits.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/api/HttpRequest.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/api/BoundedHttpParser.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/api/OpenAiDtos.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/api/OpenAiRouter.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/api/ApiAuditStore.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/api/SseWriter.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/api/SocketHttpServer.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/api/DefaultSocketHttpServer.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/api/ApiKeyStoreTest.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/api/BoundedHttpParserTest.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/api/OpenAiRouterTest.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/api/SocketHttpServerTest.kt`
- Create: `apps/android-node/app/src/androidTest/java/dev/opendevice/node/api/KeystoreApiKeyStoreTest.kt`
- Modify: `apps/android-node/app/src/main/java/dev/opendevice/node/ai/DefaultAiNodeController.kt`

**Interfaces:**
- Consumes: `AiNodeController.chat`, `AiNodeController.state`, and the selected model descriptor.
- Produces: `ApiKeyStore`, `OpenAiRouter.route(request, peer)`, `SocketHttpServer.start(config)`, `SocketHttpServer.stop()`, and OpenAI-compatible JSON/SSE responses.

- [ ] **Step 1: Write failing parser-boundary tests**

```kotlin
@Test fun requestLineAboveTwoKiBIsRejected() {
    val raw = "GET /${"a".repeat(2048)} HTTP/1.1\r\n\r\n".byteInputStream()
    assertEquals(HttpParseError.REQUEST_LINE_TOO_LARGE, assertIs<ParseResult.Error>(parser.parse(raw)).reason)
}

@Test fun sixtyFifthHeaderIsRejected() {
    val headers = (1..65).joinToString("") { "X-$it: v\r\n" }
    val raw = "GET /health HTTP/1.1\r\n${headers}\r\n".byteInputStream()
    assertEquals(HttpParseError.TOO_MANY_HEADERS, assertIs<ParseResult.Error>(parser.parse(raw)).reason)
}

@Test fun bodyAboveOneMiBIsRejectedBeforeAllocation() {
    val raw = "POST /v1/chat/completions HTTP/1.1\r\nContent-Length: 1048577\r\n\r\n".byteInputStream()
    assertEquals(HttpParseError.BODY_TOO_LARGE, assertIs<ParseResult.Error>(parser.parse(raw)).reason)
}
```

Define these parser types; do not add a general-purpose parser dependency:

```kotlin
data class HttpRequest(
    val method: String,
    val path: String,
    val version: String,
    val headers: Map<String, String>,
    val body: ByteArray,
)

enum class HttpParseError {
    REQUEST_LINE_TOO_LARGE,
    HEADERS_TOO_LARGE,
    TOO_MANY_HEADERS,
    BODY_TOO_LARGE,
    MALFORMED_REQUEST,
    UNSUPPORTED_TRANSFER_ENCODING,
    READ_TIMEOUT,
}

sealed interface ParseResult {
    data class Success(val request: HttpRequest) : ParseResult
    data class Error(val reason: HttpParseError) : ParseResult
}
```

Also cover total headers at 16,384 bytes, malformed content length, duplicate content length, transfer encoding, unsupported method, and a 15-second socket timeout mapping to HTTP 408. Chunked request bodies are rejected with HTTP 400 in V1.

- [ ] **Step 2: Write failing authentication and endpoint tests**

```kotlin
@Test fun loopbackHealthMayBeReadWithoutToken() = runTest {
    val response = router.route(get("/health"), Peer.LOOPBACK)
    assertEquals(200, response.status)
    val json = Json.parseToJsonElement(response.bytesBody().decodeToString()).jsonObject
    assertEquals(setOf("status", "model_ready"), json.keys)
}

@Test fun lanHealthRequiresBearerToken() = runTest {
    assertEquals(401, router.route(get("/health"), Peer.LAN).status)
}

@Test fun secondChatReturns429() = runTest {
    controller.rejectBusy = true
    val response = router.route(authenticatedChat(stream = false), Peer.LOOPBACK)
    assertEquals(429, response.status)
    assertEquals("node_busy", response.errorCode)
}

@Test fun streamEndsWithDoneMarker() = runTest {
    val bytes = router.route(authenticatedChat(stream = true), Peer.LOOPBACK).bytesBody()
    assertTrue(bytes.decodeToString().endsWith("data: [DONE]\n\n"))
}
```

Test 401 for missing/wrong/revoked keys, 404 for all other paths, 405 for wrong methods, 409 `model_not_ready`, 400 for invalid roles/options, 504 `generation_timeout`, request-ID propagation, client disconnect cancellation, and absence of prompt/token values in captured logs.

- [ ] **Step 3: Store per-client verifier material behind Android Keystore**

```kotlin
data class CreatedClient(
    val id: String,
    val label: String,
    val rawToken: String,
    val fingerprint: String,
)

data class ApiClient(
    val id: String,
    val label: String,
    val fingerprint: String,
    val createdAtMillis: Long,
    val revokedAtMillis: Long? = null,
)

interface ApiKeyStore {
    val clients: StateFlow<List<ApiClient>>
    suspend fun create(label: String): CreatedClient
    suspend fun verify(rawToken: String): ApiClient?
    suspend fun revoke(id: String): Boolean
    suspend fun revokeAll()
}
```

On first use, create a non-exportable Android Keystore HMAC-SHA256 key named `opendevice_api_token_verifier_v1`. `create` uses `SecureRandom` for exactly 32 bytes and Base64 URL encoding without padding. Persist only client ID, label, creation time, SHA-256 fingerprint prefix, and HMAC(token) in a DataStore file rooted at `noBackupFilesDir`; return the raw token once from `create`. `verify` recomputes HMAC and compares with `MessageDigest.isEqual`. `revokeAll` clears records and deletes the Keystore alias. Unit tests inject a deterministic `TokenMac` interface; an instrumented test uses the real Android Keystore.

- [ ] **Step 4: Implement the strict request and response DTOs**

```kotlin
@Serializable data class ChatCompletionRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = false,
    @SerialName("max_tokens") val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
)

@Serializable data class OpenAiMessage(val role: String, val content: String)

@Serializable data class OpenAiErrorEnvelope(val error: OpenAiError)
@Serializable data class OpenAiError(
    val message: String,
    val type: String,
    val code: String,
    val param: String? = null,
)

@Serializable data class ChatCompletionResponse(
    val id: String,
    val object: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<ChatChoice>,
    val usage: TokenUsage,
)

@Serializable data class ChatChoice(
    val index: Int = 0,
    val message: OpenAiMessage,
    @SerialName("finish_reason") val finishReason: String,
)

@Serializable data class TokenUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int,
)

@Serializable data class ModelListResponse(
    val object: String = "list",
    val data: List<ModelObject>,
)

@Serializable data class ModelObject(
    val id: String,
    val object: String = "model",
    val created: Long,
    @SerialName("owned_by") val ownedBy: String = "opendevice-node",
)

@Serializable data class ChatCompletionChunk(
    val id: String,
    val object: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<ChunkChoice>,
)

@Serializable data class ChunkChoice(
    val index: Int = 0,
    val delta: ChatDelta,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable data class ChatDelta(
    val role: String? = null,
    val content: String? = null,
)

sealed interface HttpBody {
    data class Bytes(val value: ByteArray) : HttpBody
    data class Events(val frames: Flow<ByteArray>) : HttpBody
}

data class HttpResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: HttpBody,
    val errorCode: String? = null,
)
```

The API tests define `bytesBody()` to return `HttpBody.Bytes.value` and collect `HttpBody.Events.frames`; production routing never buffers an SSE response.

Configure `Json { ignoreUnknownKeys = false; explicitNulls = false }`. Accept only the loaded model ID, 1..512 `max_tokens`, finite temperature 0.0..2.0, 1..64 messages, each content 1..65,536 characters, and roles `system`, `user`, `assistant`. Reject a decoded request that would exceed the 1 MiB body bound even if the socket parser is bypassed in a unit test.

- [ ] **Step 5: Implement a deliberately small closing HTTP/1.1 server**

```kotlin
data class ServerConfig(
    val address: InetAddress,
    val port: Int,
    val mode: NetworkMode,
)

enum class NetworkMode { LOOPBACK, LAN }
enum class Peer { LOOPBACK, LAN }
data class ServerEndpoint(val address: String, val port: Int, val mode: NetworkMode)

interface SocketHttpServer {
    val endpoint: StateFlow<ServerEndpoint?>
    suspend fun start(config: ServerConfig): ServerEndpoint
    suspend fun stop()
}
```

Use `ServerSocket` with backlog 8 and `reuseAddress = true`; set each accepted socket `soTimeout = 15_000`. A bounded coroutine dispatcher handles at most four open sockets, while the AI controller still allows only one generation. Every response includes `Connection: close`, `Content-Type`, `X-Request-Id`, and exact `Content-Length` except SSE, which writes HTTP headers once, flushes each `data: <json>\n\n` frame, then `[DONE]` and closes. Stop closes the server socket, all active clients, and the accept job.

`GET /health` returns only `{"status":"ok|starting|paused|error","model_ready":true|false}`. `GET /v1/models` returns one OpenAI list item only when the model is loaded, uses the descriptor ID, `owned_by: "opendevice-node"`, and `created: 0` rather than inventing a publication timestamp. Neither endpoint exposes filesystem paths, IP history, tokens, prompts, or device identifiers.

- [ ] **Step 6: Map chat requests to the one controller path**

Generate request IDs as `chatcmpl-` plus random lowercase hex from 16 bytes, and set `created` to Unix seconds from the request-start clock. Wrap controller collection in `withTimeout(120_000L)`. Non-streaming collects chunks and returns a standard `chat.completion` with exact prompt/output/total token usage. Streaming returns `chat.completion.chunk`, emits the assistant role in the first delta, text deltas thereafter, and `finish_reason: "stop"` in the last JSON frame before `[DONE]`. A timeout before headers returns HTTP 504 `generation_timeout`; after SSE headers it cancels generation and closes the connection. A socket write failure cancels collection, which reaches `InferenceEngine.nativeCancel` through the controller.

The server logger accepts only this record and exposes `ApiAuditStore.records: StateFlow<List<ApiAuditRecord>>`, retaining the newest 100 entries in memory and persisting none:

```kotlin
data class ApiAuditRecord(
    val requestId: String,
    val startedAtMillis: Long,
    val modelId: String?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val durationMillis: Long,
    val statusCode: Int,
    val peakRssBytes: Long?,
    val batteryTemperatureC: Float?,
)

interface ApiAuditStore {
    val records: StateFlow<List<ApiAuditRecord>>
    fun append(record: ApiAuditRecord)
    fun clear()
}
```

- [ ] **Step 7: Run the complete API suite**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 apps/android-node/gradlew -p apps/android-node :app:testDebugUnitTest --tests 'dev.opendevice.node.api.*'
```

Expected: parser bounds, key creation/revocation, loopback/LAN policy, DTO validation, route errors, non-stream response, SSE flush/order/termination, cancellation, and secret-free logging tests all pass.

- [ ] **Step 8: Commit the local API**

```bash
git add apps/android-node/app/src/main/java/dev/opendevice/node/api apps/android-node/app/src/test/java/dev/opendevice/node/api apps/android-node/app/src/androidTest/java/dev/opendevice/node/api apps/android-node/app/src/main/java/dev/opendevice/node/ai/DefaultAiNodeController.kt
git commit -m "feat(android): expose bounded OpenAI local API"
```

### Task 9: Complete Phone Control and Local Chat Flow

**Files:**
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/settings/NodeSettings.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/settings/NodeSettingsRepository.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/settings/DataStoreNodeSettingsRepository.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/NodeViewModel.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/NodeUiState.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/LocalChat.kt`
- Create: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/ConnectionConfirmation.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/settings/NodeSettingsRepositoryTest.kt`
- Create: `apps/android-node/app/src/test/java/dev/opendevice/node/ui/NodeViewModelTest.kt`
- Create: `apps/android-node/app/src/androidTest/java/dev/opendevice/node/ui/NodeFlowTest.kt`
- Modify: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/NodeApp.kt`
- Modify: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/NodeScreen.kt`
- Modify: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/ModulesScreen.kt`
- Modify: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/ConnectionsScreen.kt`
- Modify: `apps/android-node/app/src/main/java/dev/opendevice/node/ui/StatusScreen.kt`
- Modify: `apps/android-node/app/src/main/java/dev/opendevice/node/OpenDeviceNodeApp.kt`

**Interfaces:**
- Consumes: registry, model repository, `AiNodeController.state`, `AiNodeController.metrics`, API key store, `ApiAuditStore.records`, socket server, and device facts.
- Produces: the complete phone-side flow for enable, download, start, chat, endpoint configuration, LAN confirmation, client-key creation/revocation, stop, and diagnosis.

- [ ] **Step 1: Write failing setting-boundary tests**

```kotlin
@Test fun portMustBe1024Through65535() = runTest {
    assertIs<SettingResult.Rejected>(repo.setPort(1023, serviceRunning = false))
    assertIs<SettingResult.Changed>(repo.setPort(1024, serviceRunning = false))
    assertIs<SettingResult.Changed>(repo.setPort(65535, serviceRunning = false))
}

@Test fun endpointCannotChangeWhileServing() = runTest {
    assertEquals(SettingResult.Rejected("请先停止节点"), repo.setPort(9090, serviceRunning = true))
    assertEquals(SettingResult.Rejected("请先停止节点"), repo.setLanEnabled(true, serviceRunning = true, confirmed = true))
}

@Test fun lanRequiresFreshPhoneConfirmation() = runTest {
    assertEquals(SettingResult.Rejected("需要在手机上确认"), repo.setLanEnabled(true, false, confirmed = false))
}
```

`NodeSettings` defaults to port 8080, LAN false, context 2048, max output 256, and threads 2. `setMaxOutputTokens` accepts 1..512 and `setThreads` accepts 2..4 only while the service is stopped. Context remains the manifest-fixed value 2048 in this release. Do not persist a prior confirmation as permission to re-enable LAN after the user turns it off.

- [ ] **Step 2: Write failing view-model workflow tests**

```kotlin
@Test fun startActionExplainsEachPrerequisite() = runTest {
    viewModel.startNode()
    assertEquals("请先启用本地 AI 节点模块", viewModel.state.value.blockingMessage)
    registry.enable(AI_MODULE_ID)
    viewModel.startNode()
    assertEquals("请先下载并校验模型", viewModel.state.value.blockingMessage)
}

@Test fun localChatUsesTheSameController() = runTest {
    viewModel.sendLocalMessage("你好")
    assertEquals(listOf(ChatMessage("user", "你好")), fakeController.lastMessages)
    assertEquals("手机回复", viewModel.state.value.chat.messages.last().content)
}

@Test fun createdTokenIsVisibleOnlyUntilDismissed() = runTest {
    viewModel.createClient("我的电脑")
    assertNotNull(viewModel.state.value.oneTimeToken)
    viewModel.dismissOneTimeToken()
    assertNull(viewModel.state.value.oneTimeToken)
    assertNull(fakeKeyStore.persistedRawToken)
}
```

- [ ] **Step 3: Implement one state owner and explicit phone actions**

`NodeViewModel` combines all `StateFlow`s into `NodeUiState`. It exposes exactly these user actions: `enableAiModule`, `disableAiModule`, `downloadModel`, `cancelDownload`, `startNode`, `stopNode`, `sendLocalMessage`, `cancelLocalMessage`, `setPort`, `setMaxOutputTokens`, `setThreads`, `requestLanEnable`, `confirmLanEnable`, `disableLan`, `createClient`, `revokeClient`, and `exitSafeMode`.

`sendLocalMessage` calls `AiNodeController.chat` directly with request ID prefix `local-`; it does not call the network server and does not create a second engine or queue. The same controller therefore enforces 429-equivalent busy behavior between phone chat and remote chat.

On API 33+, `startNode` first requests `POST_NOTIFICATIONS` from the visible activity. If denied, show `需要通知权限才能持续显示节点状态` and keep the service stopped. Never open settings automatically and never bypass the visible-user-start boundary.

- [ ] **Step 4: Complete the four screens with real states**

- Node: prerequisite checklist, verified model name, Download/Cancel, Start/Stop, streaming local chat, Cancel generation, and current blocking reason.
- Modules: built-in AI module manifest, enabled state, exact permissions, protected marker, version, safe-mode warning, and no marketplace/search button that implies a working online store.
- Connections: current bind address and port, USB forwarding command, LAN off/on state, full-screen confirmation stating `局域网模式使用 HTTP，同一网络中的攻击者可能窃听；只在可信 Wi-Fi 临时开启`, per-client list, Create key, one-time copy surface, fingerprint, and Revoke.
- Status: live RAM/storage/battery/thermal, model state, service state, load duration, first-token latency, tokens/s, peak RSS, recent request IDs/status codes, and explicit rows showing Public gateway, Root Broker, Marketplace, Creator, and Script sandbox as `尚未实现`.

Use selectable monospace text for endpoint and USB command. Never render a raw token after the one-time dialog is dismissed. Never display guessed speed or capacity.

- [ ] **Step 5: Detect port conflicts before starting**

In localhost mode, select only `InetAddress.getByName("127.0.0.1")`. In LAN mode, read the active network from `ConnectivityManager`, inspect its `LinkProperties.linkAddresses`, and select the first site-local IPv4 address that is neither loopback nor link-local. Reject LAN start with `当前网络没有可用的局域网 IPv4 地址` if none exists; never bind `0.0.0.0` and never treat a cellular or VPN route as a confirmed Wi-Fi address.

Before starting the foreground service, bind a temporary `ServerSocket` to the selected address and port, close it, then let the real server bind. If either bind fails, keep the service stopped and show `端口 <port> 已被占用`. A race between probe and actual bind is mapped to the same message; the probe is advisory, not assumed ownership.

- [ ] **Step 6: Add a phone-flow instrumentation test with fakes**

The test launches `MainActivity` with fake dependencies, verifies all four tabs, enables the module, moves model state from Missing to Downloading to Ready, starts the node, sends `你好`, observes streamed `手机回复`, opens LAN confirmation and cancels it, creates a client key, dismisses it, and stops the node. It asserts the Modules screen contains `尚未提供在线模块市场` and Status contains `尚未实现` for public gateway and Root Broker.

- [ ] **Step 7: Run phone unit, UI, and build checks**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 apps/android-node/gradlew -p apps/android-node :app:testDebugUnitTest --tests 'dev.opendevice.node.settings.*' --tests 'dev.opendevice.node.ui.*' :app:assembleDebug
```

When an API-28 or later emulator is available:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 apps/android-node/gradlew -p apps/android-node :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.opendevice.node.ui.NodeFlowTest
```

Expected: unit tests and APK build pass; the connected test passes rather than being reported as skipped.

- [ ] **Step 8: Commit the complete phone flow**

```bash
git add apps/android-node/app/src/main/java/dev/opendevice/node/settings apps/android-node/app/src/main/java/dev/opendevice/node/ui apps/android-node/app/src/test/java/dev/opendevice/node/settings apps/android-node/app/src/test/java/dev/opendevice/node/ui apps/android-node/app/src/androidTest/java/dev/opendevice/node/ui apps/android-node/app/src/main/java/dev/opendevice/node/OpenDeviceNodeApp.kt
git commit -m "feat(android): complete phone AI node controls"
```

### Task 10: Real CDL-AN50 and Standard-Client Verification

**Files:**
- Create: `scripts/verify-openai-client.mjs`
- Create: `scripts/soak-openai-client.mjs`
- Create: `scripts/collect-android-node-evidence.mjs`
- Create: `scripts/verify-android-node-logs.mjs`
- Create: `scripts/android-node-verification.test.mjs`
- Create: `docs/verification/android-node-cdl-an50.md`
- Create: `docs/verification/artifacts/.gitkeep`
- Modify: `package.json`
- Modify: `pnpm-lock.yaml`

**Interfaces:**
- Consumes: debug APK, a physically connected CDL-AN50, phone-side user confirmation, a one-time API token, ADB forwarding, and standard OpenAI JavaScript SDK 7.5.0.
- Produces: repeatable verification scripts, redacted evidence, APK SHA-256, measured performance/thermal record, and an honest pass/blocked result for every acceptance layer.

- [ ] **Step 1: Write failing verification-tool tests**

```js
import assert from "node:assert/strict";
import test from "node:test";
import { verifyClient } from "./verify-openai-client.mjs";
import { redactEvidence } from "./collect-android-node-evidence.mjs";
import { findSecretLeaks } from "./verify-android-node-logs.mjs";

const fakeOpenAiClient = (calls) => ({
  models: {
    list: async () => {
      calls.push("models.list");
      return { data: [{ id: "qwen3-0.6b-q8_0" }] };
    },
  },
  chat: {
    completions: {
      create: async (request) => {
        if (!request.stream) {
          calls.push("chat.plain");
          return { choices: [{ message: { content: "手机节点正常" } }] };
        }
        calls.push("chat.stream");
        return (async function* stream() {
          yield { choices: [{ delta: { content: "一二" } }] };
          yield { choices: [{ delta: { content: "三" } }] };
        })();
      },
    },
  },
});

test("standard verifier exercises models, plain chat, and stream", async () => {
  const calls = [];
  const client = fakeOpenAiClient(calls);
  const result = await verifyClient(client);
  assert.equal(result.ok, true);
  assert.deepEqual(calls, ["models.list", "chat.plain", "chat.stream"]);
});

test("evidence redaction removes token and full adb serial", () => {
  const redacted = redactEvidence("Authorization: Bearer raw-secret SERIAL-123", ["raw-secret", "SERIAL-123"]);
  assert.equal(redacted.includes("raw-secret"), false);
  assert.equal(redacted.includes("SERIAL-123"), false);
});

test("log scanner identifies prompt, token, and serial leaks", () => {
  assert.deepEqual(
    findSecretLeaks("你好 raw-secret SERIAL-123", ["你好", "raw-secret", "SERIAL-123"]),
    ["你好", "raw-secret", "SERIAL-123"],
  );
});
```

Run it before creating the imported scripts:

```bash
node --test scripts/android-node-verification.test.mjs
```

Expected: failure with `ERR_MODULE_NOT_FOUND` for `verify-openai-client.mjs`.

- [ ] **Step 2: Write the standard OpenAI client verifier**

Add `openai@7.5.0` as a root development dependency. The script requires `OPENDEVICE_BASE_URL` and `OPENDEVICE_API_KEY`, refuses a base URL not ending in `/v1`, and never prints the key.

```js
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
  if (!plain.choices[0]?.message.content) throw new Error("empty non-stream response");

  const stream = await client.chat.completions.create({
    model,
    messages: [{ role: "user", content: "从一数到三" }],
    max_tokens: 32,
    stream: true,
  });
  let streamed = "";
  for await (const chunk of stream) streamed += chunk.choices[0]?.delta.content ?? "";
  if (!streamed) throw new Error("empty stream response");
  return { ok: true, model, nonStreamChars: plain.choices[0].message.content.length, streamChars: streamed.length };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const baseURL = process.env.OPENDEVICE_BASE_URL;
  const apiKey = process.env.OPENDEVICE_API_KEY;
  if (!baseURL?.endsWith("/v1") || !apiKey) throw new Error("set OPENDEVICE_BASE_URL ending in /v1 and OPENDEVICE_API_KEY");
  const result = await verifyClient(new OpenAI({ baseURL, apiKey, timeout: 120_000, maxRetries: 0 }));
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
```

- [ ] **Step 3: Implement redacted device evidence collection**

`collect-android-node-evidence.mjs` invokes `adb` with an optional `ANDROID_SERIAL`, hashes any serial before storage, and writes one timestamped JSON file under `docs/verification/artifacts/`. It collects `ro.product.model`, `ro.build.version.release`, `ro.build.version.sdk`, `ro.product.cpu.abilist`, `/proc/meminfo`, `df -k /data`, `dumpsys battery`, the app version, foreground-service state, process RSS, and APK SHA-256. It rejects a device model other than `CDL-AN50` unless the operator passes `--allow-different-device`, in which case the report cannot satisfy the CDL-AN50 gate.

The script must redact all values matching `Authorization: Bearer`, `sk-`, the active raw token, and the full ADB serial before writing. `verify-android-node-logs.mjs` scans captured logcat text and fails on the exact test prompts, generated response text, the raw token, or the unhashed serial.

Use literal replacement for caller-provided secrets so metacharacters cannot alter the scan:

```js
export function redactEvidence(value, secrets) {
  let redacted = String(value)
    .replace(/Authorization:\s*Bearer\s+[^\s"']+/gi, "Authorization: Bearer [REDACTED]")
    .replace(/\bsk-[A-Za-z0-9_-]+\b/g, "[REDACTED]");
  for (const secret of secrets.filter(Boolean)) redacted = redacted.split(secret).join("[REDACTED]");
  return redacted;
}

export function findSecretLeaks(text, secrets) {
  return secrets.filter(Boolean).filter((secret) => text.includes(secret));
}
```

The collector applies `redactEvidence` to the serialized object before the only file write. The log verifier exits non-zero when `findSecretLeaks` returns any element and prints only leak categories (`prompt`, `response`, `api_key`, `adb_serial`), never the matched secret.

`soak-openai-client.mjs` uses the same two required environment variables as the standard verifier, lists the phone model once, then sends one 256-token streaming request at a time until `Date.now()` reaches `startedAt + 1_800_000`. It waits 10 seconds after each completed stream, cancels and exits non-zero on any SDK error, and prints only request ordinal, elapsed milliseconds, output token count, and HTTP-safe error codes.

Update the root package scripts and development dependencies without changing the other scripts:

```json
{
  "scripts": {
    "test": "pnpm -r test && pnpm test:verification",
    "test:verification": "node --test scripts/android-node-verification.test.mjs"
  },
  "devDependencies": {
    "openai": "7.5.0"
  }
}
```

Run `pnpm install`, then `pnpm test:verification`. Expected: all three verification-tool tests pass.

- [ ] **Step 4: Run all source and build gates from a clean checkout state**

```bash
pnpm install --frozen-lockfile
pnpm check
pnpm --filter @opendevice/module-contract generate -- --check
JAVA_HOME=/opt/homebrew/opt/openjdk@17 apps/android-node/gradlew -p apps/android-node clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
git diff --check
shasum -a 256 apps/android-node/app/build/outputs/apk/debug/app-debug.apk
git -C apps/android-node/inference/third_party/llama.cpp rev-parse HEAD
```

Expected: all checks succeed, the worktree remains clean except deliberate verification artifacts, the APK hash is captured, and the submodule prints `3737e41370da1830a44c663f9929a0f27591ffa6`.

- [ ] **Step 5: Stop for the precise physical-device authorization boundary**

Ask the user to connect the CDL-AN50 with a data-capable USB cable, unlock it, approve this computer’s ADB fingerprint, approve APK installation, then use the phone UI to enable the AI module and start the pinned model download. Do not unlock the bootloader, Root the phone, buy a VPS, enable LAN, or install the APK/model without these visible device-side confirmations.

- [ ] **Step 6: Install and collect current device facts**

```bash
adb devices -l
adb install -r apps/android-node/app/build/outputs/apk/debug/app-debug.apk
adb install -r apps/android-node/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am start -n dev.opendevice.node/.MainActivity
node scripts/collect-android-node-evidence.mjs --phase installed
```

Expected: exactly one authorized target, successful install, the app opens, and evidence contains current Android/ABI/RAM/storage/battery/thermal fields read from this device. If the phone is not currently available, mark only Tasks 1-9 complete and record Task 10 as blocked by the missing physical-device prerequisite.

- [ ] **Step 7: Verify phone-side model integrity and native generation**

After the user starts and the phone finishes the download, run:

```bash
adb shell run-as dev.opendevice.node ls -l no_backup/models/qwen3-0.6b-q8_0
adb shell am instrument -w -e class dev.opendevice.node.inference.LlamaCppSmokeTest -e modelPath /data/user/0/dev.opendevice.node/no_backup/models/qwen3-0.6b-q8_0/Qwen3-0.6B-Q8_0.gguf dev.opendevice.node.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class dev.opendevice.node.api.KeystoreApiKeyStoreTest dev.opendevice.node.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class dev.opendevice.node.ui.NodeFlowTest dev.opendevice.node.test/androidx.test.runner.AndroidJUnitRunner
node scripts/collect-android-node-evidence.mjs --phase native-generation
```

Expected: file size `639446688`, every instrumentation command reports success without a skip, a real non-empty native token stream, a real Keystore create/verify/revoke cycle, the four-screen flow, and model inference attributed to the phone app process.

- [ ] **Step 8: Verify USB forwarding and the standard SDK**

Create a phone-side client key labelled `USB 验证电脑`, copy it once into the shell environment without saving it to a file, then run:

```bash
adb forward tcp:18080 tcp:8080
read -s "OPENDEVICE_API_KEY?粘贴手机刚显示的一次性密钥："
export OPENDEVICE_API_KEY
echo
OPENDEVICE_BASE_URL=http://127.0.0.1:18080/v1 OPENDEVICE_API_KEY="$OPENDEVICE_API_KEY" node scripts/verify-openai-client.mjs
```

Expected: JSON with `ok: true`, the exact model ID, and non-zero character counts for non-streaming and streaming responses. Re-run with a wrong key and expect HTTP 401; start a phone local chat, send a simultaneous SDK request and expect HTTP 429 `node_busy`; stop the node and expect connection failure rather than a cloud or computer-local answer.

- [ ] **Step 9: Exercise lifecycle, cancellation, network, and safety states**

Using the phone UI and the SDK verifier, record each of these as a separate timestamped phase: cancel a stream, turn the screen off for five minutes, background the app for five minutes, unplug/reconnect USB, disable/enable Wi-Fi without LAN mode, force-stop/reopen the app, and run the wrong-SHA download instrumentation case. Then run `ResourcePolicyInstrumentedTest`, which injects fake low-memory, severe-thermal, and moderate/40 C recovery facts into a controller with a fake engine. Expected results are respectively: generation cancelled, no unsupported “permanent background” claim, visible recovery state, clean client disconnect, local node remains usable, no silent service restart after explicit stop, integrity failure without final-file publication, memory block, and heat pause followed by hysteresis-controlled resume.

```bash
adb shell am instrument -w -e class dev.opendevice.node.ai.ResourcePolicyInstrumentedTest dev.opendevice.node.test/androidx.test.runner.AndroidJUnitRunner
```

- [ ] **Step 10: Run a 30-minute thermal soak**

Send one 256-token request at a time for 30 minutes with a 10-second pause between completions. Run evidence sampling concurrently:

```bash
node scripts/collect-android-node-evidence.mjs --phase soak --duration-seconds 1800 --interval-seconds 30 &
EVIDENCE_PID=$!
OPENDEVICE_BASE_URL=http://127.0.0.1:18080/v1 OPENDEVICE_API_KEY="$OPENDEVICE_API_KEY" node scripts/soak-openai-client.mjs
wait "$EVIDENCE_PID"
```

The collector records load time, first-token latency, output tokens/s, process peak RSS, battery percent/temperature, and Android thermal status. Stop immediately on severe thermal state, 45 C battery temperature, app crash, or model corruption. A stopped-by-policy soak is a successful safety behavior but not a performance-pass; the report must state both facts.

- [ ] **Step 11: Produce the final evidence report and secret scan**

Write `docs/verification/android-node-cdl-an50.md` with these exact sections populated only from the generated artifacts: Build provenance, Device facts, Model integrity, Local chat, USB/OpenAI SDK, Authentication and busy errors, Lifecycle and cancellation, Thermal soak, Secret-log scan, Known OEM background behavior, and Deferred scope. The Deferred scope section lists public VPS gateway, GitHub marketplace/search/install, Creator/SDK, JavaScript/WASM sandbox, display/video/toy/router modules, Root Broker, production signing, and store publication as not implemented.

```bash
node scripts/verify-android-node-logs.mjs docs/verification/artifacts
git diff --check
git status --short
```

Expected: secret scan succeeds; the report distinguishes source/build, Android runtime, real CDL-AN50, computer client, and deferred public deployment. If a physical or thermal gate failed, retain the evidence and label that gate failed or blocked instead of marking the release complete.

- [ ] **Step 12: Commit only redacted verification code and evidence**

```bash
git add package.json pnpm-lock.yaml scripts/verify-openai-client.mjs scripts/soak-openai-client.mjs scripts/collect-android-node-evidence.mjs scripts/verify-android-node-logs.mjs scripts/android-node-verification.test.mjs docs/verification/android-node-cdl-an50.md docs/verification/artifacts
git diff --cached --check
git commit -m "test(android): verify AI node on CDL-AN50"
```

## Completion Gate

Before declaring this implementation complete, confirm all of the following:

- [ ] The canonical schema generation check, TypeScript workspace check, Android unit tests, lint, and APK build pass from a clean state.
- [ ] The APK contains only `arm64-v8a`, reports minSdk 28/targetSdk 36, and embeds the pinned `llama.cpp` commit and license.
- [ ] The phone downloaded the exact pinned model itself and published it only after size and SHA-256 verification.
- [ ] Local chat and the standard OpenAI JavaScript SDK both produced real output from the phone process.
- [ ] Wrong/revoked keys, model-not-ready, concurrent request, cancellation, port conflict, and offline states returned the specified failures.
- [ ] Foreground service, safe mode, explicit stop, memory guard, thermal pause/resume, and OEM background behavior have real-device evidence.
- [ ] Logs and committed artifacts contain no prompts, responses, raw API keys, or full ADB serial.
- [ ] Public gateway, marketplace, creator, sandbox, other device modules, and Root remain visibly deferred and are not described as working.
