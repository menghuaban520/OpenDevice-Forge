# OpenDevice Forge MVP verification

## Verdict

The local MVP is runnable on this Apple-silicon Mac. The module kernel, read-only device-inspection domain, browser demo, fixed ADB bridge, desktop interface, responsive product site, and macOS packaging route are implemented. The generated macOS app launched successfully and exited normally.

This is not a public release or a phone-compatibility certification.

## Evidence ownership

- This report owns the cross-platform MVP, desktop packaging and release-boundary summary.
- [`android-module-host.md`](android-module-host.md) owns the current Android candidate status, installed artifact hashes and current-device regression evidence.
- [`android-node-cdl-an50.md`](android-node-cdl-an50.md) retains version-bound device and performance history. An older APK's result never carries forward to a later build.

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
| HUAWEI CDL-AN50 | Version-bound installed hashes, module persistence, current Compose flows and native inference verified on 2026-08-29 | Current `6d3798…` candidate passed bounded checks; other devices, background/soak and current authenticated API behavior remain separate gates |
| Windows runtime | Workflow is configured; no Windows hardware/run was available locally | Not available |
| Android local model and local/USB API | 148 Android JVM tests, lint, arm64 app and instrumentation APKs, installed-hash readback, 3/3 Compose flows and 2/2 native inference smoke tests | Current source/build and bounded current-device regression passed; online acquisition, wider-window coverage and multi-device runtime evidence remain separate gates |
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
- The public GitHub repository now contains the source history and candidate pull-request branch by explicit authorization. No tag, release, deployment, account token, or signing credential was published.

## Remaining real-world gates

1. Add bounded HTTPS/catalog acquisition, review and disabled registry activation for verified packages; APK-owned reinstall is not an online download.
2. Run the configured workflow on Windows and inspect the actual installer and native window before calling Windows supported.
3. Add platform signing/notarization only after the repository/owner and distribution policy are chosen.
4. Extend the bounded CDL-AN50 verification to screen-off/OEM background recovery, a guarded thermal soak and authenticated API behavior on the new APK; do not carry an earlier APK's result forward.
5. Implement and deploy the public gateway only after a VPS and its externally visible side effects are separately authorized.

## Android candidate summary — updated 2026-08-29

The current Android evidence is maintained in [`android-module-host.md`](android-module-host.md) instead of being duplicated here. Its installed `6d3798…` candidate has version-matched main/test APK hashes, 148 JVM tests, 3/3 current Compose flows, 2/2 native inference smoke tests, module persistence and visible AI-service start/stop evidence on the verified Android API-29 arm64 phone.

The evidence is deliberately bounded: other manufacturers and Android versions, tablets, large font scales, screen-off/OEM background recovery, sustained load, current-version authenticated API behavior, online acquisition and public distribution remain unverified or pending. Any source, package or UI-contract change must update the Android candidate record with fresh build and device evidence before this summary changes.
