# Android module-first host candidate

Status: **latest source/tests/build and desktop/browser UI verified; redesigned Android APK installation, hash readback and phone UI/native regression are pending phone unlock. Prior device evidence below belongs to the predecessor APK and is not carried forward**.

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
| Android JVM | `:app:testDebugUnitTest`: 138 tests, 0 failures/errors/skips | verified |
| Android lint | `:app:lintDebug`: 0 errors, 9 retained advisory warnings | verified |
| APK build | `:app:assembleDebug :app:assembleDebugAndroidTest`, exit 0 | verified |
| Shared contract / desktop / site | `pnpm check`: 91 Vitest tests plus 3 verification-script tests, lint/typecheck/build | verified |
| Native desktop package verifier/store | Rust/Tauri: 18 tests plus `cargo clippy --locked --all-targets -- -D warnings`; fixed Node-signed fixture, tampering, trust, size/canonical, host compatibility, private version store, review gate and append-only rollback pointers | verified on current macOS host |
| macOS app bundle | `tauri build --bundles app` produced a 0.1.0 bundle; strict local codesign verification passed. Gatekeeper assessment rejected the ad-hoc identity as expected because Developer ID signing/notarization is absent | verified local preview only |
| Independent read-only review | Correctness/readability/architecture/security/performance review found and corrected incomplete Android schema checks, non-interoperable canonical JSON, missing review gates and permission-restoring rollback | verified at code level |
| Cross-host package crypto on API 29 | `ModulePackageCryptoSmokeTest`: 2/2 in 0.091 s, including a fixed Node-signed package fixture and a device-generated P-256 package | verified |
| Android atomic package store | `ModulePackageStoreSmokeTest`: install, update and pointer rollback 1/1 in 0.152 s on the current phone filesystem | verified |
| Current-device Compose flows | The predecessor APK passed `NodeFlowTest` 3/3; the redesigned current APK compiled but could not be installed while the phone was locked | pending for current APK |
| Current native inference regression | `LlamaCppSmokeTest`: 2/2 passed in 10.918 s using the retained pinned model; cancellation followed by reload/generation succeeded | verified |
| Installed APK provenance | Predecessor hashes were read back; current APK hashes exist locally but have not been read back from an installation | pending for current APK |
| Real app module persistence | Device info opened with actual facts and no model prompt; disable, cancel-uninstall, uninstall, cold-start tombstone, reinstall-disabled, enable and second cold-start persistence all passed | verified |
| Real AI service lifecycle | Retained model verified; explicit start created a foreground `AiNodeService`, UI reached ready, explicit stop removed the service | verified |
| Current-device safety observation | Device module reported normal thermal state; native/UI runs reached at most 28.0 °C battery temperature, with no Root or protection changes | verified for these short runs |
| Online acquisition / registry activation | Cryptographic contract plus Android and desktop private stores exist, but no network/catalog UI or registry activation claims are made | pending |
| GitHub release / OpenAI application | Not published or submitted by this slice | pending |

Observed failing-to-passing regressions include AI safe-mode handling, uninstalled records surviving reload, initial module route, retained-model verification after cancellation, cancelled queued work, creation/unload of an unused engine on a JVM without the JNI library, service-cancel ordering, and cancellation of an old startup request across disable → uninstall → reinstall/enable.

## Artifact provenance

- Candidate app: `apps/android-node/app/build/outputs/apk/debug/app-debug.apk`
  - SHA-256: `8e9d099cda85937ceec730719636b191125cbfbcad75eff3bb6f252c7a3d1415`
- Candidate instrumentation: `apps/android-node/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
  - SHA-256: `7f2a7ffbdcb3ba5cb18e4761b0083824f30d439b27ab26f003eb54f085bf3a8e`
- Candidate macOS app executable: `apps/desktop/src-tauri/target/release/bundle/macos/OpenDevice Forge.app/Contents/MacOS/opendevice-forge`
  - SHA-256: `8808f1d8125a90c5c5790b177c68592f6aec69e8028d3d5653e3f36910f851ca`
  - Bundle id/version: `dev.opendevice.forge` / `0.1.0`; ad-hoc signed, not notarized
- These are local Debug builds, not production-signed release artifacts.
- Installed main APK SHA-256 at the last completed readback: `8f0e67fc8b2e3804d662001d9b471c13706c6e615e58fe3922d51c0c8307b9ea`. It passed the listed API-29 crypto/store/UI/native checks but predates the redesigned module center and default-hidden performance migration; installing and reading back `8e9d09…` is pending phone unlock.
- Previously installed test APK SHA-256: `eb811602b590cfc61cebeb25f9e160d8c455483398e518e8ade822ca174d4b72`; the current candidate differs and is pending installation.
- The existing Qwen3 0.6B Q8_0 file remains 639,446,688 bytes and freshly matches SHA-256 `9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031`; no model download occurred.
- Native direct-load smoke evidence: load 637 ms, first token 957 ms, generation complete 1,011 ms, unload 1,090 ms, 2 output tokens, 2 threads. This is a short regression measurement, not a throughput or long-duration guarantee.
- Current real-app state after verification: device-info installed/enabled, AI installed/enabled, safe mode off, AI service stopped, retained model/settings preserved.
- Final cold launch opened the module-first host in 2,530 ms. The short smoke sequence began and ended at 29.0 °C battery temperature.

## Prior-version device visual evidence

- `screenshots/android-modules-final.png`: module-first host and restored enabled state.
- `screenshots/android-device-info.png`: actual read-only device, memory, battery and thermal facts without an AI prerequisite.
- `screenshots/android-ai-node-running.png`: retained model verified and the explicit foreground node ready with bounded performance readings.

## Remaining release gates

1. Unlock the authorized phone, install/read back the current `8e9d09…` main APK and `7f2a7f…` test APK, then rerun the Compose and native regression without clearing retained model/settings data.
2. Add bounded HTTPS/catalog acquisition and a review UI that feeds only verified packages into the Android store, then re-verify stored bytes before disabled registry activation. APK-owned reinstall is not an online download.
3. Add a deliberate publisher-key provisioning/revocation workflow. The native desktop verifier/store already fails closed without an official fingerprint supplied at build time and consumes the same package v1 fixture.
4. Run the version-matched GitHub draft workflow and inspect its macOS, Windows and Android arm64 checksums. Production signing/notarization remains a separate credential-dependent gate.
5. Treat GitHub publication and the OpenAI support application as external account actions requiring fresh confirmation at their final submit controls.

No Root, clock controls, battery/thermal exemption, LAN enablement, production signing-key creation, model redownload, public publication or membership application occurred in this slice.
