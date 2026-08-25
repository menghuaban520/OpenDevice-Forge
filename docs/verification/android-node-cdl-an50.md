# CDL-AN50 Android AI node verification

Status: **blocked by missing physical-device prerequisite** as of 2026-08-26.

The source and build gates below are local evidence only. No Android device or emulator was attached, so this report does not claim that the APK was installed, that the model was downloaded, that JNI inference ran, or that a computer reached the phone API.

## Build provenance

- Branch: `feat/mvp-plugin-kernel`
- Android application: `dev.opendevice.node` version `0.1.0`
- Android contract: minSdk 28, targetSdk 36, arm64-v8a only
- Pinned llama.cpp commit: `3737e41370da1830a44c663f9929a0f27591ffa6`
- Source/build checks: `pnpm install --frozen-lockfile`, full workspace lint/typecheck/tests/build, canonical manifest generation check, 87 Android JVM tests, Android lint, app APK, and instrumentation APK all passed from a cleaned Android build directory
- Android lint: 0 errors; 9 non-blocking dependency/packaging advisories retained for the pinned release contract
- App APK SHA-256: `aec1e5f9be501f06f34cfed3be7f0514b4904898e6a6926b30a3346c19a01a76`
- Instrumentation APK SHA-256: `33c6c7a028ab46ec1addb1e4114d46fadd7bde06fc9ae9a692322f56bde16fae`
- APK inspection: minSdk 28, targetSdk 36, and native code only for `arm64-v8a`; `libopendevice_llama.so` is present
- Evidence boundary: source, JVM tests, lint, native packaging, and test-APK compilation can be verified locally; Android runtime behavior cannot.

## Device facts

Blocked. `adb devices -l` returned no attached device on 2026-08-26. No manufacturer, model, Android version, ABI, RAM, storage, battery, or thermal value is inferred from reference specifications.

## Model integrity

Blocked. The target phone has not downloaded the pinned Qwen model, and the private model file size/SHA-256 has not been measured on a device.

## Local chat

Blocked for real inference. The deterministic phone-flow test is compiled into the instrumentation APK, but it has not run on Android hardware and is not a substitute for llama.cpp output from the phone process.

## USB/OpenAI SDK

Blocked. No APK is installed, no phone-side client key exists, no ADB forward is active, and the standard OpenAI JavaScript SDK verifier has not called a phone.

## Authentication and busy errors

Local unit tests cover missing, wrong, and revoked keys, model-not-ready, single-flight busy behavior, request bounds, and streaming completion. Wrong-key `401` and simultaneous phone/SDK `429` remain unverified on the target device.

## Lifecycle and cancellation

Local unit and compiled instrumentation tests cover explicit stop, no sticky restart, client cancellation, heat hysteresis, low-memory blocking, safe mode, and port conflicts. Screen-off, background, USB reconnect, Wi-Fi changes, force-stop/reopen, and OEM recovery behavior remain blocked on the missing device.

## Thermal soak

Blocked. A 30-minute single-request soak has not run, so this report contains no phone performance, battery-temperature, Android thermal-status, or peak-RSS claim.

## Secret-log scan

The verifier scans evidence for the exact test prompts and responses, bearer/API-key material, and the unhashed ADB serial while printing only leak categories. There are no captured device artifacts yet; the final scan must be rerun after collection and before any artifact is committed.

## Known OEM background behavior

Unknown. Huawei/EMUI background behavior has not been observed on the CDL-AN50. The application uses a visible, user-started, non-sticky foreground service and makes no claim of permanent background survival.

## Deferred scope

The following are not implemented in this delivery:

- public VPS gateway, TLS public-IP endpoint, and phone-initiated tunnel;
- GitHub marketplace/search/install;
- Creator/SDK;
- JavaScript/WASM sandbox;
- display, video, toy, and router modules;
- Root Broker, bootloader unlock, and phone Root;
- production signing and store publication.

Public deployment, VPS purchase/configuration, credentials, LAN enablement, APK installation, model download, and any Root action require their own explicit authorization or visible phone-side confirmation.
