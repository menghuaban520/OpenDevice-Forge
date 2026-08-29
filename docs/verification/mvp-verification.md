# OpenDevice Forge MVP verification

## Verdict

The local MVP is runnable on this Apple-silicon Mac. The module kernel, read-only device-inspection domain, browser demo, fixed ADB bridge, desktop interface, responsive product site, and macOS packaging route are implemented. The generated macOS app launched successfully and exited normally.

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
| Module manifest, lifecycle, rollback and safe mode | Core unit tests | Passed |
| Device sources, readiness, JSON/Markdown reports and redaction | Core unit tests | Passed |
| Fixed read-only ADB operations, parsing, timeout/error behavior | Rust tests and Clippy | Passed |
| Overview, module center, lifecycle controls and device-inspection workbench | Desktop unit and real-browser tests | Passed |
| Website platform detection, release honesty and responsive layout | Site unit and real-browser tests | Passed |
| macOS native application | Tauri release build and process launch | Passed on this Mac |
| DMG container | `hdiutil verify` | Passed |
| Apple distribution signature/notarization | No Developer ID credentials were provided | Not available |
| HUAWEI CDL-AN50 | Current installed hashes, four device tests, bounded thread comparison, actual local chat and visible stopped lifecycle verified on 2026-08-28 | Bounded current-candidate checks passed; background/soak and current authenticated API behavior remain separate gates |
| Windows runtime | Workflow is configured; no Windows hardware/run was available locally | Not available |
| Android local model and local/USB API | 138 Android JVM tests, lint, arm64 app APK, instrumentation APK, and prior version-bound phone evidence | Current source/build passed; latest device regression remains pending phone unlock |
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
- GitHub/community module content is declarative-only in the MVP; native package execution is rejected.
- A public, empty GitHub repository now exists by explicit authorization. No source commit, tag, release, deployment, account token, or signing credential was published from this candidate.

## Remaining real-world gates

1. Unlock the currently authorized phone, install the latest UI APK, read back its hash, and rerun the Compose/native regression before carrying prior phone evidence forward.
2. Run the configured workflow on Windows and inspect the actual installer and native window before calling Windows supported.
3. Add platform signing/notarization only after the repository/owner and distribution policy are chosen.
4. Extend the bounded CDL-AN50 verification to screen-off/OEM background recovery, a guarded thermal soak and authenticated API behavior on the new APK; do not carry an earlier APK's result forward.
5. Implement and deploy the public gateway only after a VPS and its externally visible side effects are separately authorized.

## Android node addendum — updated 2026-08-28

The Android companion implements the built-in AI module lifecycle, verified resumable model storage, pinned `llama.cpp` JNI integration, one-generation-at-a-time controller, bounded authenticated OpenAI-compatible HTTP service, phone settings, local chat, client-key management, and the module-first control surface. Live temperature/CPU readings are hidden by default; bounded thread/output/temperature/deadline controls remain connected to actual inference and protection behavior.

This addendum does not replace the version-bound phone report. The latest candidate's installed hashes, native/UI instrumentation, real local chat, compact light-theme screenshots and stopped lifecycle are recorded together there. Earlier authenticated USB/API evidence applies only to its own APK; no long-duration or OEM background-survival guarantee is made.

### UI verification

| Gate | Status | Evidence | Action |
| --- | --- | --- | --- |
| Task and structure | PASS on compact phone | Node follows enable → model → service → local chat; five stable bottom destinations; actual start/chat/stop checked | Recheck at enlarged font scales and on tablets |
| Color and type | PASS for current compact light theme | Material semantic colors/type; actual performance-panel and settings screenshots inspected | Dark-theme visual revalidation remains open |
| Icons and shape | PASS on compact phone | One Material outline icon family; 16 dp card radius; five navigation items visually inspected | Revalidate when navigation changes |
| Components and states | PASS at source/build layer | 138 JVM tests pass; the redesigned Compose flows compile, while the latest APK still needs an unlocked-phone run | Do not carry the predecessor APK's phone evidence forward |
| Motion and feedback | N/A | No decorative or continuous motion was added; state changes use native Compose feedback | Reassess if animated transitions are introduced |
| Build and device | PARTIAL for current candidate | 138 JVM tests, lint and both APK builds pass; install/hash/device tests are pending phone unlock | Background recovery, sustained load and current-version authenticated API testing remain unverified |
| Learning continuity | PASS | `DESIGN.md`, prior fidelity evidence, and the Android UI quality gates were applied | Revalidate after an online module directory, public gateway, Root Broker, or tablet layouts exist |
