# CDL-AN50 Android AI node verification

Status: **verified for the bounded USB/local-AI delivery** on 2026-08-26.

This report distinguishes current-source checks, installed APK behavior, native model generation, OpenAI-compatible API behavior, and deferred scope. It does not claim LAN, public-Internet, Root, marketplace, or long-duration thermal-soak support.

## Build provenance

- Branch: `feat/mvp-plugin-kernel`
- Android application: `dev.opendevice.node` version `0.1.0`
- Android contract: minSdk 28, targetSdk 36, arm64-v8a only
- Pinned llama.cpp commit: `3737e41370da1830a44c663f9929a0f27591ffa6`
- Current app APK SHA-256: `91b533acd3f5b25b362172d7829886cc269ffa8a4bd14afc2f837a8a3b8c8d68`
- Current instrumentation APK SHA-256: `c9661acbb3195885cf42e6f6755be21a46ecc95a7e5b49d1ea1a778738e09f6c`
- Current Android JVM suite: 91 tests, 0 failures, 0 errors
- Android lint: 0 errors and 9 retained advisory dependency/packaging warnings
- The complete workspace `pnpm check`, current Android JVM tests, Android lint, app APK assembly, and instrumentation APK assembly all passed after the final disconnect fix.

## Device facts

- Target gate: `CDL-AN50`, satisfied in every captured artifact
- Android 10 / API 29
- ABI list: `arm64-v8a, armeabi-v7a, armeabi`
- Observed memory on the final running snapshot: 7,749,536 kB total and 4,664,460 kB available
- Observed app high-water RSS after loading the model: approximately 1.06 GiB (`VmHWM` 1,110,560 kB)
- Battery temperature across captured phases: 29–34°C; Android thermal state remained `NONE`; no safety stop occurred
- Raw ADB serials are not stored. Evidence contains only a SHA-256 serial identifier.

## Model integrity and restart recovery

- Model: `Qwen/Qwen3-0.6B-GGUF`
- Pinned revision: `23749fefcc72300e3a2ad315e1317431b06b590a`
- File: `Qwen3-0.6B-Q8_0.gguf`
- Size: 639,446,688 bytes
- SHA-256: `9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031`
- License: Apache-2.0

The phone could not resolve the Hugging Face host during this session, so the pinned file was downloaded on the authorized development computer, verified there, transferred over ADB, and verified again on-device. The final private model remains under the application's no-backup model directory. A force-stop and cold restart re-hashed the model and restored `Ready` without downloading it again.

The download UI now reports an immediate queued state, offers cancellation while waiting, and restores a verified model after process restart. These behaviors are covered by current JVM tests.

## Native inference and local chat boundary

The installed instrumentation package ran `LlamaCppSmokeTest` against the private on-device model without a skip. It loaded the GGUF through the packaged JNI library, generated two tokens, unloaded, and finished with `OK (1 test)` in 185.347 seconds.

This proves native model loading and generation in the phone process. The full Compose phone-flow test reached the local-chat input on this device, but its final send assertion was not rerun after the source-level keyboard-scroll repair because no further test APK was installed. Therefore the final automated end-to-end local-chat UI flow remains unverified, even though the native engine and shared controller were exercised separately.

## USB and OpenAI-compatible API

Using an ADB port forward and the standard OpenAI JavaScript SDK, the installed phone served:

- `GET /v1/models` with model `qwen3-0.6b-q8_0`;
- one non-stream completion with 66 response characters;
- one SSE stream with 56 response characters and a terminating completion.

That SDK run passed as `ok: true`. The final APK was then verified separately with `GET /health` returning HTTP 200 and `{"status":"ok","model_ready":true}`.

## Authentication, concurrency, and disconnect recovery

Real-device results:

- wrong key: HTTP 401, `invalid_api_key`;
- simultaneous second generation: HTTP 429, `node_busy`;
- revoked key on the final APK: HTTP 401, `invalid_api_key`;
- raw keys were shown only on the phone, used ephemerally, then revoked.

The concurrency test exposed a real `Broken pipe` defect: a client-side stream disconnect was initially treated as an inference-engine failure. The controller now distinguishes upstream inference failures from downstream response-write failures. Current tests prove that a write failure keeps the node serving while a genuine inference failure still unloads and fails closed.

On the final APK, a stream was deliberately disconnected after three seconds. Four seconds later both `/health` and authenticated `/v1/models` returned HTTP 200, the service diagnostic state was `serving`, the model remained ready, and no safety stop occurred. This is captured in `2026-08-26T06-05-35.795Z-disconnect-recovery.json`.

## Lifecycle

After final verification, the phone-side client key was revoked and the node was stopped through the visible app control. The foreground service disappeared and the forwarded computer connection failed closed. This is captured in `2026-08-26T06-06-12.954Z-lifecycle-stopped.json`.

Screen-off, reboot, USB reconnect, Wi-Fi changes, and Huawei OEM background recovery have not been exhaustively verified. The application uses a visible, user-started, non-sticky foreground service and makes no permanent-background-survival claim.

## Evidence and secret scan

Captured artifacts:

- `2026-08-25T18-30-11.107Z-installed.json`
- `2026-08-25T19-50-36.220Z-native-generation.json`
- `2026-08-25T20-18-02.135Z-api-auth.json`
- `2026-08-26T06-05-35.795Z-disconnect-recovery.json`
- `2026-08-26T06-06-12.954Z-lifecycle-stopped.json`

The artifact verifier scanned all captured files after the final lifecycle phase and reported success. It checks for raw bearer/API-key material, test prompts and responses, and the unhashed ADB serial while reporting only leak categories.

## Not verified or not implemented

- No 30-minute thermal soak was run, so sustained throughput and long-run thermal behavior are unverified.
- LAN listening was not enabled or tested.
- Public VPS gateway, TLS public-IP endpoint, and phone-initiated tunnel are not implemented.
- GitHub marketplace/search/install, Creator/SDK, and JavaScript/WASM sandbox are not implemented.
- Display, video, toy, and router modules are not implemented.
- Root Broker, bootloader unlock, and phone Root are not implemented or attempted.
- Production signing and store publication are not implemented.

Public deployment, VPS purchase/configuration, credentials, LAN enablement, production publication, and any Root action remain separate authorization boundaries.
