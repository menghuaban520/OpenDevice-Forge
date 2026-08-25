# OpenDevice Forge MVP verification

## Verdict

The local MVP is runnable on this Apple-silicon Mac. The plugin microkernel, device assessment/report domain, browser demo, read-only ADB bridge, desktop interface, responsive product site, and macOS packaging route are implemented. The generated macOS app launched successfully and exited normally.

This is not a public release or a phone-compatibility certification.

## Verification environment

- macOS 26.5 (25F71), arm64
- Node.js 24.18.1
- pnpm 11.5.2
- Rust/Cargo 1.95.0
- System Google Chrome through Playwright

## Evidence matrix

| Area | Local evidence | Status |
| --- | --- | --- |
| Plugin manifest, lifecycle, rollback, safe mode, layouts | Core unit tests | Passed |
| Device sources, readiness, JSON/Markdown reports and redaction | Core unit tests | Passed |
| Fixed read-only ADB operations, parsing, timeout/error behavior | Rust tests and Clippy | Passed |
| Overview, market, lifecycle controls, profiles, layout draft/save | Desktop unit and real-browser tests | Passed |
| Website platform detection, release honesty and responsive layout | Site unit and real-browser tests | Passed |
| macOS native application | Tauri release build and process launch | Passed on this Mac |
| DMG container | `hdiutil verify` | Passed |
| Apple distribution signature/notarization | No Developer ID credentials were provided | Not available |
| HUAWEI nova 7 SE 5G 乐活版 | No real phone was connected during verification | Not available |
| Windows runtime | Workflow is configured; no Windows hardware/run was available locally | Not available |
| Android local model and local/USB API | 87 Android JVM tests, lint, arm64 app APK, and instrumentation APK | Source/build passed; real phone pending |
| Public-IP gateway | Requires a separately authorized VPS, TLS, and phone-initiated tunnel | Deliberately not implemented |

## Packaging result

- Application: `apps/desktop/src-tauri/target/release/bundle/macos/OpenDevice Forge.app`
- Disk image: `apps/desktop/src-tauri/target/release/bundle/dmg/OpenDevice Forge_0.1.0_aarch64.dmg`
- Architecture: arm64
- Disk image size: 5,021,524 bytes
- SHA-256: `204b9c7092e45669b94ef0d7239facd93c57441f3cf185ed4f1183280fbcde6c`
- Launch proof: the packaged application process appeared at its bundle executable path after `open -na`, then accepted a normal quit request.
- Integrity: the DMG passed its embedded CRC verification. A separate SHA-256 checksum is included with the handoff copy.
- Signing boundary: the local binary only has an ad-hoc linker signature and is not Developer ID signed or notarized. Gatekeeper distribution acceptance is therefore not claimed.

## Safety review

- Tauri exposes only `probe_adb`, `list_devices`, and `inspect_device`.
- ADB commands are constructed from fixed enum variants; there is no user-provided shell command path.
- No bootloader unlock, Root acquisition, flash, APK install, identifier modification, lock bypass, personal-file extraction, or retained serial number was added.
- GitHub/community plugin content is declarative-only in the MVP; native package execution is rejected.
- No remote repository, tag, release, deployment, account, token, or signing credential was created.

## Remaining real-world gates

1. Connect one chosen phone, enable developer options and USB debugging, authorize this computer, and compare the live snapshot with the public/demo snapshot.
2. Run the configured workflow on Windows and inspect the actual installer and native window before calling Windows supported.
3. Add platform signing/notarization only after the repository/owner and distribution policy are chosen.
4. Run the Android node APK and its instrumentation suite on the target CDL-AN50; the current evidence proves source, tests, lint, native arm64 packaging, and test-APK compilation, not real-device inference.
5. Implement and deploy the public gateway only after a VPS and its externally visible side effects are separately authorized.

## Android node addendum — 2026-08-26

The Android companion now implements the built-in AI module lifecycle, verified resumable model storage, pinned `llama.cpp` JNI integration, one-generation-at-a-time controller, bounded authenticated OpenAI-compatible HTTP service, phone settings, local chat, client-key management, and the four-screen control surface.

This addendum does not replace real-phone evidence. No Android device or emulator was attached during this build, so the UI instrumentation flow, Android Keystore runtime, notification permission, OEM background behavior, model download, JNI inference, USB client, and thermal soak remain pending.

### UI verification

| Gate | Status | Evidence | Action |
| --- | --- | --- | --- |
| Task and structure | PASS | Node follows enable → model → service → local chat; four stable bottom destinations | Recheck on CDL-AN50 at its real font scale |
| Color and type | PASS at source/build layer | Material semantic colors and type scale; selectable monospace endpoint, command, fingerprint, and token | Capture real light/dark screenshots before visual sign-off |
| Icons and shape | PASS at source/build layer | One Material outline icon family; 16 dp card radius; no text-glyph navigation icons | Inspect optical alignment on the phone |
| Components and states | PASS at test layer | Unit tests cover prerequisites, LAN confirmation, one-time token, settings bounds, local streaming, and actual bind race | Execute `NodeFlowTest` on a connected device |
| Motion and feedback | N/A | No decorative or continuous motion was added; state changes use native Compose feedback | Reassess if animated transitions are introduced |
| Build and device | PARTIAL | 87 JVM tests, lint, app APK, and Android-test APK pass | Blocked on absent Android device/emulator |
| Learning continuity | PASS | `DESIGN.md`, prior fidelity evidence, and the Android UI quality gates were applied | Revalidate after marketplace, public gateway, Root Broker, or tablet layouts exist |
