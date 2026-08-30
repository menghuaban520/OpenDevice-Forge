# Android module-first host candidate

Status: **latest source/tests/build, installed Android APK provenance, current-phone UI flows, native inference and compact dark-theme screenshots verified. Online acquisition, multi-model real-device coverage, signed public artifacts and external publication remain pending**.

Evidence updated on 2026-08-29 (Asia/Shanghai). Source is the local `feat/mvp-plugin-kernel` branch, based on `86dc85b`; this is not a published release.

## Scope implemented

- OpenDevice Forge is a desktop/Android module platform. AI inference and remote API access are one optional module, not the mandatory host flow.
- Android starts on Modules. Host navigation is Modules / Device / Status; AI's Node / Chat / Connections stay inside AI. Model setup appears only when entering installed AI.
- Both APK-owned modules use the registry and generic install, enable, disable and uninstall actions: device information and local AI. Device information uses existing actual read-only facts, with no model or network permission in its manifest.
- Uninstall requires a disabled, unprotected module. Its tombstone survives registry reload, reinstall defaults to disabled, and local model/settings data are retained. APK-bundled code is not physically removed; the UI explicitly explains this. This is not online package downloading.
- AI is not protected. Safe-mode migration disables ordinary modules, and kernel management/recovery does not depend on loading the native AI library.
- Native library loading happens only at explicit model load. Existing model validation happens on AI entry/start, not on host cold start.
- Download cancellation no longer poisons later verification of a retained model. Closing AI sends service cancellation before awaiting controller cleanup. Closing/uninstalling also cancels any pending startup precheck and rechecks state before a service request.
- Package v1 signs the complete canonical manifest plus its payload and publisher-key hashes with domain-separated P-256 ECDSA/SHA-256. Android and Node verify the same fixed fixture; untrusted keys, non-HTTPS/unpinned sources, protected/downloaded builtins, incompatible runtimes, oversized input and tampering fail closed.
- Android stores only verified declarative packages in bounded private version directories. New installs and permission/runtime/publisher/risk changes—including rollback that restores a permission—require fresh review. Version directories and current/previous pointers publish with same-filesystem atomic moves. This store is not yet connected to network acquisition or the live module registry.

## Verification layers

| Layer | Evidence | Result |
| --- | --- | --- |
| Android JVM | `:app:testDebugUnitTest`: 148 tests, 0 failures/errors | verified |
| Android lint | `:app:lintDebug`: 0 errors, 9 retained advisory warnings | verified |
| APK build | `:app:assembleDebug :app:assembleDebugAndroidTest`, exit 0 | verified |
| Shared contract / desktop / site | `pnpm check`: 91 Vitest tests plus 3 verification-script tests, lint/typecheck/build | verified |
| Native desktop package verifier/store | Rust/Tauri: 18 tests plus `cargo clippy --locked --all-targets -- -D warnings`; fixed Node-signed fixture, tampering, trust, size/canonical, host compatibility, private version store, review gate and append-only rollback pointers | verified on current macOS host |
| macOS app bundle | `tauri build --bundles app` produced a 0.1.0 bundle; strict local codesign verification passed. Gatekeeper assessment rejected the ad-hoc identity as expected because Developer ID signing/notarization is absent | verified local preview only |
| Independent read-only review | Correctness/readability/architecture/security/performance review found and corrected incomplete Android schema checks, non-interoperable canonical JSON, missing review gates and permission-restoring rollback | verified at code level |
| Cross-host package crypto on API 29 | `ModulePackageCryptoSmokeTest`: 2/2 in 0.091 s, including a fixed Node-signed package fixture and a device-generated P-256 package | verified |
| Android atomic package store | `ModulePackageStoreSmokeTest`: install, update and pointer rollback 1/1 in 0.152 s on the current phone filesystem | verified |
| Current-device Compose flows | `NodeFlowTest`: 3/3 in 11.036 s on the installed redesigned APK. The first run exposed two stale title expectations after the UI redesign; the isolated failing flow was reproduced, synchronized to the current visible contract, passed 1/1, then passed in the full 3-test run | verified |
| Current native inference regression | `LlamaCppSmokeTest`: 2/2 in 8.424 s using the retained pinned model. Native-load cancellation released the handle and allowed reload/generation; the bounded generation loaded in 621 ms, reached first output at 945 ms, finished at 997 ms and unloaded at 1,065 ms | verified |
| Installed APK provenance | Main APK `6d3798…` and instrumentation APK `6ca0d9…` were read back from `/data/app` and exactly matched the final local artifacts | verified |
| Real app module persistence | Device info opened with actual facts and no model prompt; disable, cancel-uninstall, uninstall, cold-start tombstone, reinstall-disabled, enable and second cold-start persistence all passed | verified |
| Real AI service lifecycle | Retained model verified; explicit start created a foreground `AiNodeService`, UI reached ready, explicit stop removed the service | verified |
| Current-device safety observation | Device module reported normal thermal state; native/UI runs reached at most 28.0 °C battery temperature, with no Root or protection changes | verified for these short runs |
| Online acquisition / registry activation | Cryptographic contract plus Android and desktop private stores exist, but no network/catalog UI or registry activation claims are made | pending |
| GitHub release / OpenAI application | Not published or submitted by this slice | pending |

Observed failing-to-passing regressions include AI safe-mode handling, uninstalled records surviving reload, initial module route, retained-model verification after cancellation, cancelled queued work, creation/unload of an unused engine on a JVM without the JNI library, service-cancel ordering, and cancellation of an old startup request across disable → uninstall → reinstall/enable.

## Artifact provenance

- Candidate app: `apps/android-node/app/build/outputs/apk/debug/app-debug.apk`
  - SHA-256: `6d3798b600fc2ebc1bcd4da3962fe0ab0691a4f374358ef6735bc1ad7764b97b`
- Candidate instrumentation: `apps/android-node/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
  - SHA-256: `6ca0d96fbb6f08a10186481330ae3676b0be8eec1755af5b5ccf5aed340763c7`
- Candidate macOS app executable: `apps/desktop/src-tauri/target/release/bundle/macos/OpenDevice Forge.app/Contents/MacOS/opendevice-forge`
  - SHA-256: `8808f1d8125a90c5c5790b177c68592f6aec69e8028d3d5653e3f36910f851ca`
  - Bundle id/version: `dev.opendevice.forge` / `0.1.0`; ad-hoc signed, not notarized
- These are local Debug builds, not production-signed release artifacts.
- Installed main APK SHA-256: `6d3798b600fc2ebc1bcd4da3962fe0ab0691a4f374358ef6735bc1ad7764b97b`; exact local match after Huawei's visible installer and human verification flow.
- Installed instrumentation APK SHA-256: `6ca0d96fbb6f08a10186481330ae3676b0be8eec1755af5b5ccf5aed340763c7`; exact local match.
- The existing Qwen3 0.6B Q8_0 file remains 639,446,688 bytes and freshly matches SHA-256 `9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031`; no model download occurred.
- Native direct-load smoke evidence on the final pair: load 621 ms, first output 945 ms after test start, generation complete 997 ms, unload 1,065 ms, 2 output tokens, 2 threads. This is a short regression measurement, not a throughput or long-duration guarantee.
- Current real-app state after verification: device-info installed/enabled, AI installed/enabled, safe mode off, AI service stopped, retained model/settings preserved.
- Final cold launch opened the module-first host in 2,530 ms. The short smoke sequence began and ended at 29.0 °C battery temperature.

## Current-device visual evidence

- `screenshots/android-adaptive-modules.png`: cold-launch capability console with property-driven `性能级` profile and temperature/CPU readings absent from first view.
- `screenshots/android-adaptive-modules-lower.png`: both APK-owned modules with one primary action and progressively disclosed technical details.
- `screenshots/android-adaptive-device.png`: actual HUAWEI CDL-AN50 facts, host compatibility and workload tier kept distinct.
- `screenshots/android-adaptive-status.png`: real resource facts and unpublished model/service boundaries without invented telemetry.
- `screenshots/android-adaptive-ai-node.png`: retained verified model, stopped service state and one primary start action.
- `screenshots/android-adaptive-ai-settings.png` and `android-adaptive-ai-custom-limits.png`: optional readings remain hidden while presets and bounded custom limits are available.

All screenshots are from the installed `6d3798…` APK at the phone's actual 1080 × 2400 window. Visual inspection found no actionable overflow, clipped control or bottom-navigation overlap. The current palette's checked foreground/background pairs range from 7.31:1 to 17.50:1. Tablet hardware, large font scales and other manufacturers still require their own visual evidence.

## UI verification

| Gate | Status | Evidence | Action |
| --- | --- | --- | --- |
| Task and structure | PASS | capability hero, module list, device facts, status and AI-node screenshots | retain one primary action and progressive details |
| Color and type | PASS | compact dark-theme screenshots; checked contrast pairs 7.31:1–17.50:1 | recheck if palette tokens change |
| Icons and shape | PASS | Material icon family, 10/16/20 dp shape scale, state pills only | no action |
| Components and states | PASS | 3/3 Compose flows plus stopped/ready, uninstall, dialog and hidden-reading states | add screenshots when new modules ship |
| Motion and feedback | PASS | state-only Material feedback; no looping or decorative motion found | recheck if transitions are introduced |
| Build and native device | PASS | 148 JVM tests, lint/build, installed hashes, 3/3 UI and 2/2 native smoke | wider-window visual evidence remains separate |
| Learning continuity | PASS | `DESIGN.md` adaptive-console rules and the current evidence above | older light-theme screenshots remain version-bound |

## Remaining release gates

1. Add bounded HTTPS/catalog acquisition and a review UI that feeds only verified packages into the Android store, then re-verify stored bytes before disabled registry activation. APK-owned reinstall is not an online download.
2. Add a deliberate publisher-key provisioning/revocation workflow. The native desktop verifier/store already fails closed without an official fingerprint supplied at build time and consumes the same package v1 fixture.
3. Run the version-matched GitHub draft workflow and inspect its macOS, Windows and Android arm64 checksums. Production signing/notarization remains a separate credential-dependent gate.
4. Treat GitHub publication and the OpenAI support application as external account actions requiring fresh confirmation at their final submit controls.

No Root, clock controls, battery/thermal exemption, LAN enablement, production signing-key creation, model redownload, public publication or membership application occurred in this slice.
