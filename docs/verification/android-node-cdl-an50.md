# CDL-AN50 Android AI node verification

Status: **adaptive-console candidate and bounded native regression verified on 2026-08-29** for the installed APK identified below. Earlier performance and USB/API evidence remains version-bound to its recorded APK and is not silently inherited.

This report distinguishes current-source checks, installed APK behavior, native model generation, OpenAI-compatible API behavior, and deferred scope. It does not claim LAN, public-Internet, Root, marketplace, or long-duration thermal-soak support.

## Adaptive console current candidate — 2026-08-29

- Installed main APK SHA-256: `6d3798b600fc2ebc1bcd4da3962fe0ab0691a4f374358ef6735bc1ad7764b97b`; installed instrumentation APK SHA-256: `6ca0d96fbb6f08a10186481330ae3676b0be8eec1755af5b5ccf5aed340763c7`. Both were read back from `/data/app` and match the final local artifacts.
- The retained Qwen3 0.6B Q8_0 model remains 639,446,688 bytes and matches SHA-256 `9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031` inside the application's private no-backup directory. It was not downloaded again.
- `NodeFlowTest` passed 3/3 in 11.036 seconds. The first current-build run reproduced a stale-test failure after the redesigned device page renamed its visible title and disabled-state label; the two outdated expectations were updated, the isolated flow passed 1/1, and the complete flow then passed 3/3.
- The remaining device tests covered the live fail-closed resource policy, Android Keystore key lifecycle, cross-host/device P-256 verification and atomic package-store rollback. The no-argument aggregate run executed these eight non-model tests and explicitly skipped the two model-path-dependent tests; it is not counted as native proof.
- `LlamaCppSmokeTest` was rerun separately with the actual private model path and passed 2/2 in 8.424 seconds. Native-load cancellation observed the native frame, released the model, and allowed reload/generation. The bounded generation loaded in 621 ms, reached first output at 945 ms after test start, completed at 997 ms, and unloaded at 1,065 ms with 2 output tokens and 2 threads.
- Battery temperature was 28.0°C before and after the native run. The AI foreground service was stopped after verification. No LAN listener, new client key, Root action, clock change, battery/thermal bypass or system protection change was made.
- The installed app produced current 1080 × 2400 dark-theme evidence in `screenshots/android-adaptive-*.png`: property-driven HUAWEI CDL-AN50 capability profile, module center, device facts, status, stopped AI node and bounded custom limits. Temperature and CPU readings are absent from the first view and remain optional while protection stays active.
- This proves the redesigned compact-phone path on one Android API-29 arm64 device. Other manufacturers, Android versions, tablets, large font scales, screen-off/background reliability and sustained load remain unverified.

## Performance candidate — 2026-08-28

Status: source/build checks, four device regression tests, a bounded thread comparison, real local chat, compact-phone visual checks and stopped lifecycle passed. Screen-off/background reliability, sustained load and this APK's authenticated API behavior remain unverified. Older APK evidence below is version-bound.

- Root-cause evidence: during a bounded, native-only test the generating thread and several peers waited in `__refrigerator`; the process belonged to a freezer cgroup. Android reported awake, charging and active device-idle state. Bringing the same app into the foreground allowed the same Qwen3 Q8 model to finish generation in 14,540 ms (16,596 ms including load/unload). This identifies OS task freezing in the headless test, not a demonstrated model incompatibility or a CPU clock limit. See [Linux freezer documentation](https://www.kernel.org/doc/html/latest/power/freezing-of-tasks.html).
- The smoke test now owns a visible ActivityScenario and keeps that activity's screen on only for the test, then closes it. It makes no foreground-service, screen-off or OEM exemption claim.
- Native Debug compilation now includes `-O3` throughout the inference subtree, retaining symbols/assertions and the same pinned llama.cpp/ARM baseline. A first optimized two-token control completed loading in 690 ms, first output 318 ms after generation began, generation in 373 ms, and unloading by 1,134 ms. App APK at that control: `af37780c35c19d04ce197c78b4d864088cedec10ea5f68e3ae0d7a6a16495277`; test APK: `c3f3bd1d560afc4ea7e2ba8a5465325e808bf93681727afd1c34343f2cf7f068`. Sampled battery temperature was 30°C and thermal status 0. This short control is not sustained-throughput evidence.
- Implemented: actual model-load threads from phone settings, phone-side output cap for both local/remote requests, 2/3/4-thread presets, custom 2–4 threads, 1–512 output tokens, 38–43°C battery ceiling, 15–120-second load/generation deadline, and an optional in-app performance panel. No system overlay permission is requested.
- Guarding: severe Android thermal status pauses regardless of battery reading; loss of both heat signals pauses; a 3°C cooldown gap prevents immediate restart. The display flag never enters resource-guard decisions. Load-time checks use an independent observer, and native loading cooperatively checks cancellation between tensors. A cancelled dispatcher handoff now closes its acquired native handle.
- These are application workload limits, not manufacturer safety ratings or a guarantee against a damaged battery. The app cannot run its watchdog while the OS freezes the entire process; OS thermal/charging protections remain intact. No Root, overclock, frequency pinning, battery-optimization exemption, charging-protection change, GPU backend or long stress test was performed.
- CPU percentage measures this application's CPU time across its reported available cores. Frequency uses read-only sysfs and remains unavailable when denied; battery temperature is not CPU temperature. Output rate includes first-token wait. Peak app memory is labeled PSS, matching the Android collector.
- JVM regression evidence: 111 tests passed, including observed red-to-green tests for fixed thread application, remote output cap, load-time heat interruption, generation deadline, hidden-panel protection, unknown sensors, bounded presets and CPU readings. Lint has 0 errors and the same 9 advisory warnings. App and instrumentation APK assembly also passed with JDK 17.
- Installed app SHA-256 verified by reading back the phone's base APK: `289eddb163aa21603a59af4eefd2edd5fa583d0c8175874e3d8bb77401a20855`; installed test APK: `1c5a8de927c8db328e2671d23fc1758e8834358e0819080c3a65481a683c1684`. Both match local artifacts.
- The strengthened cancellation regression observed the real `nativeLoad` stack frame before cancellation, then passed cancellation cleanup, reloading, generation cancellation, and a subsequent generation. The native two-test suite passed in 5.619 seconds. It first caught a real cancellation-handoff bug in the initial cleanup code; cleanup now uses outer `NonCancellable` and inner dispatcher switching as required by [Kotlin 1.11.0](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html).
- The final installed app/test pair passed the combined suite, `OK (4 tests)`, in 13.392 seconds (15.553 seconds host elapsed). This includes both native tests and both UI flows: the original five-screen flow and hidden-readings/locked-performance-settings. UI dependencies remain fake; native tests use the actual model. Sampled maximum battery temperature was 31°C, thermal status 0, with no host safety stop.

### Bounded 2-thread / 4-thread comparison

Both runs used the same verified Qwen3 0.6B Q8_0 file, identical short input, greedy sampling, a 64-token ceiling, and a visible test activity on the final APK. Each produced 64 output tokens. The average includes first-token wait, but excludes model loading.

| Threads | Model load | First-token wait | Generation duration | Average output |
| --- | --- | --- | --- | --- |
| 2 | 611 ms | 319 ms | 3,713 ms | 17.24 tokens/s |
| 4 | 606 ms | 235 ms | 3,938 ms | 16.25 tokens/s |

The 4-thread run returned its first token sooner but completed slightly slower in this one short comparison. The default remains 2 threads; the presets are not universal speed rankings. Both runs remained at or below a sampled 31°C / thermal status 0 with no host safety stop. No sustained-speed or maximum-device-performance claim follows from these runs.

### Real application and visual checks

- The actual app, not a fake UI dependency, displayed changing CPU-frequency readings for all six cores, app CPU use, battery temperature and Android thermal status. This confirms the read-only collector works in this installed app's context on this phone; other phones may still deny these fields.
- With performance readings hidden, a short message was sent through the actual Chat screen and native controller. Generation completed and the controller returned to `serving`: model load 724 ms, first-token wait 618 ms, average output 15.3879 tokens/s. Sampled battery temperature stayed 30–31°C, thermal status 0. Prompt and response content were not copied into evidence.
- The first host-only cleanup monitor incorrectly looked for a `ServiceRecord` marker in the service dump and reached its deadline after chat had already completed. That was a verification-script cleanup error, not a new inference hang. A corrected visible Start → Stop check then passed in 11.937 seconds: the screen reported stopped, the start control returned, and the service diagnostics disappeared. Maximum sampled temperature was 31°C / thermal status 0. The performance display preference was restored to visible.
- Compact light-theme screenshots were inspected at the phone's actual 1080 × 2400 resolution: `screenshots/android-performance-node.png` and `screenshots/android-performance-settings.png`. The existing five-item navigation, scrolling, wrapped frequency text, preset controls, numeric fields and protection copy remain usable. Dark theme, enlarged font scales and tablet layouts were not visually revalidated.
- App and test processes were stopped after the visible lifecycle check. Final snapshot: `2026-08-27T17-31-16.918Z-performance-candidate-stopped-20260828.json` (UTC filename; 2026-08-28 in Asia/Shanghai). No new client key, LAN exposure or system power/protection setting was created or changed.

## Pre-performance candidate revalidation (superseded)

- Source base: branch `feat/mvp-plugin-kernel`, commit `f73ecc5858e2642a32fb76d6d0b763b0dd1b8f5b`, plus the current recommendation-policy fix.
- Candidate app APK SHA-256: `109d36dd974d6057223fc6a6dbe1bab3d21567afa7ac3f8f9acead3bec7606fc`.
- Initial instrumentation APK SHA-256: `d3f6c9ce4d0f7788f0e882422c0bd88967af018b515dec04c682cb521bd8e2c9`; subsequent diagnostic/UI test APKs are identified separately below.
- Current Android JVM suite: 100 tests, 0 failures, 0 errors. Lint, app APK assembly, and instrumentation APK assembly pass; lint retains 9 advisory warnings.
- A pre-fix candidate selected `Qwen3.5 0.8B Q4_0`. Its 563,036,064-byte file matched SHA-256 `57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf`, but the native smoke test made no CPU, memory, or process-state progress for more than five minutes and was stopped with battery temperature at 28°C and Android thermal status 0.
- On the same pre-fix installed APK and JNI library, `Qwen3 0.6B Q8_0` completed load, two-token generation, and unload once in 32.582 seconds. That was a successful bounded control, not proof of sustained or repeatable native reliability; the later follow-up below supersedes any broader inference.
- The recommendation policy now selects the release-verified Qwen3 Q8_0 profile on the 8 GiB target and retains the smaller Qwen3 Q4_0 fallback for lower-resource phones. The regression test was observed failing before the policy change and passing afterward.
- On 2026-08-28, the installed app's SHA-256 was read back from the phone and matched the candidate app exactly. The initial installed test APK also matched `d3f6c9ce4d0f7788f0e882422c0bd88967af018b515dec04c682cb521bd8e2c9`.
- The same Qwen3 Q8_0 model was re-hashed on the phone and still matched `9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031`. Its current-candidate smoke test did not complete within a 120-second host watchdog and was deliberately force-stopped. Battery temperature peaked at 28°C and Android thermal status stayed 0; the runner's subsequent `Process crashed` result was caused by this cleanup, not evidence of a spontaneous crash.
- A diagnostic test APK (`34771c2c38ea86f208c417c3b9905ace555cc16008b16bead739c361ce9d76e4`, installed hash verified) added payload-free phase timestamps. It reported model load completion after 1,942 ms and entered generation after 1,943 ms, then reached the 120-second watchdog and was deliberately stopped. Battery temperature peaked at 29°C and thermal status stayed 0. The waiting phase is therefore generation, not model-file loading. Late process samples showed 0% CPU and unchanged accumulated CPU time; this does not establish the native root cause or a throughput benchmark.
- UI instrumentation initially exposed off-screen actions/assertions and an ambiguous warning-text selector shared by a dialog and its background page. After explicit scrolling and a dialog-scoped selector, `NodeFlowTest.completePhoneFlowUsesFirstSetupAndFiveScreenSurface` passed on the target in 6.272 seconds (`OK (1 test)`). The final test APK SHA-256 was verified on-device as `914f83dd89f37e8a8021d9adcfab08e5eee98001a665ccb57cfeeb147b4ae6bb`. These checks use fake model/service dependencies and do not substitute for actual native generation.
- At this earlier stage native generation reliability remained open. The subsequent freezer/foreground control above supersedes the unexplained-hang diagnosis; it does not turn this older APK into a sustained-performance verification.
- After verification, app and test processes were stopped. The stopped-state snapshot is `2026-08-27T16-19-08.625Z-current-candidate-stopped-20260828.json`; its timestamp is UTC (2026-08-28 in Asia/Shanghai). No new LAN listener or client key was created in the real application.

### Baseline performance and safety inspection — before the implementation above

- The target reports Android API 29, board platform `kirin820E`, and six present CPU IDs (`0-5`). Battery temperature and system thermal status were readable.
- In the debug `runas_app` context, selected CPU frequency files were readable, but `/proc/stat` and the probed thermal-zone files were not. This is not proof that the production application domain can read every CPU frequency or temperature; unsupported telemetry must remain unavailable, and battery temperature must not be relabeled as CPU temperature.
- The installed candidate is a Debug build. Its generated native compile commands contain debug flags and no optimization level. CPU inference uses two threads because `DefaultAiNodeController.loadLocked` passes `GenerationContract.MIN_THREADS`, irrespective of the settings UI's 2–4 selection. GPU offload is disabled and the build excludes KleidiAI and dynamic CPU variants. These are verified implementation facts, not measured speedup claims.
- `ResourceGuard` currently uses battery temperature of 45°C or severe system thermal status to pause, with a 40°C resume threshold. These are project policy values, not device-manufacturer safety ratings. Continuous monitoring begins only after successful model load; the loading interval and unavailable thermal readings need explicit fail-safe treatment before offering a performance mode.
- Performance modes, an optional in-app telemetry panel, configurable conservative ceilings, optimized native builds, and quantization/backend comparisons are research proposals only. No performance-mode production behavior, CPU-frequency control, system thermal override, Root action, or long-duration stress run was added.

## Previously verified build provenance

- Branch: `feat/mvp-plugin-kernel`
- Android application: `dev.opendevice.node` version `0.1.0`
- Android contract: minSdk 28, targetSdk 36, arm64-v8a only
- Pinned llama.cpp commit: `3737e41370da1830a44c663f9929a0f27591ffa6`
- Verified app APK SHA-256: `91b533acd3f5b25b362172d7829886cc269ffa8a4bd14afc2f837a8a3b8c8d68`
- Verified instrumentation APK SHA-256: `c9661acbb3195885cf42e6f6755be21a46ecc95a7e5b49d1ea1a778738e09f6c`
- Verified Android JVM suite at that build: 91 tests, 0 failures, 0 errors
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
- `2026-08-27T16-19-08.625Z-current-candidate-stopped-20260828.json`
- `2026-08-27T17-31-16.918Z-performance-candidate-stopped-20260828.json`

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
